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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class FileUtilLightTest {
  private static final char UNIX_SEPARATOR = '/';
  private static final char WINDOWS_SEPARATOR = '\\';

  @Test
  public void toCanonicalPath() {
    assertEquals("", FileUtil.toCanonicalPath(""));
    assertEquals("  ", FileUtil.toCanonicalPath("  "));

    assertEquals("/", FileUtil.toCanonicalPath("/", UNIX_SEPARATOR));
    assertEquals("/", FileUtil.toCanonicalPath("/./", UNIX_SEPARATOR));
    assertEquals("a/b", FileUtil.toCanonicalPath("a/b/", UNIX_SEPARATOR));
    assertEquals("/a/b", FileUtil.toCanonicalPath("/a/////b/", UNIX_SEPARATOR));
    assertEquals("/a/b", FileUtil.toCanonicalPath("/a/././b/", UNIX_SEPARATOR));
    assertEquals("/c", FileUtil.toCanonicalPath("/a/b/../../c", UNIX_SEPARATOR));
    assertEquals("/a", FileUtil.toCanonicalPath("/a/b/..", UNIX_SEPARATOR));
    assertEquals("/a", FileUtil.toCanonicalPath("/a/b/../", UNIX_SEPARATOR));
    assertEquals("/a\\b", FileUtil.toCanonicalPath("/a\\b/", UNIX_SEPARATOR));
    assertEquals("/", FileUtil.toCanonicalPath("/a/../", UNIX_SEPARATOR));
    assertEquals("/", FileUtil.toCanonicalPath("/a/../..", UNIX_SEPARATOR));
    assertEquals("/b", FileUtil.toCanonicalPath("/a/../../b", UNIX_SEPARATOR));
    assertEquals("a...b", FileUtil.toCanonicalPath("a...b", UNIX_SEPARATOR));
    assertEquals("a../b", FileUtil.toCanonicalPath("a../b", UNIX_SEPARATOR));
    assertEquals("a./.b", FileUtil.toCanonicalPath("a./.b", UNIX_SEPARATOR));
    assertEquals("a", FileUtil.toCanonicalPath("a/b/..", UNIX_SEPARATOR));
    assertEquals("a/b", FileUtil.toCanonicalPath("a/b/.", UNIX_SEPARATOR));
    assertEquals("a/b/...", FileUtil.toCanonicalPath("a/b/...", UNIX_SEPARATOR));
    assertEquals("...", FileUtil.toCanonicalPath("...", UNIX_SEPARATOR));
    assertEquals(".local", FileUtil.toCanonicalPath(".local/", UNIX_SEPARATOR));
    assertEquals("file.ext", FileUtil.toCanonicalPath("file.ext", UNIX_SEPARATOR));
    assertEquals("file.", FileUtil.toCanonicalPath("file.", UNIX_SEPARATOR));
    assertEquals("file..", FileUtil.toCanonicalPath("file..", UNIX_SEPARATOR));
    assertEquals("", FileUtil.toCanonicalPath(".", UNIX_SEPARATOR));
    assertEquals("", FileUtil.toCanonicalPath("./", UNIX_SEPARATOR));
    assertEquals("", FileUtil.toCanonicalPath("a/..", UNIX_SEPARATOR));
    assertEquals("b", FileUtil.toCanonicalPath("a/..//b", UNIX_SEPARATOR));
    assertEquals("..", FileUtil.toCanonicalPath("..", UNIX_SEPARATOR));
    assertEquals("..", FileUtil.toCanonicalPath("../", UNIX_SEPARATOR));
    assertEquals("../..", FileUtil.toCanonicalPath("../..", UNIX_SEPARATOR));
    assertEquals("../../..", FileUtil.toCanonicalPath("../../..///./", UNIX_SEPARATOR));
    assertEquals("../a....", FileUtil.toCanonicalPath("../a....//", UNIX_SEPARATOR));
    assertEquals("..", FileUtil.toCanonicalPath("../a..../../", UNIX_SEPARATOR));

    assertEquals("C:/", FileUtil.toCanonicalPath("C:\\", WINDOWS_SEPARATOR));
    assertEquals("a/b", FileUtil.toCanonicalPath("a\\b\\", WINDOWS_SEPARATOR));
    assertEquals("c:/a/b", FileUtil.toCanonicalPath("c:\\a\\\\b\\", WINDOWS_SEPARATOR));
    assertEquals("c:/a/b", FileUtil.toCanonicalPath("c:\\a\\.\\.\\b\\", WINDOWS_SEPARATOR));
    assertEquals("c:/d", FileUtil.toCanonicalPath("c:\\a\\b\\..\\..\\d", WINDOWS_SEPARATOR));
    assertEquals("/a/b", FileUtil.toCanonicalPath("\\a/b\\", WINDOWS_SEPARATOR));
    assertEquals("c:/", FileUtil.toCanonicalPath("c:\\a\\..\\", WINDOWS_SEPARATOR));
    assertEquals("c:/", FileUtil.toCanonicalPath("c:\\a\\..\\..", WINDOWS_SEPARATOR));
    assertEquals("c:/b", FileUtil.toCanonicalPath("c:\\a\\..\\..\\b", WINDOWS_SEPARATOR));

    if (SystemInfo.isWindows) {
      assertEquals("//", FileUtil.toCanonicalPath("\\\\\\", WINDOWS_SEPARATOR));
      assertEquals("//host/", FileUtil.toCanonicalPath("\\\\\\host", WINDOWS_SEPARATOR));
      assertEquals("//host/", FileUtil.toCanonicalPath("\\\\\\host\\\\", WINDOWS_SEPARATOR));
      assertEquals("//host/share/", FileUtil.toCanonicalPath("\\\\host\\\\share", WINDOWS_SEPARATOR));
      assertEquals("//host/share/", FileUtil.toCanonicalPath("\\\\host\\\\share\\\\", WINDOWS_SEPARATOR));
      assertEquals("//host/share/path", FileUtil.toCanonicalPath("\\\\host\\\\share\\\\path\\\\", WINDOWS_SEPARATOR));
      assertEquals("//host/share/path", FileUtil.toCanonicalPath("\\\\host\\\\share\\\\traversal\\..\\..\\path\\", WINDOWS_SEPARATOR));
    }
  }

  @Test
  public void isAncestor() {
    assertTrue(FileUtil.isAncestor("/", "/a/", true));
    assertTrue(FileUtil.isAncestor("/a/b/c", "/a/b/c/d/e/f", true));
    assertTrue(FileUtil.isAncestor("/a/b/c/", "/a/b/c/d/e/f", true));
    assertFalse(FileUtil.isAncestor("/a/b/c/1", "/a/b/c/2", true));
    assertFalse(FileUtil.isAncestor("/a/b/c/1", "/a/b/c/2", false));
    assertTrue(FileUtil.isAncestor("/a/b/c/", "/a/b/c", false));
    assertTrue(FileUtil.isAncestor("/a///b/c", "/a/b/c/", false));
    assertFalse(FileUtil.isAncestor("/a/b/c/", "/a/./b/c", true));
    assertFalse(FileUtil.isAncestor("/a/b/c", "/a/b/c/", true));
    assertFalse(FileUtil.isAncestor("/a/b/c", "/a/b/cde", true));
    assertFalse(FileUtil.isAncestor("/a/b/c", "/a/b/cde", false));

    assertEquals(SystemInfo.isWindows, FileUtil.isAncestor("c:\\", "C:/a/b/c", true));
    assertEquals(!SystemInfo.isFileSystemCaseSensitive, FileUtil.isAncestor("/a/b/c", "/a/B/c/d", true));
  }

  @Test
  public void testRemoveAncestors() {
    List<String> data = Arrays.asList("/a/b/c", "/a", "/a/b", "/d/e", "/b/c", "/a/d", "/b/c/ttt", "/a/ewq.euq");
    String[] expected = {"/a","/b/c","/d/e"};
    @SuppressWarnings("unchecked") Collection<String> result = FileUtil.removeAncestors(data, Convertor.SELF, PairProcessor.TRUE);
    assertArrayEquals(expected, ArrayUtil.toStringArray(result));
  }

  @Test
  public void testCheckImmediateChildren() {
    String root = "/a";
    String[] data = {"/a/b/c", "/a", "/a/b", "/d/e", "/b/c", "/a/d", "/a/b/c/d/e"};
    ThreeState[] expected1 = {ThreeState.UNSURE, ThreeState.YES, ThreeState.YES, ThreeState.NO, ThreeState.NO, ThreeState.YES, ThreeState.UNSURE};
    ThreeState[] expected2 = {ThreeState.UNSURE, ThreeState.NO, ThreeState.YES, ThreeState.NO, ThreeState.NO, ThreeState.YES, ThreeState.UNSURE};

    for (int i = 0; i < data.length; i++) {
      ThreeState state = FileUtil.isAncestorThreeState(root, data[i], false);
      assertEquals(String.valueOf(i), expected1[i], state);
    }

    for (int i = 0; i < data.length; i++) {
      ThreeState state = FileUtil.isAncestorThreeState(root, data[i], true);
      assertEquals(String.valueOf(i), expected2[i], state);
    }
  }

  @Test
  public void testStartsWith() {
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jeka"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/local/"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/"));
    assertTrue(FileUtil.startsWith("c:/idea", "c:/"));
    assertTrue(FileUtil.startsWith("c:/idea", "c:"));
    assertTrue(FileUtil.startsWith("c:/idea", ""));
    assertTrue(FileUtil.startsWith("c:/idea/x", "C:/IDEA", false));

    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jek"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/aaa"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jeka/"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/aaa"));
    assertFalse(FileUtil.startsWith("c:/idea2", "c:/idea"));
    assertFalse(FileUtil.startsWith("c:/idea_branches/i18n", "c:/idea"));
  }

  @Test
  public void testLoadProperties() throws IOException {
    String data = "key2=value2\nkey1=value1\nkey3=value3";
    Map<String, String> map = FileUtil.loadProperties(new StringReader(data));
    assertEquals(ContainerUtil.newArrayList("key2", "key1", "key3"), ContainerUtil.newArrayList(map.keySet()));
  }

  @Test
  public void testRootPath() {
    assertTrue(FileUtil.isRootPath("/"));
    assertTrue(FileUtil.isRootPath("c:/"));
    assertTrue(FileUtil.isRootPath("Z:\\"));

    assertFalse(FileUtil.isRootPath(""));
    assertFalse(FileUtil.isRootPath("/tmp"));
    assertFalse(FileUtil.isRootPath("c:"));
    assertFalse(FileUtil.isRootPath("X:\\Temp"));
  }

  @Test
  public void testNormalize() {
    assertEquals("/a/b/.././c/", FileUtil.normalize("/a//b//..///./c//"));
    if (SystemInfo.isWindows) {
      assertEquals("//a/b/.././c/", FileUtil.normalize("\\\\\\a\\\\//b//..///./c//"));
    }
    else {
      assertEquals("/a/b/.././c/", FileUtil.normalize("\\\\\\a\\\\//b//..///./c//"));
    }
  }

  @Test
  public void testRelativeToUserHome() {
    assertEquals(SystemProperties.getUserHome(), FileUtil.getLocationRelativeToUserHome(SystemProperties.getUserHome(), false));
    String expected = SystemInfo.isWindows ? "~\\relative" : "~/relative";
    assertEquals(expected, FileUtil.getLocationRelativeToUserHome(SystemProperties.getUserHome() + "/relative", false));
  }

  @Test
  public void sanitizeFileName() {
    String newS = "tmp";
    assertThat(FileUtil.sanitizeFileName(newS)).isSameAs(newS);
    assertThat(FileUtil.sanitizeFileName("_test")).isSameAs("_test");

    assertThat(FileUtil.sanitizeFileName(" ")).isEqualTo("_");
    assertThat(FileUtil.sanitizeFileName("\u2026")).isEmpty();
    assertThat(FileUtil.sanitizeFileName("q_test")).isSameAs("q_test");
    assertThat(FileUtil.sanitizeFileName("12_")).isSameAs("12_");
    assertThat(FileUtil.sanitizeFileName("12_  123")).isEqualTo("12___123");
    assertThat(FileUtil.sanitizeFileName(" 12\u2026123")).isEqualTo("_12123");

    assertThat(FileUtil.sanitizeFileName("a+b+c")).isEqualTo("a_b_c");
  }

  @Test
  public void windowsShortName() {
    assertTrue(FileUtil.containsWindowsShortName("C:\\dir~1"));
    assertTrue(FileUtil.containsWindowsShortName("C:\\dir~1\\"));
    assertTrue(FileUtil.containsWindowsShortName("C:\\dir~1\\file.txt"));
    assertTrue(FileUtil.containsWindowsShortName("C:/dir/file~1"));
    assertTrue(FileUtil.containsWindowsShortName("C:/dir/file~1.txt"));
    assertTrue(FileUtil.containsWindowsShortName("C:/dir/file~1.1"));

    assertFalse(FileUtil.containsWindowsShortName("~"));
    assertFalse(FileUtil.containsWindowsShortName("C:\\some~dir"));
    assertFalse(FileUtil.containsWindowsShortName("C:\\some-dir~1"));
    assertFalse(FileUtil.containsWindowsShortName("C:/dir/file~1.extension"));
    assertFalse(FileUtil.containsWindowsShortName("C:/dir/file.~1"));
    assertFalse(FileUtil.containsWindowsShortName("C:/dir/file.ext~1"));
  }
}
