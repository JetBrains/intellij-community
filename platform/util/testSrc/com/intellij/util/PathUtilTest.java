// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.util.PathUtilRt.Platform;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
  }

  @Test
  public void fileExt() {
    assertThat(PathUtilRt.getFileExtension("foo.html")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("foo.html/")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("/foo.html/")).isEqualTo("html");
    assertThat(PathUtilRt.getFileExtension("/bar/foo.html/")).isEqualTo("html");
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
}