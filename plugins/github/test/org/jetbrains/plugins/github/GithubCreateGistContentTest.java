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
package org.jetbrains.plugins.github;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.github.api.requests.GithubGistRequest.FileContent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreateGistContentTest extends GithubCreateGistContentTestBase {
  protected Editor myEditor;

  @Override
  protected void afterTest() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
    }
  }

  public void testCreateFromFile() {
    List<FileContent> expected = new ArrayList<>();
    expected.add(new FileContent("file.txt", "file.txt content"));

    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromDirectory() {
    List<FileContent> expected = new ArrayList<>();
    expected.add(new FileContent("folder_file1", "file1 content"));
    expected.add(new FileContent("folder_file2", "file2 content"));
    expected.add(new FileContent("folder_dir_file3", "file3 content"));

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyDirectory() {
    List<FileContent> expected = new ArrayList<>();

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder/empty_folder");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyFile() {
    List<FileContent> expected = new ArrayList<>();

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder/empty_file");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromFiles() {
    List<FileContent> expected = new ArrayList<>();
    expected.add(new FileContent("file.txt", "file.txt content"));
    expected.add(new FileContent("file2", "file2 content"));
    expected.add(new FileContent("file3", "file3 content"));

    VirtualFile[] files = new VirtualFile[3];
    files[0] = myProjectRoot.findFileByRelativePath("file.txt");
    files[1] = myProjectRoot.findFileByRelativePath("folder/file2");
    files[2] = myProjectRoot.findFileByRelativePath("folder/dir/file3");
    assertNotNull(files[0]);
    assertNotNull(files[1]);
    assertNotNull(files[2]);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, null, files);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyFiles() {
    List<FileContent> expected = new ArrayList<>();

    VirtualFile[] files = VirtualFile.EMPTY_ARRAY;

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, null, files);

    checkEquals(expected, actual);
  }

  public void testCreateFromEditor() {
    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);

    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);

    myEditor = EditorFactory.getInstance().createEditor(document, myProject);
    assertNotNull(myEditor);

    List<FileContent> expected = new ArrayList<>();
    expected.add(new FileContent("file.txt", "file.txt content"));

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, myEditor, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEditorWithoutFile() {
    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);

    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);

    myEditor = EditorFactory.getInstance().createEditor(document, myProject);
    assertNotNull(myEditor);

    List<FileContent> expected = new ArrayList<>();
    expected.add(new FileContent("", "file.txt content"));

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, myEditor, null, null);

    checkEquals(expected, actual);
  }
}
