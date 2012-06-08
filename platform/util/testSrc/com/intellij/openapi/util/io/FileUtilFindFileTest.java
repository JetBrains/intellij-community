/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.text.StringUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author lene
 * @since 29.03.11
 */
public class FileUtilFindFileTest {
  private static File myTempFile;
  private static File myFirstFile;
  private static File mySecondFile;

  @BeforeClass
  public static void setUp() throws Exception {
    myTempFile = FileUtil.createTempDirectory("tEF", "",false); //NON-NLS
    myFirstFile = new File(myTempFile, "first");
    mySecondFile = new File(myTempFile, "second"); //NON-NLS
    assertTrue(myFirstFile.createNewFile());
    assertTrue(mySecondFile.createNewFile());
  }

  @AfterClass
  public static void tearDown() throws Exception {
    FileUtil.delete(myTempFile);
  }

  @Test
  public void nonExistingFileInNonExistentDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath("123", "zero");//NON-NLS
    assertTrue(StringUtil.isEmpty(path));
  }

  @Test
  public void nonExistingFileInDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "zero");//NON-NLS
    assertTrue(StringUtil.isEmpty(path));
  }

  @Test
  public void nonExistingFile() throws Exception {
    String path =
      FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath() + "123", myFirstFile.getName() + "123");
    assertTrue(StringUtil.isEmpty(path));
  }

  @Test
  public void existingFileInDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "first");
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  @Test
  public void existingFile() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath(), "first");
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrderInDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "first", "second"); //NON-NLS
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrderInDirectory2() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "second", "first"); //NON-NLS
    assertEquals(path, mySecondFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrder() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath(), "first", "second");//NON-NLS
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrder2() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath(), "second", "first"); //NON-NLS
    assertEquals(path, myFirstFile.getAbsolutePath());
  }
}
