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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreateGistContentTest extends GithubCreateGistTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    createProjectFiles();
  }

  public void testCreateFromFile() throws Throwable {
    Map<String, String> expected = new HashMap<String, String>();
    expected.put("file.txt", "file.txt content");

    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromDirectory() throws Throwable {
    Map<String, String> expected = new HashMap<String, String>();
    expected.put("folder_file1", "file1 content");
    expected.put("folder_file2", "file2 content");
    expected.put("folder_dir_file3", "file3 content");

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder");
    assertNotNull(file);

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyDirectory() throws Throwable {
    Map<String, String> expected = new HashMap<String, String>();

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder/empty_folder");
    assertNotNull(file);

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyFile() throws Throwable {
    Map<String, String> expected = new HashMap<String, String>();

    VirtualFile file = myProjectRoot.findFileByRelativePath("folder/empty_file");
    assertNotNull(file);

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, null, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromFiles() throws Throwable {
    Map<String, String> expected = new HashMap<String, String>();
    expected.put("file.txt", "file.txt content");
    expected.put("file2", "file2 content");
    expected.put("file3", "file3 content");

    VirtualFile[] files = new VirtualFile[3];
    files[0] = myProjectRoot.findFileByRelativePath("file.txt");
    files[1] = myProjectRoot.findFileByRelativePath("folder/file2");
    files[2] = myProjectRoot.findFileByRelativePath("folder/dir/file3");
    assertNotNull(files[0]);
    assertNotNull(files[1]);
    assertNotNull(files[2]);

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, null, null, files);

    checkEquals(expected, actual);
  }

  public void testCreateFromEmptyFiles() throws Throwable {
    Map<String, String> expected = new HashMap<String, String>();

    VirtualFile[] files = new VirtualFile[0];

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, null, null, files);

    checkEquals(expected, actual);
  }

  public void testCreateFromEditor() throws Throwable {
    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
    assertNotNull(editor);
    ((EditorImpl)editor).setCaretActive();

    Map<String, String> expected = new HashMap<String, String>();
    expected.put("file.txt", "file.txt content");

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, editor, file, null);

    checkEquals(expected, actual);
  }

  public void testCreateFromEditorWithoutFile() throws Throwable {
    VirtualFile file = myProjectRoot.findFileByRelativePath("file.txt");
    assertNotNull(file);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(myProject, file, 0), false);
    assertNotNull(editor);
    ((EditorImpl)editor).setCaretActive();

    Map<String, String> expected = new HashMap<String, String>();
    expected.put("", "file.txt content");

    Map<String, String> actual = GithubCreateGistAction.collectContents(myProject, editor, null, null);

    checkEquals(expected, actual);
  }
}
