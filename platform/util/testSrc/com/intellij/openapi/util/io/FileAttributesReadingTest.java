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

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class FileAttributesReadingTest {
  private final byte[] myTestData = new byte[]{'t', 'e', 's', 't'};
  private File myTempDirectory;

  @BeforeClass
  public static void checkMediator() throws Exception {
    final String expectedName = SystemInfo.isWindows ? "IdeaWin32" : "JnaUnix";
    assertEquals(expectedName, FileSystemUtil.getMediatorName());
  }

  @Before
  public void setUp() throws Exception {
    myTempDirectory = FileUtil.createTempDirectory(getClass().getName(), ".tmp");
  }

  @After
  public void tearDown() throws Exception {
    if (myTempDirectory != null) {
      FileUtil.delete(myTempDirectory);
    }
  }

  @Test
  public void missingFile() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt", false);

    final FileAttributes attributes = FileSystemUtil.getAttributes(file);
    assertNull(attributes);
    final String target = FileSystemUtil.resolveSymLink(file);
    assertNull(target);
  }

  @Test
  public void regularFile() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt", true);
    FileUtil.writeToFile(file, myTestData);

    final FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(myTestData.length, attributes.length);
    assertTimestampEquals(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());
  }

  @Test
  public void directory() throws Exception {
    final File file = FileUtil.createTempDirectory(myTempDirectory, "test.", ".tmp");

    final FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampEquals(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());
  }

  @Test
  public void special() throws Exception {
    assumeTrue(SystemInfo.isUnix);
    final File file = new File("/dev/null");

    final FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.SPECIAL, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(0, attributes.length);
    assertTrue(attributes.isWritable());
  }

  @Test
  public void linkToFile() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt", true);
    FileUtil.writeToFile(file, myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    final File link = IoTestUtil.createTempLink(file.getPath(), new File(myTempDirectory, "link").getPath());

    final FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(myTestData.length, attributes.length);
    assertTimestampEquals(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    final String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void doubleLink() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt", true);
    FileUtil.writeToFile(file, myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    final File link1 = IoTestUtil.createTempLink(file.getPath(), new File(myTempDirectory, "link1").getPath());
    final File link2 = IoTestUtil.createTempLink(link1.getPath(), new File(myTempDirectory, "link2").getPath());

    final FileAttributes attributes = getAttributes(link2);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(myTestData.length, attributes.length);
    assertTimestampEquals(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    final String target = FileSystemUtil.resolveSymLink(link2);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void linkToDirectory() throws Exception {
    final File file = FileUtil.createTempDirectory(myTempDirectory, "test.", ".tmp");
    if (SystemInfo.isUnix) assertTrue(file.setWritable(false, false));
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    final File link = IoTestUtil.createTempLink(file.getPath(), new File(myTempDirectory, "link").getPath());

    final FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampEquals(file.lastModified(), attributes.lastModified);
    if (SystemInfo.isUnix) assertFalse(attributes.isWritable());

    final String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void missingLink() throws Exception {
    final File file = FileUtil.createTempFile(myTempDirectory, "test.", ".txt", false);
    final File link = IoTestUtil.createTempLink(file.getPath(), new File(myTempDirectory, "link").getPath());

    final FileAttributes attributes = getAttributes(link);
    assertNull(attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(0, attributes.length);

    final String target = FileSystemUtil.resolveSymLink(link);
    assertNull(target);
  }

  @Test
  public void junction() throws Exception {
    assumeTrue(SystemInfo.isWindows);

    final File target = FileUtil.createTempDirectory(myTempDirectory, "temp.", ".dir");
    final File path = FileUtil.createTempFile(myTempDirectory, "junction.", ".dir", false);
    final File junction = IoTestUtil.createJunction(target.getPath(), path.getAbsolutePath());

    FileAttributes attributes = getAttributes(junction);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(0, attributes.flags);
    assertTrue(attributes.isWritable());

    FileUtil.delete(target);

    attributes = getAttributes(junction);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(0, attributes.flags);
    assertTrue(attributes.isWritable());
  }

  @NotNull
  private static FileAttributes getAttributes(@NotNull final File file) {
    final FileAttributes attributes = FileSystemUtil.getAttributes(file);
    assertNotNull(attributes);
    System.out.println(attributes);
    return attributes;
  }

  private static void assertTimestampEquals(final long expected, final long actual) {
    final long roundedExpected = (expected / 1000) * 1000;
    final long roundedActual = (actual / 1000) * 1000;
    assertEquals("expected: " + expected + ", actual: " + actual,
                 roundedExpected, roundedActual);
  }
}
