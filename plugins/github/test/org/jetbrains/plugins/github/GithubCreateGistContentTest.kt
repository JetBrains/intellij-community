// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent

class GithubCreateGistContentTest : GithubCreateGistContentTestBase() {
  private lateinit var editor: Editor

  override fun tearDown() {
    runAll(
      { invokeAndWaitIfNeeded { if (::editor.isInitialized) EditorFactory.getInstance().releaseEditor(editor) } },
      { super.tearDown() }
    )
  }

  fun testCreateFromFile() {
    val expected = ArrayList<FileContent>()
    expected.add(FileContent("file.txt", "file.txt content"))

    val file = projectRoot.findFileByRelativePath("file.txt")
    assertNotNull(file)

    val actual = GithubCreateGistAction.collectContents(myProject, null, file, null)

    checkEquals(expected, actual)
  }

  fun testCreateFromDirectory() {
    val expected = ArrayList<FileContent>()
    expected.add(FileContent("folder_file1", "file1 content"))
    expected.add(FileContent("folder_file2", "file2 content"))
    expected.add(FileContent("folder_dir_file3", "file3 content"))

    val file = projectRoot.findFileByRelativePath("folder")
    assertNotNull(file)

    val actual = GithubCreateGistAction.collectContents(myProject, null, file, null)

    checkEquals(expected, actual)
  }

  fun testCreateFromEmptyDirectory() {
    val expected = ArrayList<FileContent>()

    val file = projectRoot.findFileByRelativePath("folder/empty_folder")
    assertNotNull(file)

    val actual = GithubCreateGistAction.collectContents(myProject, null, file, null)

    checkEquals(expected, actual)
  }

  fun testCreateFromEmptyFile() {
    val expected = ArrayList<FileContent>()

    val file = projectRoot.findFileByRelativePath("folder/empty_file")
    assertNotNull(file)

    val actual = GithubCreateGistAction.collectContents(myProject, null, file, null)

    checkEquals(expected, actual)
  }

  fun testCreateFromFiles() {
    val expected = ArrayList<FileContent>()
    expected.add(FileContent("file.txt", "file.txt content"))
    expected.add(FileContent("file2", "file2 content"))
    expected.add(FileContent("file3", "file3 content"))

    val files = arrayOfNulls<VirtualFile>(3)
    files[0] = projectRoot.findFileByRelativePath("file.txt")
    files[1] = projectRoot.findFileByRelativePath("folder/file2")
    files[2] = projectRoot.findFileByRelativePath("folder/dir/file3")
    assertNotNull(files[0])
    assertNotNull(files[1])
    assertNotNull(files[2])

    val actual = GithubCreateGistAction.collectContents(myProject, null, null, files)

    checkEquals(expected, actual)
  }

  fun testCreateFromEmptyFiles() {
    val expected = ArrayList<FileContent>()

    val files = VirtualFile.EMPTY_ARRAY

    val actual = GithubCreateGistAction.collectContents(myProject, null, null, files)

    checkEquals(expected, actual)
  }

  fun testCreateFromEditor() {
    val file = projectRoot.findFileByRelativePath("file.txt")
    assertNotNull(file)

    val document = FileDocumentManager.getInstance().getDocument(file!!)
    assertNotNull(document)

    editor = EditorFactory.getInstance().createEditor(document!!, myProject)
    assertNotNull(editor)

    val expected = ArrayList<FileContent>()
    expected.add(FileContent("file.txt", "file.txt content"))

    val actual = GithubCreateGistAction.collectContents(myProject, editor, file, null)

    checkEquals(expected, actual)
  }

  fun testCreateFromEditorWithoutFile() {
    val file = projectRoot.findFileByRelativePath("file.txt")
    assertNotNull(file)

    val document = FileDocumentManager.getInstance().getDocument(file!!)
    assertNotNull(document)

    editor = EditorFactory.getInstance().createEditor(document!!, myProject)
    assertNotNull(editor)

    val expected = ArrayList<FileContent>()
    expected.add(FileContent("", "file.txt content"))

    val actual = GithubCreateGistAction.collectContents(myProject, editor, null, null)

    checkEquals(expected, actual)
  }
}
