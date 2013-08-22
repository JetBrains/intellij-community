package com.siyeh.ig.imports;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class UnusedImportInspectionTest extends LightInspectionTestCase {

  public void testShadowedImports() {
    addEnvironmentClass("package java.util; public class HashTable {}");
    addEnvironmentClass("package java.util; public class List {}");
    addEnvironmentClass("package java.awt; public class List { public static final int ABORT = 1; }");
    addEnvironmentClass("package java.awt; public class Component {}");
    doTest("package test;" +
           "import java.util.*;" +
           "import java.awt.*;" +
           "import java.awt.List;" +
           "public class Bar2 extends Component {" +
           "  void foo() {" +
           "    new Hashtable();" +
           "    System.out.println(List.ABORT);" +
           "  }" +
           "}");
  }

  public void testStaticImport() {
    doTest("/*Unused import 'import java.util.Map.*;'*/import java.util.Map.*;/**/" +
           "import static java.util.Map.*;" +
           "public class X { " +
           "  void m(Entry e) {}" +
           "}");
  }

  public void testStaticImport2() {
    doTest("import static java.lang.Math.*;" +
           "public class X {" +
           "    static {" +
           "        System.out.println(\"\"+PI);" +
           "    }" +
           "}");
  }
  
  public void testNoWarning() {
    doTest("import java.util.List;" +
           "import java.util.ArrayList;" +
           "import static java.lang.Integer.SIZE;" +
           "public class X {" +
           "    private final List<Integer> list = new ArrayList<Integer>(SIZE);" +
           "    public void add(int i) {" +
           "        list.add(i);" +
           "    }" +
           "}");
  }

  public void testInnerClassAndMethod() {
    addEnvironmentClass("package one; public class X { public class Inner {} public static void method() {}}");
    doTest("package one;" +
           "import static one.X.*; " +
           "import one.X.*;" +
           "public class Y { " +
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

  @Override
  protected LocalInspectionTool getInspection() {
    return new UnusedImportInspection();
  }
}