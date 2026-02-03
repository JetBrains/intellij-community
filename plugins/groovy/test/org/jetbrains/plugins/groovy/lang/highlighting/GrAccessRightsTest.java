// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

public class GrAccessRightsTest extends GroovyLatestTest implements HighlightingTest {
  public GrAccessRightsTest() {
    super("highlighting");
  }

  @Override
  public @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return Arrays.asList(GroovyAccessibilityInspection.class);
  }

  @Test
  public void privateMembers() {
    fileHighlightingTest("accessPrivateMembers.groovy");
  }

  @Test
  public void privateScriptMembers() {
    fileHighlightingTest("accessPrivateScriptMembers.groovy");
  }

  @Test
  public void constructor() {
    getFixture().addClass("""
                             package p1;
                             public class MyClass {
                               public MyClass() {}
                               protected MyClass(int i) {}
                               MyClass(int i, int j) {}
                               private MyClass(int i, int j, int k) {}
                             }
                             """);
    fileHighlightingTest("accessConstructor.groovy");
  }

  @Test
  public void method() {
    getFixture().addClass("""
                             package p1;
                             public class MyClass {
                               public void publicMethod() {}
                               protected void protectedMethod() {}
                               void packageLocalMethod() {}
                               private void privateMethod() {}
                             }
                             """);
    fileHighlightingTest("accessMethod.groovy");
  }

  @Test
  public void property() {
    fileHighlightingTest("accessProperty.groovy");
  }

  @Test
  public void traitMethod() {
    fileHighlightingTest("accessTraitMethod.groovy");
  }

  @Test
  public void nestedClass() {
    getFixture().addClass("""
                             package p1;

                             public class Outer {
                               public static class PublicClass {}
                               protected static class ProtectedClass {}
                               static class PackageLocalClass {}
                               private static class PrivateClass {}
                             }
                             """);
    fileHighlightingTest("accessClass.groovy");
  }

  @Test
  public void methodCS() {
    fileHighlightingTest("accessMethodCS.groovy");
  }

  @Test
  public void staticImportedProperty() {
    fileHighlightingTest("accessStaticImportedProperty.groovy");
  }
}
