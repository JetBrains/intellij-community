/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.imports;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;

import java.io.File;
import java.util.Collections;

public class UnusedImportGlobalInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/InspectionGadgets/test/com/siyeh/igtest/imports/globalInspection";
  }

  public void testInnerClassImport() {
    myFixture.addClass("package pkg;" +
                       "interface Action {" +
                       "    interface SuperInnerInterface {}" +
                       "}");
    myFixture.addClass("package pkg;" +
                       "class ConceteAction implements Action {" +
                       "    interface SubInnerInterface {}" +
                       "}");
     doTest("package pkg;" +
           "/*Unused import 'import pkg.Action.SuperInnerInterface;'*/import pkg.Action.SuperInnerInterface;/**/" +
           "import static pkg.ConceteAction.*;" +
           "class Main {" +
           "    SubInnerInterface innerClass;" +
           "    void k()  {" +
           "        new SuperInnerInterface() { };" +
           "    }" +
           "}");
  }

  public void testShadowedImports() {
    myFixture.addClass("package java.util; public class HashTable {}");
    myFixture.addClass("package java.util; public class List {}");
    myFixture.addClass("package java.awt; public class List { public static final int ABORT = 1; }");
    myFixture.addClass("package java.awt; public class Component {}");
    doTest("package test;" +
           "import java.util.*;" +
           "import java.awt.*;" +
           "import java.awt.List;" +
           "class Bar2 extends Component {" +
           "  void foo() {" +
           "    new Hashtable();" +
           "    System.out.println(List.ABORT);" +
           "  }" +
           "}");
  }

  public void testStaticImport() {
    doTest("/*Unused import 'import java.util.Map.*;'*/import java.util.Map.*;/**/" +
           "import static java.util.Map.*;" +
           "class X { " +
           "  void m(Entry e) {}" +
           "}");
  }

  public void testStaticImport2() {
    doTest("import static java.lang.Math.*;" +
           "class X {" +
           "    static {" +
           "        System.out.println(\"\"+PI);" +
           "    }" +
           "}");
  }

  public void testExactStaticImport() {
    doTest("package a;\n" +
           "import static java.lang.Math.abs;\n" +
           "/*Unused import 'import static java.lang.Math.max;'*/import static java.lang.Math.max;/**/\n" +
           "class Main {{\n" +
           "  abs(1);\n" +
           "}}");
  }

  public void testNoHighlightingInInvalidCode() {
    myFixture.configureByText("a.java",
                              "import<EOLError></EOLError>\n" +
                              "import java.util.*<error> </error><error>Math.max;</error>\n" +
                              "class Main {}");
    myFixture.testHighlighting(true, false, false);
  }

  public void testStaticImportOnDemandConflict1() {
    myFixture.addClass("package a;" +
                       "public class Parent {" +
                       "  public static final int FOOBAR = 1;" +
                       "  public static class FooBar {}" +
                       "}");
    myFixture.addClass("package a;" +
                       "public class FooBar {" +
                       "}");
    doTest("package b;\n" +
           "import a.*;\n" +
           "import a.FooBar;\n" +
           "import static a.Parent.*;\n" +
           "class Main {\n" +
           "    public static void main() {\n" +
           "        Parent parent = new Parent();\n" +
           "        int i = FOOBAR;\n" +
           "        FooBar foobar = new FooBar();\n" +
           "    }\n" +
           "}");
  }

  public void testStaticImportOnDemandConflict2() {
    myFixture.addClass("package a;" +
                       "public class Parent {" +
                       "  public static final int FOOBAR = 1;" +
                       "  public static class FooBar {}" +
                       "}");
    myFixture.addClass("package a;" +
                       "public class FooBar {" +
                       "}");
    doTest("package b;\n" +
           "import a.*;\n" +
           "import static a.Parent.*;\n" +
           "import static a.Parent.FooBar;\n" +
           "class Main {\n" +
           "    public static void main() {\n" +
           "        Parent parent = new Parent();\n" +
           "        int i = FOOBAR;\n" +
           "        FooBar foobar = new FooBar();\n" +
           "    }\n" +
           "}");
  }

  public void testInherited() {
    myFixture.addClass("package a;" +
                       "class GrandParent {" +
                       "  public static final int FOOBAR = 1;" +
                       "}");
    myFixture.addClass("package a;" +
                       "public class Parent extends GrandParent {" +
                       "}");
    doTest("package b;\n" +
           "import static a.Parent.*;\n" +
           "class Main {\n" +
           "    public static void main() {\n" +
           "        int i = FOOBAR;\n" +
           "    }\n" +
           "}");
  }

  public void testNoWarning() {
    doTest("import java.util.List;" +
           "import java.util.ArrayList;" +
           "import static java.lang.Integer.SIZE;" +
           "class X {" +
           "    private final List<Integer> list = new ArrayList<Integer>(SIZE);" +
           "    public void add(int i) {" +
           "        list.add(i);" +
           "    }" +
           "}");
  }

  public void testInnerClassAndMethod() {
    myFixture.addClass("package one; public class X { public class Inner {} public static void method() {}}");
    doTest("package one;" +
           "import static one.X.*; " +
           "import one.X.*;" +
           " class Y { " +
           "    void m() {" +
           "        method(); " +
           "        Inner inner = new X().new Inner();" +
           "    }" +
           "}");
  }

  public void testRedundantImport() {
    doTest("/*Unused import 'import java.util.ArrayList;'*/import java.util.ArrayList;/**/" +
           "import java.util.ArrayList;" +
           "class X { " +
           "    void foo(ArrayList l) {}" +
           "}");
  }

  public void testNoWarn() {
    myFixture.addClass("package java.awt; public class List extends Component {}");
    doTest("import javax.swing.*;\n" +
           "import java.awt.*;\n" +
           "import java.util.*;\n" +
           "import java.util.List;\n" +
           "\n" +
           "class ImportTest extends Component {\n" +
           "\n" +
           "  Collection<String> c;\n" +
           "  List<Integer> l;\n" +
           "  JComponent jc;\n" +
           "}");
  }

  public void testOrderIsNotImportant() {
    doTest("package a;" +
           "import java.util.*;" +
           "/*Unused import 'import java.util.List;'*/import java.util.List;/**/" +
           "class X {{" +
           "  List list = new ArrayList();" +
           "}}");

  }

  public void testConflictInSamePackage() {
    myFixture.addClass("package a; public class List {}");
    doTest("package a;" +
           "import java.util.List;" +
           "import java.util.*;" +
           "class X {{" +
           "  List list = new ArrayList();" +
           "}}");
  }

  public void testNoConflictInSamePackage() {
    doTest("package a;" +
           "/*Unused import 'import java.util.List;'*/import java.util.List;/**/" +
           "import java.util.*;" +
           "class X {{" +
           "  List list = new ArrayList();" +
           "}}");
  }

  public void testModuleInfo() {
    myFixture.addClass("package a; public class A {}");
    myFixture.addFileToProject("module-info.java", "import a.A;\n" +
                                                   "module my {" +
                                                   "  uses A;" +
                                                   "}");
    doTest();
  }

  private void doTest(String classText) {
    myFixture.addClass(classText);

    doTest();
  }

  private void doTest() {
    GlobalInspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new UnusedImportInspection());
    AnalysisScope scope = new AnalysisScope(myFixture.getProject());
    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), Collections.<InspectionToolWrapper<?, ?>>singletonList(toolWrapper));

    InspectionTestUtil.runTool(toolWrapper, scope, globalContext);
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, new File(getTestDataPath(), getTestName(false)).getPath());
  }

}