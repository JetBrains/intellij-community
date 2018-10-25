/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.github.api.requests.GithubGistRequest.FileContent
import java.util.*

/**
 * @author Aleksey Pivovarov
 */
class GithubCreateGistContentTest : GithubCreateGistContentTestBase() {
  private lateinit var editor: Editor

  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { if (wasInit { editor }) EditorFactory.getInstance().releaseEditor(editor) })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
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
