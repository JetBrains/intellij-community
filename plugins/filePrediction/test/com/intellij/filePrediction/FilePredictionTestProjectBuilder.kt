package com.intellij.filePrediction

import com.intellij.filePrediction.FilePredictionTestDataHelper.DEFAULT_MAIN_FILE
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.PathUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.TestCase

internal class FilePredictionTestProjectBuilder {
  private var mainFile: String? = null
  private var mainFileContent: String? = null

  private val files: MutableMap<String, String> = hashMapOf()

  fun addMainFile(path: String, imports: String? = null): FilePredictionTestProjectBuilder {
    val extension = if (imports != null) "java" else "txt"
    return addFile("$path/$DEFAULT_MAIN_FILE.$extension", imports)
  }

  fun addFiles(vararg paths: String): FilePredictionTestProjectBuilder {
    paths.forEach { addFile(it) }
    return this
  }

  fun addFile(path: String, imports: String? = null): FilePredictionTestProjectBuilder {
    val unified = FileUtil.toSystemIndependentName(path)
    if (isMainFile(path)) {
      mainFile = unified
      mainFileContent = newFileText(unified, imports)
    }
    else {
      files[unified] = newFileText(unified, imports)
    }
    return this
  }

  fun create(fixture: CodeInsightTestFixture): VirtualFile {
    TestCase.assertTrue("Cannot create empty project", files.isNotEmpty() || mainFile != null)
    TestCase.assertNotNull("Cannot create project without main file", mainFile)

    for (file in files.entries) {
      fixture.addFileToProject(file.key, file.value)
    }

    val file = fixture.addFileToProject(mainFile!!, mainFileContent!!)
    val root = findRootDirectory(mainFile!!, file)
    TestCase.assertNotNull("Cannot find project root by main file", mainFile)
    return root!!.virtualFile
  }

  private fun findRootDirectory(path: String, file: PsiFile): PsiDirectory? {
    var root = file.parent
    var numberOfDirs = path.split("/").size - 1
    while (root != null && numberOfDirs > 0) {
      root = root.parent
      numberOfDirs--
    }
    return root
  }

  private fun newFileText(path: String, imports: String? = null): String {
    val name = getFileName(path)
    if (FileUtilRt.extensionEquals(path, "java")) {
      val builder = StringBuilder()
      PathUtil.getParent(path)?.replace("/", ".")?.let { builder.append("package $it;\n") }
      builder.append("\n")

      imports?.let { builder.append(imports).append("\n") }
      builder.append("class $name {}")
      return builder.toString()
    }
    return name!!
  }

  private fun isMainFile(path: String) = StringUtil.equals(DEFAULT_MAIN_FILE, getFileName(path))

  private fun getFileName(path: String) =
    FileUtilRt.getRelativePath(PathUtil.getParent(path)!!, FileUtilRt.getNameWithoutExtension(path), '/')
}