/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.github.api.GithubGist.FileContent;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreateGistContentTest extends GithubCreateGistTestBase {

  @Override
  protected void beforeTest() throws Exception {
    createProjectFiles();
  }

  public void testCreateFromFile() throws Throwable {
    List<FileContent> expected = new ArrayList<FileContent>();
    expected.add(new FileContent("file.txt", "file.txt content"));

    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromDirectory() throws Throwable {
    List<FileContent> expected = new ArrayList<FileContent>();
    expected.add(new FileContent("folder_file1", "file1 content"));
    expected.add(new FileContent("folder_file2", "file2 content"));
    expected.add(new FileContent("folder_dir_file3", "file3 content"));

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyDirectory() throws Throwable {
    List<FileContent> expected = new ArrayList<FileContent>();

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder/empty_folder");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyFile() throws Throwable {
    List<FileContent> expected = new ArrayList<FileContent>();

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder/empty_file");
    assertNotNull(file);

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromFiles() throws Throwable {
    List<FileContent> expected = new ArrayList<FileContent>();
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

  public void testCreateFromEmptyFiles() throws Throwable {
    List<FileContent> expected = new ArrayList<FileContent>();

    VirtualFile[] files = new VirtualFile[0];

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, null, null, files);

    checkEquals(expected, actual);
  }

  public void testCreateFromEditor() throws Throwable {
    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
    assertNotNull(editor);
    ((EditorImpl)editor).setCaretActive();

    List<FileContent> expected = new ArrayList<FileContent>();
    expected.add(new FileContent("file.txt", "file.txt content"));

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, editor, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEditorWithoutFile() throws Throwable {
    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
    assertNotNull(editor);
    ((EditorImpl)editor).setCaretActive();

    List<FileContent> expected = new ArrayList<FileContent>();
    expected.add(new FileContent("", "file.txt content"));

    List<FileContent> actual = GithubCreateGistAction.collectContents(myProject, editor, null, null);

    checkEquals(expected, actual);
  }
}
