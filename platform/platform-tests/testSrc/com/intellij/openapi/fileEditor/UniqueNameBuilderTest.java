// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.filename.UniqueNameBuilder;
import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProviderKt;
import junit.framework.TestCase;

public class UniqueNameBuilderTest extends TestCase {
  public void testSimple() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/");
    builder.addPath("A", "/Users/yole/idea/foo/bar.java");
    builder.addPath("B", "/Users/yole/idea/baz/bar.java");
    assertEquals("foo/bar.java", builder.getShortPath("A"));
    assertEquals("baz/bar.java", builder.getShortPath("B"));
  }

  public void testTwoLevel() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/");
    builder.addPath("A", "/Users/yole/idea/foo/buy/index.html");
    builder.addPath("B", "/Users/yole/idea/bar/buy/index.html");
    assertEquals("foo/…/index.html", builder.getShortPath("A"));
    assertEquals("bar/…/index.html", builder.getShortPath("B"));
  }
  
  public void testThreeLevel() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/");
    builder.addPath("A", "/Users/yole/idea/foo/before/somedir/index.html");
    builder.addPath("A2", "/Users/yole/idea/foo/after/somedir/index.html");
    builder.addPath("A3", "/Users/yole/fabrique/foo/before/somedir/index.html");
    builder.addPath("B", "/Users/yole/idea/bar/before/somedir/index.html");
    builder.addPath("B2", "/Users/yole/idea/bar/after/somedir/index.html");
    builder.addPath("B3", "/Users/yole/fabrique/bar/after/somedir/index.html");

    assertEquals("idea/foo/before/…/index.html", builder.getShortPath("A"));

    assertEquals("idea/foo/after/…/index.html", builder.getShortPath("A2"));
    assertEquals("fabrique/foo/before/…/index.html", builder.getShortPath("A3"));
    
    assertEquals("idea/bar/before/…/index.html", builder.getShortPath("B"));
    assertEquals("idea/bar/after/…/index.html", builder.getShortPath("B2"));
    assertEquals("fabrique/bar/after/…/index.html", builder.getShortPath("B3"));
  }

  public void testSeparator() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "\\");
    builder.addPath("A", "/Users/yole/idea/foo/buy/index.html");
    builder.addPath("B", "/Users/yole/idea/bar/buy/index.html");
    assertEquals("foo\\…\\index.html", builder.getShortPath("A"));
  }

  public void testRoot() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/");
    builder.addPath("A", "/Users/yole/idea/build/scripts/layouts.gant");
    builder.addPath("B", "/Users/yole/idea/community/build/scripts/layouts.gant");
    assertEquals("build/…/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/…/layouts.gant", builder.getShortPath("B"));

    builder = new UniqueNameBuilder<>("", "/");
    builder.addPath("A", "build/scripts/layouts.gant");
    builder.addPath("B", "community/build/scripts/layouts.gant");
    assertEquals("build/…/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/…/layouts.gant", builder.getShortPath("B"));
  }

  public void testShortenNames() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/");
    builder.addPath("A", "/Users/yole/idea/build/scripts/layouts.gant");
    builder.addPath("B", "/Users/yole/idea/community/build/scripts/layouts.gant");
    assertEquals("build/…/layouts.gant", builder.getShortPath("A"));
    assertEquals("community/…/layouts.gant", builder.getShortPath("B"));
  }

  public void testShortenNamesUnique() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/");
    builder.addPath("A", "/Users/yole/idea/pycharm/download/index.html");
    builder.addPath("B", "/Users/yole/idea/pycharm/documentation/index.html");
    builder.addPath("C", "/Users/yole/idea/fabrique/download/index.html");
    assertEquals("pycharm/download/index.html", builder.getShortPath("A"));
    assertEquals("pycharm/documentation/index.html", builder.getShortPath("B"));
    assertEquals("fabrique/download/index.html", builder.getShortPath("C"));
  }

  public void testShortenNamesUnique2() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/Users/yole/idea", "/");
    builder.addPath("A", "source/components/views/something/tmpl/default.php");
    builder.addPath("B", "source/components/views/something_else/tmpl/default.php");
    assertEquals("something/…/default.php", builder.getShortPath("A"));
    assertEquals("something_else/…/default.php", builder.getShortPath("B"));
  }

  public void testShortenNamesUnique3() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("/js/js.tests/build/node", "/");
    builder.addPath("A", "/js/js.tests/build/node/out-per-module/codegen/firBox/specialBuiltins/throwableImpl_v5.js");
    builder.addPath("B", "/js/js.tests/build/node/out-per-module/codegen/firEs6Box/specialBuiltins/throwableImpl_v5.js");
    builder.addPath("C", "/js/js.tests/build/node/out-per-module-min/codegen/firEs6Box/specialBuiltins/throwableImpl_v5.js");
    builder.addPath("D", "/js/js.tests/build/node/out-per-module-min/codegen/firBox/specialBuiltins/throwableImpl_v5.js");
    assertEquals("out-per-module/…/firBox/…/throwableImpl_v5.js", builder.getShortPath("A"));
    assertEquals("out-per-module/…/firEs6Box/…/throwableImpl_v5.js", builder.getShortPath("B"));
    assertEquals("out-per-module-min/…/firEs6Box/…/throwableImpl_v5.js", builder.getShortPath("C"));
    assertEquals("out-per-module-min/…/firBox/…/throwableImpl_v5.js", builder.getShortPath("D"));
  }

  public void testShortenNamesUnique4() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/");
    builder.addPath("A", "foo1/bar/baar/baz1/qux/quux1/quuux/index.html");
    builder.addPath("B", "foo2/bar/baar/baz2/qux/quux2/quuux/index.html");
    builder.addPath("C", "foo2/bar/baar/baz1/qux/quux1/quuux/index.html");
    builder.addPath("D", "foo2/bar/baz2/qux/quux2/quuux/index.html");
    builder.addPath("E", "foo1/bar/qux/quux1/quuux/index.html");
    builder.addPath("F", "foo2/qux/quux2/quuux/index.html");
    assertEquals("foo1/…/baz1/…/quux1/…/index.html", builder.getShortPath("A"));
    assertEquals("baar/baz2/…/quux2/…/index.html", builder.getShortPath("B"));
    assertEquals("foo2/…/baz1/…/quux1/…/index.html", builder.getShortPath("C"));
    assertEquals("bar/baz2/…/quux2/…/index.html", builder.getShortPath("D"));
    assertEquals("foo1/bar/…/quux1/…/index.html", builder.getShortPath("E"));
    assertEquals("foo2/…/quux2/…/index.html", builder.getShortPath("F"));
  }

  public void testFilesWithoutExtensions() {
    UniqueNameBuilder<String> builder = new UniqueNameBuilder<>("", "/");
    builder.addPath("A", "foo/.htaccess");
    builder.addPath("B", "bar/.htaccess");
    String shortPath = builder.getShortPath("A");
    assertEquals("foo/.htaccess", UniqueNameEditorTabTitleProviderKt.getEditorTabText(shortPath, builder.getSeparator(), false));
    assertEquals("foo/.htaccess", UniqueNameEditorTabTitleProviderKt.getEditorTabText(shortPath, builder.getSeparator(), true));

  }
}
