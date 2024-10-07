// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.testFramework.LightProjectDescriptor;
import groovy.lang.Reference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public class GrAnnotationReferencingUnknownIdentifiersTest extends LightGroovyTestCase {
  private static final String myTuplePrefix = """
    import groovy.transform.TupleConstructor
    """;
  private static final String myMapPrefix = """
      import groovy.transform.MapConstructor
      """;

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5;
  }

  private void doTest(String before) {
    myFixture.enableInspections(new GrAnnotationReferencingUnknownIdentifiers());
    if (before.contains("TupleConstructor")) before = myTuplePrefix + before;
    if (before.contains("MapConstructor")) before = myMapPrefix + before;
    myFixture.configureByText("_.groovy", before);
    myFixture.checkHighlighting();
  }

  public void test_includes() {
    doTest("""
             
             @TupleConstructor(includes = ["a", "b", <warning>"c"</warning>])
             class Rr {
               boolean a
               String b
             }
             """);
  }

  public void test_excludes() {
    doTest("""
             
             @TupleConstructor(excludes = ["a", "b", <warning>"c"</warning>])
             class Rr {
               boolean a
               String b
             }
             """);
  }

  public void test_raw_string() {
    doTest("""
             
             @TupleConstructor(includes = "  a, b   ,   <warning>c</warning>")
             class Rr {
               boolean a
               String b
             }
             """);
  }

  public void test_map_constructor() {
    doTest("""
             
             @MapConstructor(includes = "  aa, bb  , <warning>cd</warning>")
             class Rr {
               boolean aa
               String bb
             }
             """);
  }

  public void test_super_class() {
    doTest("""
             
             class Nn {
               String a
             }
             
             @TupleConstructor(includes = " a,  b,  <warning>c</warning>", includeSuperProperties = true)
             class Rr extends Nn {
               int b
             }
             """);
  }

  public void test_ignored_super_class() {
    doTest("""
             
             class Nn {
               String a
             }
             
             @TupleConstructor(includes = " <warning>a</warning>,  b,  <warning>c</warning>")
             class Rr extends Nn {
               int b
             }
             """);
  }
}