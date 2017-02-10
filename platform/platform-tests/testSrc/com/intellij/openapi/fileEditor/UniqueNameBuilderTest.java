package com.intellij.openapi.fileEditor;

import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import junit.framework.TestCase;

/**
 * @author yole
 */
public class UniqueNameBuilderTest extends TestCase {
  public void testSimple() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/", 100);
    builder.addPath("A", "/Users/yole/idea/foo/bar.java");
    builder.addPath("B", "/Users/yole/idea/baz/bar.java");
    assertEquals("foo/bar.java", builder.getShortPath("A"));
  }

  public void testTwoLevel() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/", 100);
    builder.addPath("A", "/Users/yole/idea/foo/buy/index.html");
    builder.addPath("B", "/Users/yole/idea/bar/buy/index.html");
    assertEquals("foo/\u2026/index.html", builder.getShortPath("A"));
  }

  public void testSeparator() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "\\", 100);
    builder.addPath("A", "/Users/yole/idea/foo/buy/index.html");
    builder.addPath("B", "/Users/yole/idea/bar/buy/index.html");
    assertEquals("foo\\\u2026\\index.html", builder.getShortPath("A"));
  }

  public void testRoot() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/", 100);
    builder.addPath("A", "/Users/yole/idea/build/scripts/layouts.gant");
    builder.addPath("B", "/Users/yole/idea/community/build/scripts/layouts.gant");
    assertEquals("build/\u2026/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/\u2026/layouts.gant", builder.getShortPath("B"));

    builder = new UniqueNameBuilder<>("", "/", 100);
    builder.addPath("A", "build/scripts/layouts.gant");
    builder.addPath("B", "community/build/scripts/layouts.gant");
    assertEquals("build/\u2026/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/\u2026/layouts.gant", builder.getShortPath("B"));
  }

  public void testShortenNames() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/", 25);
    builder.addPath("A", "/Users/yole/idea/build/scripts/layouts.gant");
    builder.addPath("B", "/Users/yole/idea/community/build/scripts/layouts.gant");
    assertEquals("build/\u2026/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/\u2026/layouts.gant", builder.getShortPath("B"));
  }

  public void testShortenNamesUnique() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/", 25);
    builder.addPath("A", "/Users/yole/idea/pycharm/download/index.html");
    builder.addPath("B", "/Users/yole/idea/pycharm/documentation/index.html");
    builder.addPath("C", "/Users/yole/idea/fabrique/download/index.html");
    assertEquals("pycharm/\u2026/index.html", builder.getShortPath("A"));
  }

  public void testShortenNamesUnique2() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/", 25);
    builder.addPath("A", "source/components/views/something/tmpl/default.php");
    builder.addPath("B", "source/components/views/something_else/tmpl/default.php");
    assertEquals("something/\u2026/default.php", builder.getShortPath("A"));
    assertEquals("something_else/\u2026/default.php", builder.getShortPath("B"));
  }

  public void testFilesWithoutExtensions() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/", 25);
    builder.addPath("A", "foo/.htaccess");
    builder.addPath("B", "bar/.htaccess");
    String shortPath = builder.getShortPath("A");
    assertEquals("foo/.htaccess", UniqueNameEditorTabTitleProvider.getEditorTabText(shortPath, builder.getSeparator(), false));
    assertEquals("foo/.htaccess", UniqueNameEditorTabTitleProvider.getEditorTabText(shortPath, builder.getSeparator(), true));

  }
}
