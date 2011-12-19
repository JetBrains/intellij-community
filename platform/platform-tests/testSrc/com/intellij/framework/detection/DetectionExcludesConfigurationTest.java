/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.framework.detection.impl.exclude.ExcludedFileState;
import com.intellij.framework.detection.impl.exclude.ExcludesConfigurationState;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class DetectionExcludesConfigurationTest extends PlatformTestCase {
  private VirtualFile myTempDir;
  private VirtualFile myTempFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File tempDir = doCreateTempDir();
    final File file = new File(tempDir, "file.xml");
    FileUtil.createIfDoesntExist(file);
    myTempDir = getVirtualFile(tempDir);
    myTempFile = getVirtualFile(file);
  }

  public void testAddExcludedFramework() {
    getConfiguration().addExcludedFramework(getType());
    assertEquals("type", assertOneElement(getState().getFrameworkTypes()));
  }

  public void testAddExcludedFile() {
    getConfiguration().addExcludedFile(myTempDir, null);
    assertEquals(myTempDir.getUrl(), assertOneElement(getState().getFiles()).getUrl());
  }

  public void testAddExcludedFileForExcludedFramework() {
    final FrameworkType type = getType();
    getConfiguration().addExcludedFramework(type);
    getConfiguration().addExcludedFile(myTempDir, type);
    assertEmpty(getState().getFiles());
    getConfiguration().addExcludedFile(myTempDir, null);
    assertOneElement(getState().getFiles());
  }

  public void testAddExcludedFrameworkForExcludedFile() {
    final FrameworkType type = getType();
    getConfiguration().addExcludedFile(myTempDir, type);
    assertOneElement(getState().getFiles());
    getConfiguration().addExcludedFramework(type);
    assertEmpty(getState().getFiles());
    assertOneElement(getState().getFrameworkTypes());
  }

  public void testAddExcludedFileInExcludedDirectory() {
    getConfiguration().addExcludedFile(myTempDir, null);
    getConfiguration().addExcludedFile(myTempFile, null);
    assertEquals(myTempDir.getUrl(), assertOneElement(getState().getFiles()).getUrl());

    getConfiguration().addExcludedFile(myTempFile, getType());
    assertEquals(myTempDir.getUrl(), assertOneElement(getState().getFiles()).getUrl());

    getConfiguration().addExcludedFile(myTempDir, getType());
    final ExcludedFileState state = assertOneElement(getState().getFiles());
    assertEquals(myTempDir.getUrl(), state.getUrl());
    assertNull(state.getFrameworkType());
  }

  public void testAddExcludedFileInExcludedDirectoryForDifferentFramework() {
    getConfiguration().addExcludedFile(myTempDir, getType());
    getConfiguration().addExcludedFile(myTempFile, null);
    assertEquals(2, getState().getFiles().size());
  }

  public void testAddExcludedDirectoryContainingExcludedFile() {
    getConfiguration().addExcludedFile(myTempFile, null);
    assertEquals(myTempFile.getUrl(), assertOneElement(getState().getFiles()).getUrl());
    getConfiguration().addExcludedFile(myTempDir, null);
    assertEquals(myTempDir.getUrl(), assertOneElement(getState().getFiles()).getUrl());
  }

  public void testAddExcludedDirectoryContainingExcludedFileForAllFrameworks() {
    getConfiguration().addExcludedFile(myTempFile, getType());
    assertEquals(myTempFile.getUrl(), assertOneElement(getState().getFiles()).getUrl());
    getConfiguration().addExcludedFile(myTempDir, null);
    assertEquals(myTempDir.getUrl(), assertOneElement(getState().getFiles()).getUrl());
  }

  public void testAddExcludedDirectoryContainingExcludedFileForDifferentFramework() {
    getConfiguration().addExcludedFile(myTempFile, getType());
    assertEquals(myTempFile.getUrl(), assertOneElement(getState().getFiles()).getUrl());
    getConfiguration().addExcludedFile(myTempDir, getType2());
    assertEquals(2, getState().getFiles().size());
  }


  private ExcludesConfigurationState getState() {
    final ExcludesConfigurationState state = getConfiguration().getState();
    assertNotNull(state);
    return state;
  }

  private File doCreateTempDir() {
    try {
      return createTempDirectory();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static FrameworkType getType() {
    return new MockFrameworkType("type");
  }

  private static FrameworkType getType2() {
    return new MockFrameworkType("type2");
  }

  private DetectionExcludesConfigurationImpl getConfiguration() {
    return (DetectionExcludesConfigurationImpl)DetectionExcludesConfiguration.getInstance(myProject);
  }
}
