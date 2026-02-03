// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filePrediction

import com.intellij.filePrediction.FilePredictionTestDataHelper.DEFAULT_MAIN_FILE
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.PathUtilRt
import junit.framework.TestCase

internal class FilePredictionTestProjectBuilder(mainPath: String? = null, imports: String? = null) {
  private var mainFile: String? = null
  private var mainFileContent: String? = null

  private val files: MutableMap<String, String> = hashMapOf()
  private val fileActions: MutableList<FileAction> = arrayListOf()

  init {
    if (mainPath != null) {
      addMainFile(mainPath, imports)
    }
  }

  fun addMainFile(path: String, imports: String? = null): FilePredictionTestProjectBuilder {
    val extension = if (imports != null) "java" else "txt"
    return addFile("$path/$DEFAULT_MAIN_FILE.$extension", imports)
  }

  fun addFiles(vararg paths: String): FilePredictionTestProjectBuilder {
    paths.forEach { addFile(it) }
    return this
  }

  fun addFileIfNeeded(path: String): FilePredictionTestProjectBuilder {
    val unified = FileUtil.toSystemIndependentName(path)
    if (!files.containsKey(unified)) {
      addFile(path)
    }
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

  fun open(path: String): FilePredictionTestProjectBuilder {
    return addFileAction(path, FileActionType.OPEN)
  }

  fun openMain(): FilePredictionTestProjectBuilder {
    TestCase.assertTrue("Cannot open main file because its not defined", mainFile != null)
    return open(mainFile!!)
  }

  fun close(path: String): FilePredictionTestProjectBuilder {
    return addFileAction(path, FileActionType.CLOSE)
  }

  fun closeMain(): FilePredictionTestProjectBuilder {
    TestCase.assertTrue("Cannot close main file because its not defined", mainFile != null)
    return close(mainFile!!)
  }

  fun select(path: String): FilePredictionTestProjectBuilder {
    return addFileAction(path, FileActionType.SELECT)
  }

  fun selectMain(): FilePredictionTestProjectBuilder {
    TestCase.assertTrue("Cannot select main file because its not defined", mainFile != null)
    return select(mainFile!!)
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

    if (fileActions.isNotEmpty()) {
      performFileActions(fixture.project, root!!.virtualFile)
    }
    return root!!.virtualFile
  }

  private fun performFileActions(project: Project, root: VirtualFile) {
    val manager = FileEditorManager.getInstance(project)
    for (action in fileActions) {
      val file = root.findFileByRelativePath(action.filePath)
      TestCase.assertNotNull(file)
      when (action.actionType) {
        FileActionType.SELECT -> {
          manager.setSelectedEditor(file!!, TextEditorProvider.getInstance().editorTypeId)
        }
        FileActionType.OPEN -> {
          manager.openFile(file!!, false)
        }
        FileActionType.CLOSE -> {
          manager.closeFile(file!!)
        }
      }
    }
  }

  private fun addFileAction(path: String, actionType: FileActionType): FilePredictionTestProjectBuilder {
    fileActions.add(FileAction(path, actionType))
    return addFileIfNeeded(path)
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
      OSAgnosticPathUtil.getParent(path)?.replace("/", ".")?.let { builder.append("package $it;\n") }
      builder.append("\n")

      imports?.let { builder.append(imports).append("\n") }
      builder.append("class $name {}")
      return builder.toString()
    }
    return name
  }

  private fun isMainFile(path: String) = StringUtil.equals(DEFAULT_MAIN_FILE, getFileName(path))

  private fun getFileName(path: String): String =
    FileUtilRt.getNameWithoutExtension(PathUtilRt.getFileName(path))
}

private data class FileAction(val filePath: String, val actionType: FileActionType)

private enum class FileActionType {
  OPEN, CLOSE, SELECT
}