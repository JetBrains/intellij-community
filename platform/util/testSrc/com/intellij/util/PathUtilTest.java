// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.util.PathUtilRt.Platform;
import org.junit.Test;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
  public void suggestFileNameReplacesCharsUnmappableByFsCharset() {
    // The narrow no-break space (U+202F) that the JDK's CLDR locale data inserts before AM/PM in localized times
    // is neither whitespace nor an invalid file-name char, so only the fs-charset filter can catch it.
    String name = "Uncommitted changes before Checkout at 6/25/26, 3:19 PM";

    // With an ASCII filesystem charset (e.g. POSIX/C locale in a minimal Linux container), U+202F is unmappable
    // and must be replaced, otherwise File.toPath() throws InvalidPathException (IJPL-247915 / CLion crash).
    String ascii = PathUtilRt.suggestFileName(name, false, false, StandardCharsets.US_ASCII);
    assertThat(ascii).isEqualTo("Uncommitted_changes_before_Checkout_at_6_25_26,_3_19_PM");
    assertTrue("result must be encodable by the fs charset", StandardCharsets.US_ASCII.newEncoder().canEncode(ascii));

    // With a Unicode filesystem charset the character is mappable and is preserved (no regression on UTF-8 systems).
    String utf8 = PathUtilRt.suggestFileName(name, false, false, StandardCharsets.UTF_8);
    assertThat(utf8).isEqualTo("Uncommitted_changes_before_Checkout_at_6_25_26,_3_19 PM");

    // A null charset means "no filesystem restriction" (Windows/macOS) and must not touch encodable characters.
    String noCharset = PathUtilRt.suggestFileName(name, false, false, null);
    assertThat(noCharset).isEqualTo("Uncommitted_changes_before_Checkout_at_6_25_26,_3_19 PM");
  }

  @Test
  public void suggestFileNamePreventsUnixPathEncodeCrash() {
    // Reproduces IJPL-247915. The shelf dir name (built from DateFormatUtil.formatDateTime) contains a narrow
    // no-break space (U+202F) that the JDK's CLDR locale data inserts before AM/PM. On a filesystem whose
    // sun.jnu.encoding is ASCII (POSIX/C locale, typical of minimal Linux containers), the name reaches
    // ShelveChangesManager.generateUniqueSchemePatchDir -> File.toPath(), and sun.nio.fs.UnixPath.encode cannot
    // encode U+202F -> InvalidPathException. We cannot trigger the real .toPath() from a unit test, because
    // sun.jnu.encoding is fixed at JVM startup (and pinned to UTF-8 on macOS), so this replays UnixPath.encode's step.
    Charset fsCharset = StandardCharsets.US_ASCII;
    String shelfName = "Uncommitted changes before Checkout at 6/25/26, 3:19" + (char)0x202F + "PM";

    // Old behavior == sanitizing without an fs charset: U+202F survives, and converting to a Path crashes as reported.
    String unfixed = PathUtilRt.suggestFileName(shelfName, false, false, null);
    assertThat(unfixed.indexOf(0x202F)).as("U+202F survives the charset-unaware sanitizer").isNotNegative();
    assertThatThrownBy(() -> encodeLikeUnixPath(unfixed, fsCharset))
      .isInstanceOf(InvalidPathException.class)
      .hasMessageContaining("Malformed input or input contains unmappable characters");

    // With the fix (sanitizing against the fs charset), the name is fully encodable, so .toPath() would not throw.
    String fixed = PathUtilRt.suggestFileName(shelfName, false, false, fsCharset);
    assertThat(fixed.indexOf(0x202F)).as("U+202F is replaced when sanitizing for an ASCII filesystem").isEqualTo(-1);
    encodeLikeUnixPath(fixed, fsCharset); // must not throw
  }

  /**
   * Replays what {@code sun.nio.fs.UnixPath.encode} does on {@code File.toPath()}: encode the name with the filesystem
   * charset in REPORT mode and rethrow any coding failure as {@link InvalidPathException} with the same message.
   */
  private static void encodeLikeUnixPath(String name, Charset fsCharset) {
    try {
      fsCharset.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(name));
    }
    catch (CharacterCodingException e) {
      throw new InvalidPathException(name, "Malformed input or input contains unmappable characters");
    }
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
    if (SystemInfo.isWindows) {
      assertThat(OSAgnosticPathUtil.isAbsolute("\\\\host")).isTrue();
      assertThat(OSAgnosticPathUtil.isAbsolute("\\\\")).isTrue();
    }
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
    assertThat(OSAgnosticPathUtil.getParent("/tmp/a/.")).isEqualTo("/tmp/a");
    assertThat(OSAgnosticPathUtil.getParent("/tmp/a/../b")).isEqualTo("/tmp/a/..");


    assertThat(OSAgnosticPathUtil.getParent("c:/tmp/a")).isEqualTo("c:/tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:\\tmp\\a\\")).isEqualTo("c:\\tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:/tmp\\a")).isEqualTo("c:/tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:\\tmp/a/")).isEqualTo("c:\\tmp");
    assertThat(OSAgnosticPathUtil.getParent("c:/tmp")).isEqualTo("c:/");
    assertThat(OSAgnosticPathUtil.getParent("c:\\")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("c:")).isNull();
    assertThat(OSAgnosticPathUtil.getParent("c:x")).isNull();
    if (SystemInfo.isWindows) {
      assertThat(OSAgnosticPathUtil.getParent("//host/share/a")).isEqualTo("//host/share");
      assertThat(OSAgnosticPathUtil.getParent("\\\\host\\share/a/")).isEqualTo("\\\\host\\share");
      assertThat(OSAgnosticPathUtil.getParent("//host/share")).isNull();
      assertThat(OSAgnosticPathUtil.getParent("\\\\host\\share/")).isNull();
      assertThat(OSAgnosticPathUtil.getParent("//host")).isNull();
      assertThat(OSAgnosticPathUtil.getParent("\\\\")).isNull();
    }
  }

  @Test
  public void comparator() {
    //noinspection EqualsWithItself
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("", "")).isEqualTo(0);
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/", "")).isPositive();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("", "\\")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/", Character.toString('/'))).isEqualTo(0);
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("//", "\\\\")).isEqualTo(0);
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "\\a\\b")).isEqualTo(0);

    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("a", "b")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("b", "a")).isPositive();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "\\a\\b\\")).isNegative();

    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "/a/b/c")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "/a/bc")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b", "/a/b.c")).isNegative();
    assertThat(OSAgnosticPathUtil.COMPARATOR.compare("/a/b/c", "/a/b.c")).isNegative();

    List<String> paths = Arrays.asList("/a/bC", "/a/b-c", "/a/b", "/a/b/c", null);
    Collections.shuffle(paths);
    Collections.sort(paths, OSAgnosticPathUtil.COMPARATOR);
    assertThat(paths).containsExactly(null, "/a/b", "/a/b/c", "/a/b-c", "/a/bC");
  }

  @Test
  public void startsWith() {
    assertThat(OSAgnosticPathUtil.startsWith("", "")).isTrue();
    assertThat(OSAgnosticPathUtil.startsWith("/", "/")).isTrue();
    assertThat(OSAgnosticPathUtil.startsWith("/", "\\")).isTrue();
    assertThat(OSAgnosticPathUtil.startsWith("", "\\")).isFalse();
    assertThat(OSAgnosticPathUtil.startsWith("/a\\b", "\\a")).isTrue();
    assertThat(OSAgnosticPathUtil.startsWith("/a\\b", "\\a/")).isTrue();
    assertThat(OSAgnosticPathUtil.startsWith("/ab", "\\a")).isFalse();
    assertThat(OSAgnosticPathUtil.startsWith("/ab", "/a")).isFalse();
    assertThat(OSAgnosticPathUtil.startsWith("/a/b\\c", "/a\\b")).isTrue();
    assertThat(OSAgnosticPathUtil.startsWith("/a/bc", "/a\\b")).isFalse();
  }
}