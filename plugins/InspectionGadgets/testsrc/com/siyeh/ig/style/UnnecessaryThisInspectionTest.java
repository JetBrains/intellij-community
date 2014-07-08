package com.siyeh.ig.style;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class UnnecessaryThisInspectionTest extends LightInspectionTestCase {

  public void testSimpleField() {
    doTest("class A {" +
           "  private int x;" +
           "  void m() {" +
           "    /*'this' is unnecessary in this context*/this/**/.x = 3;" +
           "  }" +
           "}");
  }

  public void testSimpleMethod() {
    doTest("class A {" +
           "  void x() {}" +
           "  void m() {" +
           "    /*'this' is unnecessary in this context*/this/**/.x();" +
           "  }" +
           "}");
  }

  public void testQualifiedThisNeeded() {
    doTest("class A {" +
           "    public void foo(String s) {}" +
           "    class D{" +
           "        public void foo(String s) {}" +
           "    }" +
           "    class C extends D {" +
           "        class Box {" +
           "            void bar() {" +
           "                A.this.foo(\"\");" +
           "            }" +
           "        }" +
           "    }" +
           "}");
  }

  public void testQualifiedThisNeeded2() {
    doTest("class A {" +
           "  private int x;" +
           "  public void x() {}" +
           "  class X {" +
           "    private int x;" +
           "    public void x(){}" +
           "    void y() {" +
           "      A.this.x = 4;" +
           "      A.this.x();" +
           "    }" +
           "  }" +
           "}");
  }

  public void testQualifiedThisNotNeeded() {
    doTest("class A {" +
           "    public void foo(String s) {}" +
           "    class D{" +
           "        private void foo(String s) {}" +
           "    }" +
           "    class C extends D {" +
           "        class Box {" +
           "            void bar() {" +
           "                /*'A.this' is unnecessary in this context*/A.this/**/.foo(\"\");" +
           "            }" +
           "        }" +
           "    }" +
           "}");
  }

  public void testQualifiedThisDifferentPackage() {
    myFixture.addClass("package foo;" +
                       "public abstract class Foo {" +
                       "        protected  void foo() {}" +
                       "}");
    doTest("package bar;" +
           "import foo.Foo;" +
           "final class Bar extends Foo {" +
           "    public void foo() {}" +
           "        final class InnerBar extends Foo {" +
           "                void bar() {" +
           "                        Bar.this.foo(); " +
           "                }" +
           "        }" +
           "}");
  }

  /**
   * IDEA-42154
   */
  public void testCatchBlockParameter() {
    doTest("class A {" +
           "    private Throwable throwable = null;" +
           "    public void method() {" +
           "        try {" +
           "        } catch (Throwable throwable) {" +
           "            this.throwable = throwable;" +
           "            throwable.printStackTrace();" +
           "        }" +
           "    }" +
           "}");
  }

  public void testForLoopParameter() {
    doTest("class A {" +
           "  private int i = 0;" +
           "  void m() {" +
           "    for (int i = 0; i < 10; i++) {" +
           "      this.i = 3;" +
           "    }" +
           "  }" +
           "}");
  }

  public void testMethodParameter() {
    doTest("class A {" +
           "  private int i = 1;" +
           "  void m(int i) {" +
           "    this.i = 3;" +
           "  }" +
           "}");
  }

  public void testLocalVariable() {
    doTest("class A {" +
           "  private int i = 2;" +
           "  void foo() {" +
           "    int i = 3;" +
           "    this.i=4;" +
           "  }" +
           "}");
  }


  public void testLambdaMethodRefSelfRefs() {
    doTest("class Main {" +
           "    Runnable lambdaExpression = () -> System.out.println(this.lambdaExpression);" +
           "    Runnable methodReference = this.methodReference::run;" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new UnnecessaryThisInspection();
  }
}