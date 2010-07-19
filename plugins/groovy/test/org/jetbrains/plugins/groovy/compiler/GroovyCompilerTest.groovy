/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.compiler;


import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import junit.framework.AssertionFailedError

/**
 * @author peter
 */
public class GroovyCompilerTest extends GroovyCompilerTestCase {
  @Override protected void setUp() {
    super.setUp();
    addGroovyLibrary(myModule);
  }

  public void testPlainGroovy() throws Throwable {
    myFixture.addFileToProject("A.groovy", "println '239'");
    assertEmpty(make());
    assertOutput("A", "239");
  }

  public void testJavaDependsOnGroovy() throws Throwable {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}");
    myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                             "  def foo() {" +
                                             "    239" +
                                             "  }" +
                                             "}");
    assertEmpty(make());
    assertOutput("Foo", "239");
  }

  public void testCorrectFailAndCorrect() throws Exception {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}");
    final String barText = "class Bar {" + "  def foo() { 239  }" + "}";
    final PsiFile file = myFixture.addFileToProject("Bar.groovy", barText);
    assertEmpty(make());
    assertOutput("Foo", "239");

    setFileText(file, "class Bar {}");
    try {
      make();
      fail("Make should fail");
    }
    catch (RuntimeException e) {
      if (!(e.getCause() instanceof AssertionFailedError)) {
        throw e;
      }
    }

    setFileText(file, barText);
    assertEmpty(make());
    assertOutput("Foo", "239");
  }

  public void testRenameToJava() throws Throwable {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}");

    final PsiFile bar =
      myFixture.addFileToProject("Bar.groovy", "public class Bar {" + "public int foo() { " + "  return 239;" + "}" + "}");

    assertEmpty(make());
    assertOutput("Foo", "239");

    setFileName bar, "Bar.java"

    assertEmpty(make());
    assertOutput("Foo", "239");
  }

  public void testTransitiveJavaDependency() throws Throwable {
    final VirtualFile ifoo = myFixture.addClass("public interface IFoo { int foo(); }").getContainingFile().getVirtualFile();
    myFixture.addClass("public class Foo implements IFoo {" +
                       "  public int foo() { return 239; }" +
                       "}");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "Foo foo\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}");
    assertEmpty(make());
    assertOutput("Bar", "239");

    touch(ifoo);
    touch(bar.getVirtualFile());

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assertEmpty make()
    assertOutput("Bar", "239");
  }

  public void testTransitiveJavaDependencyThroughGroovy() throws Throwable {
    myFixture.addClass("public class IFoo { void foo() {} }").getContainingFile().getVirtualFile();
    myFixture.addFileToProject("Foo.groovy", "class Foo {\n" +
                       "  static IFoo f\n" +
                       "  public int foo() { return 239; }\n" +
                       "}");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo {" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}");
    assertEmpty(make());
    assertOutput("Bar", "239");

    deleteClassFile("IFoo");
    touch(bar.getVirtualFile());

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc error"));
    assertEmpty make()
    assertOutput("Bar", "239");
  }

  public void testDeleteTransitiveJavaClass() throws Throwable {
    myFixture.addClass("public interface IFoo { int foo(); }");
    myFixture.addClass("public class Foo implements IFoo {" +
                       "  public int foo() { return 239; }" +
                       "}");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "Foo foo\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}");
    assertEmpty(make());
    assertOutput("Bar", "239");

    deleteClassFile("IFoo");
    touch(bar.getVirtualFile());

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assertEmpty make()
    assertOutput("Bar", "239");
  }

  public void testGroovyDependsOnGroovy() throws Throwable {
    myFixture.addClass("public class JustToMakeGroovyGenerateStubs {}");
    myFixture.addFileToProject("Foo.groovy", "class Foo { }");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "def foo(Foo f) {}\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(239);" +
                                                                 "}" +
                                                                 "}");
    assertEmpty(make());
    assertOutput("Bar", "239");

    touch(bar.getVirtualFile());

    assertEmpty(make());
    assertOutput("Bar", "239");
  }

  public void testMakeInTests() throws Throwable {
    setupTestSources();
    myFixture.addFileToProject("tests/Super.groovy", "class Super {}");
    assertEmpty(make());

    myFixture.addFileToProject("tests/Sub.groovy", "class Sub {\n" +
                                                                       "  Super xxx() {}\n" +
                                                                       "  static void main(String[] args) {" +
                                                                       "    println 'hello'" +
                                                                       "  }" +
                                                                       "}");
    myFixture.addFileToProject("tests/Java.java", "public class Java {}");
    assertEmpty(make());
    assertOutput("Sub", "hello");
  }

  public void testTestsDependOnProduction() throws Throwable {
    setupTestSources();
    myFixture.addFileToProject("src/com/Bar.groovy", "package com\n" +
                                                     "class Bar {}");
    myFixture.addFileToProject("src/com/ToGenerateStubs.java", "package com;\n" +
                                                     "public class ToGenerateStubs {}");
    myFixture.addFileToProject("tests/com/BarTest.groovy", "package com\n" +
                                                       "class BarTest extends Bar {}");
    assertEmpty(make());
  }

  public void testStubForGroovyExtendingJava() throws Exception {
    myFixture.addClass("public class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo {}");
    myFixture.addClass("public class Goo extends Bar {}");

    assertEmpty(make());
  }

  public void testDontApplyTransformsFromSameModule() throws Exception {
    addTransform();

    myFixture.addClass("public class JavaClassToGenerateStubs {}");

    assertEmpty(make());

  }

  private void addTransform() throws IOException {
    myFixture.addFileToProject("Transf.groovy",
                               "import org.codehaus.groovy.ast.*\n" +
                               "import org.codehaus.groovy.control.*\n" +
                               "import org.codehaus.groovy.transform.*\n" +
                               "@GroovyASTTransformation(phase = CompilePhase.CONVERSION)\n" +
                               "public class Transf implements ASTTransformation {\n" +
                               "  void visit(ASTNode[] nodes, SourceUnit sourceUnit) {\n" +
                               "    ModuleNode module = nodes[0]\n" +
                               "    for (clazz in module.classes) {\n" +
                               "      if (clazz.name.contains('Bar')) " +
                               "        module.addStaticImportClass('Foo', ClassHelper.makeWithoutCaching(Foo.class));\n" +
                               "    }\n" +
                               "  }\n" +
                               "}");

    myFixture.addFileToProject("Foo.groovy", "class Foo {\n" +
                                             "static def autoImported() { 239 }\n" +
                                             "}");

    CompilerConfiguration.getInstance(getProject()).addResourceFilePattern("*.ASTTransformation");

    myFixture.addFileToProject("META-INF/services/org.codehaus.groovy.transform.ASTTransformation", "Transf");
  }

  public void testApplyTransformsFromDependencies() throws Exception {
    addTransform();

    myFixture.addFileToProject("dependent/Bar.groovy", "class Bar {\n" +
                                                       "  static Object zzz = autoImported()\n" +
                                                       "  static void main(String[] args) {\n" +
                                                       "    println zzz\n" +
                                                       "  }\n" +
                                                       "}");

    myFixture.addFileToProject("dependent/AJavaClass.java", "class AJavaClass {}");

    Module dep = addDependentModule();

    addGroovyLibrary(dep);

    assertEmpty(make());
    assertOutput("Bar", "239", dep);
  }

  public void testExtendFromGroovyAbstractClass() throws Exception {
    myFixture.addFileToProject "Super.groovy", "abstract class Super {}"
    myFixture.addFileToProject "AJava.java", "public class AJava {}"
    assertEmpty make()

    myFixture.addFileToProject "Sub.groovy", "class Sub extends Super {}"
    assertEmpty make()
  }

  public void test1_7InnerClass() throws Exception {
    myFixture.addFileToProject "Foo.groovy", """
class Foo {
  static class Bar {}
}"""
    myFixture.addFileToProject "AJava.java", "public class AJava extends Foo.Bar {}"
    assertEmpty make()
  }

  public void testRecompileDependentClass() throws Exception {
    def cloud = myFixture.addFileToProject("Cloud.groovy", """
class Cloud {
  def accessFooProperty(Foo c) {
    c.prop = 2
  }
}
""")
    myFixture.addFileToProject "Foo.groovy", """
class Foo {
  def withGooParameter(Goo x) {}
}"""
    def goo = myFixture.addFileToProject("Goo.groovy", "class Goo {}")

    assertEmpty make()

    touch(cloud.virtualFile)
    touch(goo.virtualFile)
    assertEmpty make()
  }

  public void testRecompileImportedClass() throws Exception {
    def bar = myFixture.addFileToProject("pack/Bar.groovy", """
package pack
import pack.Foo
class Bar {}
""")
    myFixture.addFileToProject "pack/Foo.groovy", """
package pack
class Foo extends Goo {
}"""
    def goo = myFixture.addFileToProject("pack/Goo.groovy", """
package pack
class Goo {}""")

    assertEmpty make()

    touch(bar.virtualFile)
    touch(goo.virtualFile)
    assertEmpty make()
  }

  public void testRecompileDependentClassesWithOnlyOneChanged() throws Exception {
    def bar = myFixture.addFileToProject("Bar.groovy", """
class Bar {
  Foo f
}
""")
    myFixture.addFileToProject "Foo.groovy", """
class Foo extends Bar {
}"""

    assertEmpty make()

    touch(bar.virtualFile)
    assertEmpty make()
  }


}
