/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class TestFileSystemItem {
  private final boolean myDirectory;
  private final boolean myArchive;
  private final String myName;
  @Nullable private final String myContent;
  private final Map<String, TestFileSystemItem> myChildren = new HashMap<>();

  TestFileSystemItem(String name, boolean archive, boolean directory, @Nullable String content) {
    myDirectory = directory;
    myArchive = archive;
    myName = name;
    myContent = content;
  }

  TestFileSystemItem(String name, boolean archive, boolean directory) {
    this(name, archive, directory, null);
  }

  void addChild(TestFileSystemItem item) {
    Assert.assertFalse(item.myName + " already added", myChildren.containsKey(item.myName));
    myChildren.put(item.myName, item);
  }

  public void assertDirectoryEqual(final File file) {
    assertDirectoryEqual(file, "/");
  }

  public void assertFileEqual(File file) {
    TestFileSystemItem fileItem = myChildren.values().iterator().next();
    fileItem.assertFileEqual(file, "/");
  }

  private void assertDirectoryEqual(File file, String relativePath) {
    final File[] actualChildren = file.listFiles();
    Set<String> notFound = new HashSet<>(myChildren.keySet());
    if (actualChildren != null) {
      for (File child : actualChildren) {
        final String name = child.getName();
        final TestFileSystemItem item = myChildren.get(name);
        Assert.assertNotNull("unexpected file: " + relativePath + name, item);
        item.assertFileEqual(child, relativePath + name + "/");
        notFound.remove(name);
      }
    }
    Assert.assertTrue("files " + notFound.toString() + " not found in " + relativePath, notFound.isEmpty());
  }

  private void assertFileEqual(File file, String relativePath) {
    try {
      Assert.assertEquals("in " + relativePath, myName, file.getName());
      if (myArchive) {
        final File dirForExtracted = FileUtil.createTempDirectory("extracted_archive", null, false);
        ZipUtil.extract(file, dirForExtracted, null);
        assertDirectoryEqual(dirForExtracted, relativePath);
        FileUtil.delete(dirForExtracted);
      }
      else if (myDirectory) {
        Assert.assertTrue(relativePath + file.getName() + " is not a directory", file.isDirectory());
        assertDirectoryEqual(file, relativePath);
      }
      else if (myContent != null) {
        final String content = FileUtil.loadFile(file);
        Assert.assertEquals("content mismatch for " + relativePath, myContent, content);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static TestFileSystemBuilder fs() {
    return TestFileSystemBuilder.fs();
  }
}
