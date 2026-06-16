// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchEP
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDefaultExecutor
import com.intellij.openapi.vcs.changes.patch.PatchNameChecker
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.util.text.CharArrayCharSequence
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_PATCH_NAME: @NonNls String = "shelved"

internal fun getPatchFileInConfigDir(schemePatchDir: Path): Path =
  schemePatchDir.resolve("$DEFAULT_PATCH_NAME.${VcsConfiguration.PATCH}")

internal fun suggestPatchNamePath(project: Project, commitMessage: String?, directory: Path, extension: String?): Path {
  @NonNls var defaultPath: @NonNls String = shortenAndSanitize(commitMessage)
  val patchExtension = extension ?: VcsConfiguration.getInstance(project).patchFileExtension
  while (true) {
    val nonexistentFile = directory.findSequentNonexistentFile(defaultPath, patchExtension)
    if (nonexistentFile.fileName.toString().length >= PatchNameChecker.MAX) {
      defaultPath = defaultPath.substring(0, defaultPath.length - 1)
      continue
    }
    return nonexistentFile
  }
}

@Throws(IOException::class, PatchSyntaxException::class)
internal fun loadPatches(
  project: Project,
  patchPath: Path,
  commitContext: CommitContext?,
  loadContent: Boolean,
): MutableList<TextFilePatch> {
  val text: CharArray
  InputStreamReader(Files.newInputStream(patchPath), StandardCharsets.UTF_8).use { reader ->
    text = FileUtilRt.loadText(reader, Files.size(patchPath).toInt())
  }
  if (text.isEmpty()) return mutableListOf()

  val reader = PatchReader(CharArrayCharSequence(text, 0, text.size), loadContent)
  val textFilePatches = reader.readTextPatches()
  ApplyPatchDefaultExecutor.applyAdditionalInfoBefore(project, reader.getAdditionalInfo(null), commitContext)
  return textFilePatches
}

internal fun writePatchesToFile(project: Project?, path: Path, patches: MutableList<out FilePatch>, commitContext: CommitContext?) {
  Files.newBufferedWriter(path).use { writer ->
    UnifiedDiffWriter.write(project, patches, writer, "\n", commitContext)
  }
}

@Throws(IOException::class)
internal fun savePatchFile(
  project: Project,
  patchFile: Path,
  patches: MutableList<out FilePatch>,
  extensions: MutableList<out PatchEP>?,
  context: CommitContext,
) {
  Files.newBufferedWriter(patchFile).use { writer ->
    UnifiedDiffWriter.write(
      project,
      project.stateStore.projectBasePath,
      patches,
      writer,
      "\n",
      context,
      extensions
    )
  }
}

private fun Path.findSequentNonexistentFile(fileName: String, extension: String): Path {
  var candidate = resolve(if (extension.isEmpty()) fileName else "$fileName.$extension")
  var index = 1
  while (Files.exists(candidate)) {
    val indexedName = if (extension.isEmpty()) "$fileName$index" else "$fileName$index.$extension"
    candidate = resolve(indexedName)
    index++
  }
  return candidate
}

private fun shortenAndSanitize(commitMessage: String?): String {
  @NonNls var defaultPath: @NonNls String = PathUtil.suggestFileName(commitMessage.orEmpty())
  if (defaultPath.isEmpty()) {
    defaultPath = "unnamed"
  }
  if (defaultPath.length > PatchNameChecker.MAX - 10) {
    defaultPath = defaultPath.substring(0, PatchNameChecker.MAX - 10)
  }
  return defaultPath
}
