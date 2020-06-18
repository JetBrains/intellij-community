// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.util.PathUtilRt.Platform;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class PathUtilTest {
  @Test
  public void fileName() {
    assertThat(PathUtilRt.getFileName("foo.html")).isSameAs("foo.html");
    assertThat(PathUtilRt.getFileName("/bar/foo.html")).isEqualTo("foo.html");
    assertThat(PathUtilRt.getFileName("bar/foo.html")).isEqualTo("foo.html");
    assertThat(PathUtilRt.getFileName("bar/foo.html/")).isEqualTo("foo.html");
    assertThat(PathUtilRt.getFileName("bar/foo.html//")).isEqualTo("foo.html");
    assertThat(PathUtilRt.getFileName("bar/foo.html///")).isEqualTo("foo.html");
    assertThat(PathUtilRt.getFileName("/")).isEqualTo("");
    assertThat(PathUtilRt.getFileName("")).isEqualTo("");
    assertThat(PathUtilRt.getFileName("C")).isEqualTo("C");
  }

  @Test
  public void fileExt() {
    assertThat(PathUtilRt.getFileExtension("foo.html")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("foo.html/")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("/foo.html/")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("/bar/foo.html/")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("/bar/foo.html//")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("/bar/foo.html///")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("")).isNull();
    assertThat(PathUtilRt.getFileExtension("foo")).isNull();
    assertThat(PathUtilRt.getFileExtension("foo.or.bar/bar")).isNull();
    assertThat(PathUtilRt.getFileExtension("foo.")).isEmpty();
  }

  @Test
  public void fileNameValidityBasics() {
    assertFalse(PathUtilRt.isValidFileName("", false));
    assertFalse(PathUtilRt.isValidFileName(".", false));
    assertFalse(PathUtilRt.isValidFileName("..", false));
    assertFalse(PathUtilRt.isValidFileName("a/b", false));
    assertFalse(PathUtilRt.isValidFileName("a\\b", false));
  }

  @Test
  public void fileNameValidityPlatform() {
    assertFalse(PathUtilRt.isValidFileName("a:b", true));
    assertTrue(PathUtilRt.isValidFileName("a:b", Platform.UNIX, false, null));
    assertFalse(PathUtilRt.isValidFileName("a:b", Platform.WINDOWS, false, null));
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void fileNameValidityCharset() {
    Charset cp1251 = Charset.forName("Cp1251");
    assertTrue(PathUtilRt.isValidFileName("имя файла", Platform.UNIX, false, cp1251));
    assertFalse(PathUtilRt.isValidFileName("název souboru", Platform.UNIX, false, cp1251));

    Charset cp1252 = Charset.forName("Cp1252");
    assertFalse(PathUtilRt.isValidFileName("имя файла", Platform.UNIX, false, cp1252));
    assertTrue(PathUtilRt.isValidFileName("název souboru", Platform.UNIX, false, cp1252));

    assertTrue(PathUtilRt.isValidFileName("имя файла", Platform.UNIX, false, StandardCharsets.UTF_8));
    assertTrue(PathUtilRt.isValidFileName("název souboru", Platform.UNIX, false, StandardCharsets.UTF_8));
    assertTrue(PathUtilRt.isValidFileName("文件名", Platform.UNIX, false, StandardCharsets.UTF_8));
  }

  @Test
  public void windowsUNCPaths() {
    IoTestUtil.assumeWindows();
    windowsUNCPaths(true);
    windowsUNCPaths(false);
  }

  private static void windowsUNCPaths(boolean convertToSystemDependentPaths) {
    final Function<String, String> toPath = path -> convertToSystemDependentPaths ? FileUtil.toSystemDependentName(path) : path;

    assertThat(PathUtilRt.getFileName(toPath.apply("//wsl$/Ubuntu"))).isEqualTo(toPath.apply("//wsl$/Ubuntu"));
    assertThat(PathUtilRt.getFileName(toPath.apply("//wsl$/Ubuntu/"))).isEqualTo(toPath.apply("//wsl$/Ubuntu"));
    assertThat(PathUtilRt.getFileName(toPath.apply("//wsl$/Ubuntu/usr"))).isEqualTo("usr");
    assertThat(PathUtilRt.getFileName(toPath.apply("//wsl$/Ubuntu/usr/"))).isEqualTo("usr");

    assertThat(PathUtilRt.getParentPath(toPath.apply("//wsl$/Ubuntu"))).isEqualTo("");
    assertThat(PathUtilRt.getParentPath(toPath.apply("//wsl$/Ubuntu/"))).isEqualTo("");
    assertThat(PathUtilRt.getParentPath(toPath.apply("//wsl$/Ubuntu/usr/"))).isEqualTo(toPath.apply("//wsl$/Ubuntu"));
    assertThat(PathUtilRt.getParentPath(toPath.apply("//wsl$/Ubuntu/usr/bin/gcc"))).isEqualTo(toPath.apply("//wsl$/Ubuntu/usr/bin"));
  }

  @Test
  public void isAbsolute() {
    assertThat(OSAgnosticPathUtil.isAbsolute("/tmp")).isTrue();
    assertThat(OSAgnosticPathUtil.isAbsolute("/")).isTrue();
    assertThat(OSAgnosticPathUtil.isAbsolute("C:/")).isTrue();
    assertThat(OSAgnosticPathUtil.isAbsolute("d:\\x")).isTrue();
    assertThat(OSAgnosticPathUtil.isAbsolute("\\\\host")).isTrue();
    assertThat(OSAgnosticPathUtil.isAbsolute("\\\\")).isTrue();
    assertThat(OSAgnosticPathUtil.isAbsolute("//host")).isTrue();

    assertThat(OSAgnosticPathUtil.isAbsolute("")).isFalse();
    assertThat(OSAgnosticPathUtil.isAbsolute("\\a")).isFalse();
    assertThat(OSAgnosticPathUtil.isAbsolute("\\")).isFalse();
    assertThat(OSAgnosticPathUtil.isAbsolute("x:")).isFalse();
  }

  @Test
  public void parentPath() {
    assertThat(OSAgnosticPathUtil.getParent("")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("\\")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("tmp\\a")).isEqualTo("tmp");
    assertThat(OSAgnosticPathUtil.getParent("tmp/a/")).isEqualTo("tmp");
    assertThat(OSAgnosticPathUtil.getParent("tmp")).isNull();

    assertThat(OSAgnosticPathUtil.getParent("/tmp/a")).isEqualTo("/tmp");
    assertThat(OSAgnosticPathUtil.getParent("/tmp/a/")).isEqualTo("/tmp");
    assertThat(OSAgnosticPathUtil.getParent("/tmp")).isEqualTo("/");
    assertThat(OSAgnosticPathUtil.getParent("/")).isNull();

    assertThat(OSAgnosticPathUtil.getParent("c:/tmp/a")).isEqualTo("c:/tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:\\tmp\\a\\")).isEqualTo("c:\\tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:/tmp\\a")).isEqualTo("c:/tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:\\tmp/a/")).isEqualTo("c:\\tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:/tmp")).isEqualTo("c:/");
    assertThat(OSAgnosticPathUtil.getParent("c:\\")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("c:")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("c:x")).isNull();

    assertThat(OSAgnosticPathUtil.getParent("//host/share/a")).isEqualTo("//host/share");
    assertThat(OSAgnosticPathUtil.getParent("\\\\host\\share/a/")).isEqualTo("\\\\host\\share");
    assertThat(OSAgnosticPathUtil.getParent("//host/share")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("\\\\host\\share/")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("//host")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("\\\\")).isNull();

    assertThat(OSAgnosticPathUtil.getParent("/tmp/a/.")).isEqualTo("/tmp/a");
    assertThat(OSAgnosticPathUtil.getParent("/tmp/a/../b")).isEqualTo("/tmp/a/..");
  }

  @Test
  public void comparator() {
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("", "")).isEqualTo(0);
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/", Character.toString('/'))).isEqualTo(0);
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("//", "\\\\")).isEqualTo(0);
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "\\a\\b")).isEqualTo(0);

    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("a", "b")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("b", "a")).isPositive();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "\\a\\b\\")).isNegative();

    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "/a/b/c")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "/a/bc")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "/a/b.c")).isNegative();

    List<String> paths = Arrays.asList("/a/bC", "/a/b-c", "/a/b", "/a/b/c", null);
    Collections.shuffle(paths);
    Collections.sort(paths, OSAgnosticPathUtil.COMPARATOR);
    assertThat(paths).containsExactly(null, "/a/b", "/a/b/c", "/a/b-c", "/a/bC");
  }
}