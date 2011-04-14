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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lene
 * Date: 29.03.11
 * Time: 17:16
 */
public class FileUtilFindFileTest extends TestCase {
  private final File myTempFile;
  private final File myFirstFile;
  private final File mySecondFile;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public FileUtilFindFileTest() throws IOException {
    myTempFile = FileUtil.createTempDirectory("tEF", ""); //NON-NLS
    myFirstFile = new File(myTempFile, "first");
    mySecondFile = new File(myTempFile, "second"); //NON-NLS
    assertTrue(myFirstFile.createNewFile());
    assertTrue(mySecondFile.createNewFile());
  }

  public void testNonExistingFileInNonExistentDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath("123", "zero");//NON-NLS
    assertTrue(StringUtil.isEmpty(path));
  }

  public void testNonExistingFileInDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "zero");//NON-NLS
    assertTrue(StringUtil.isEmpty(path));
  }

  public void testNonExistingFile() throws Exception {
    String path =
      FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath() + "123", myFirstFile.getName() + "123");
    assertTrue(StringUtil.isEmpty(path));
  }

  public void testExistingFileInDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "first");
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  public void testExistingFile() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath(), "first");
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  public void testTwoFilesOrderInDirectory() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "first", "second"); //NON-NLS
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  public void testTwoFilesOrderInDirectory2() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myTempFile.getAbsolutePath(), "second", "first"); //NON-NLS
    assertEquals(path, mySecondFile.getAbsolutePath());
  }

  public void testTwoFilesOrder() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath(), "first", "second");//NON-NLS
    assertEquals(path, myFirstFile.getAbsolutePath());
  }

  public void testTwoFilesOrder2() throws Exception {
    String path = FileUtil.findFileInProvidedPath(myFirstFile.getAbsolutePath(), "second", "first"); //NON-NLS
    assertEquals(path, myFirstFile.getAbsolutePath());
  }
}
