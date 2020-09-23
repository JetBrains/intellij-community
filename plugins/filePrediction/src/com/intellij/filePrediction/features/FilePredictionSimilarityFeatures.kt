// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.features.FilePredictionFeature.Companion.binary
import com.intellij.filePrediction.features.FilePredictionFeature.Companion.numerical
import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.toLowerCase
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.NameUtilCore

class FilePredictionSimilarityFeatures : FilePredictionFeatureProvider {
  companion object {
    private val FEATURES = arrayListOf(
      "ancestor",
      "common",
      "common_norm",
      "common_words",
      "common_words_norm",
      "distance",
      "distance_norm",
      "excluded",
      "in_library",
      "in_project",
      "in_source",
      "light",
      "name_prefix",
      "name_prefix_norm",
      "path_prefix",
      "path_prefix_norm",
      "relative_common",
      "relative_common_norm",
      "relative_distance",
      "relative_distance_norm",
      "relative_path_prefix",
      "relative_path_prefix_norm",
      "same_dir",
      "same_module"
    )
  }

  override fun getName(): String = "similarity"

  override fun getFeatures(): List<String> = FEATURES

  override fun calculateFileFeatures(project: Project,
                                     newFile: VirtualFile,
                                     prevFile: VirtualFile?,
                                     cache: FilePredictionFeaturesCache): Map<String, FilePredictionFeature> {
    val result = HashMap<String, FilePredictionFeature>()
    ApplicationManager.getApplication().runReadAction {
      if (newFile.isValid) {
        val fileIndex = FileIndexFacade.getInstance(project)
        result["in_project"] = binary(fileIndex.isInProjectScope(newFile))
        result["in_source"] = binary(fileIndex.isInSource(newFile))
        result["in_library"] = binary(fileIndex.isInLibraryClasses(newFile) || fileIndex.isInLibrarySource(newFile))
        result["excluded"] = binary(fileIndex.isExcludedFile(newFile))

        if (prevFile != null && prevFile.isValid) {
          val newModule = fileIndex.getModuleForFile(newFile)
          result["same_module"] = binary(newModule != null && newModule == fileIndex.getModuleForFile(prevFile))
        }
        result["light"] = binary(newFile is LightVirtualFile)
      }
    }

    if (prevFile != null) {
      val newFileName = FileUtil.getNameWithoutExtension(newFile.name)
      val prevFileName = FileUtil.getNameWithoutExtension(prevFile.name)
      addNameSimilarity(newFileName, prevFileName, result)

      addPathSimilarity(newFile, prevFile, null, "", result)

      if (!project.isDisposed) {
        val baseDir = project.guessProjectDir()
        if (baseDir != null && VfsUtil.isAncestor(baseDir, newFile, false) && VfsUtil.isAncestor(baseDir, prevFile, false)) {
          addPathSimilarity(newFile, prevFile, baseDir, "relative_", result)
        }
      }

      val newDir = newFile.parent
      val prevDir = prevFile.parent
      result["same_dir"] = binary(FileUtil.pathsEqual(newDir.path, prevDir.path))

      val isAncestor =
        FileUtil.isAncestor(newDir.path, prevDir.path, false) ||
        FileUtil.isAncestor(prevDir.path, newDir.path, false)
      result["ancestor"] = binary(isAncestor)
    }
    return result
  }

  private fun addPathSimilarity(newFile: VirtualFile,
                                prevFile: VirtualFile,
                                root: VirtualFile?,
                                prefix: String,
                                result: HashMap<String, FilePredictionFeature>) {
    val newPath = getRelativePath(newFile, root)
    val prevPath = getRelativePath(prevFile, root)
    val commonPrefixLen = StringUtil.commonPrefixLength(newPath, prevPath)
    result["${prefix}path_prefix"] = numerical(commonPrefixLen)
    result["${prefix}path_prefix_norm"] = numerical((2 * commonPrefixLen.toDouble()) / (newPath.length + prevPath.length))

    val newDirs = FileUtil.splitPath(newPath, '/')
    val prevDirs = FileUtil.splitPath(prevPath, '/')
    val common = findCommonAncestor(newDirs, prevDirs)

    val maxDistance = newDirs.size + prevDirs.size - 2
    result["${prefix}common"] = numerical(common)
    result["${prefix}common_norm"] = numerical(if (maxDistance != 0) (2 * common.toDouble()) / maxDistance else 0.0)

    val distance = maxDistance - 2 * common
    result["${prefix}distance"] = numerical(distance)
    result["${prefix}distance_norm"] = numerical(if (maxDistance != 0) (distance.toDouble() / maxDistance) else 0.0)
  }

  private fun findCommonAncestor(newDirs: List<String>, prevDirs: List<String>): Int {
    var i = 0
    while (i < newDirs.size - 1 && i < prevDirs.size - 1 && FileUtil.namesEqual(newDirs[i], prevDirs[i])) i++
    return i
  }

  private fun getRelativePath(file: VirtualFile, ancestor: VirtualFile?): String {
    if (ancestor != null) {
      val relative = VfsUtil.getRelativePath(file, ancestor)
      if (relative != null) {
        return unify(relative)
      }
    }
    return unify(file.path)
  }

  private fun addNameSimilarity(newFileName: String, prevFileName: String, result: HashMap<String, FilePredictionFeature>) {
    val commonPrefixLen = StringUtil.commonPrefixLength(newFileName, prevFileName)
    result["name_prefix"] = numerical(commonPrefixLen)
    result["name_prefix_norm"] = numerical((2 * commonPrefixLen.toDouble()) / (newFileName.length + prevFileName.length))

    var common = 0
    val newWords = ContainerUtil.map2Set(NameUtilCore.nameToWords(newFileName), Strings::toLowerCase)
    val prevWords = ContainerUtil.map2Set(NameUtilCore.nameToWords(prevFileName), Strings::toLowerCase)
    for (prevWord in prevWords) {
      if (newWords.contains(prevWord)) common++
    }
    result["common_words"] = numerical(common)
    result["common_words_norm"] = numerical((2 * common.toDouble()) / (newWords.size + prevWords.size))
  }

  private fun unify(path: String): String {
    val caseSensitive = SystemInfo.isFileSystemCaseSensitive
    return if (caseSensitive) FileUtil.getNameWithoutExtension(path) else FileUtil.getNameWithoutExtension(toLowerCase(path))
  }
}
