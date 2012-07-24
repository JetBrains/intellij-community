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
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestLoggerFactory
import junit.framework.AssertionFailedError

/**
 * @author peter
 */
public abstract class GroovyCompilerTest extends GroovyCompilerTestCase {
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
    make();
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
    make()
    assertOutput("Foo", "239");

    setFileText(file, "class Bar {}");
    shouldFail { make() }

    setFileText(file, barText);
    make();
    assertOutput("Foo", "239");
  }

  private void shouldFail(Closure action) {
    try {
      action()
      fail("Make should fail");
    }
    catch (RuntimeException e) {
      if (!(e.getCause() instanceof AssertionFailedError)) {
        throw e;
      }
    }
  }

  public void testRenameToJava() throws Throwable {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}");

    final PsiFile bar =
      myFixture.addFileToProject("Bar.groovy", "public class Bar {" + "public int foo() { " + "  return 239;" + "}" + "}");

    make();
    assertOutput("Foo", "239");

    setFileName bar, "Bar.java"

    make();
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

  public void testTransitiveGroovyDependency() throws Throwable {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {} ')
    def bar = myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo {}')
    def goo = myFixture.addFileToProject('Goo.groovy', 'class Goo extends Bar {}')
    assertEmpty(make());

    touch(foo.virtualFile)
    touch(goo.virtualFile)
    assertEmpty(make());
  }

  public void testJavaDependsOnGroovyEnum() throws Throwable {
    myFixture.addFileToProject("Foo.groovy", "enum Foo { FOO }")
    myFixture.addClass("class Bar { Foo f; }")
    assertEmpty(make())
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

  @Override
  void runBare() {
    def ideaLog = new File(TestLoggerFactory.testLogDir, "idea.log")
    def makeLog = new File(PathManager.systemPath, "compile-server/server.log")
    if (ideaLog.exists()) {
      FileUtil.delete(ideaLog)
    }
    if (makeLog.exists()) {
      FileUtil.delete(makeLog)
    }

    try {
      super.runBare()
    }
    catch (Throwable e) {
      if (ideaLog.exists()) {
        println "Idea Log:"
        println ideaLog.text
      }
      if (makeLog.exists()) {
        println "Server Log:"
        println makeLog.text
      }
      throw e
    }
    finally {
      System.out.flush()
    }
  }

  public void testMakeInTests() throws Throwable {
    setupTestSources();
    myFixture.addFileToProject("tests/Super.groovy", "class Super {}");
    assertEmpty(make());

    def sub = myFixture.addFileToProject("tests/Sub.groovy", "class Sub {\n" +
      "  Super xxx() {}\n" +
      "  static void main(String[] args) {" +
      "    println 'hello'" +
      "  }" +
      "}");

    def javaFile = myFixture.addFileToProject("tests/Java.java", "public class Java {}");

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
    def foo = myFixture.addFileToProject("Foo.groovy", "class Foo extends Goo { }");
    myFixture.addFileToProject("Goo.groovy", "class Goo extends Main { void bar() { println 'hello' } }");
    def main = myFixture.addClass("public class Main { public static void main(String[] args) { new Goo().bar(); } }");

    assertEmpty(make());
    assertOutput 'Main', 'hello'

    touch(foo.virtualFile)
    touch(main.containingFile.virtualFile)
    assertEmpty(make());

    assertOutput 'Main', 'hello'
  }

  public void testDontApplyTransformsFromSameModule() throws Exception {
    addTransform();

    myFixture.addClass("public class JavaClassToGenerateStubs {}");

    assertEmpty(make());

  }

  private void addTransform() throws IOException {
    myFixture.addFileToProject("Transf.groovy", """
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.control.*
import org.codehaus.groovy.transform.*
@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
public class Transf implements ASTTransformation {
  void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
    ModuleNode module = nodes[0]
    for (clazz in module.classes) {
      if (clazz.name.contains('Bar')) {
        module.addStaticStarImport('Foo', ClassHelper.makeWithoutCaching(Foo.class))
      }
    }
  }
}""");

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

  public void testIndirectDependencies() throws Exception {
    myFixture.addFileToProject("dependent1/Bar1.groovy", "class Bar1 {}");
    myFixture.addFileToProject("dependent2/Bar2.groovy", "class Bar2 extends Bar1 {}");
    PsiFile main = myFixture.addFileToProject("Main.groovy", "class Main extends Bar2 {}");

    Module dep1 = addModule('dependent1')
    Module dep2 = addModule('dependent2')
    ModuleRootModificationUtil.addDependency dep2, dep1
    ModuleRootModificationUtil.addDependency myModule, dep2

    addGroovyLibrary(dep1);
    addGroovyLibrary(dep2);

    assertEmpty(make())

    touch(main.virtualFile)
    assertEmpty(make())
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
    def javaFile = myFixture.addFileToProject("AJava.java", "public class AJava extends Foo.Bar {}")
    assertEmpty make()

    touch(javaFile.virtualFile)
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

  public void testRecompileExpressionReferences() throws Exception {
    def rusCon = myFixture.addFileToProject('RusCon.groovy', '''
interface RusCon {
  Closure foo = { Seq.foo() }
}''')
    myFixture.addFileToProject "Seq.groovy", """
class Seq implements RusCon {
  static def foo() { }
}"""
    assertEmpty make()

    touch(rusCon.virtualFile)
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

  public void testDollarGroovyInnerClassUsagesInStubs() throws Exception {
    def javaFile = myFixture.addClass("""
      public class JavaClass {
        public static class InnerJavaClass {}
      }
""")
    myFixture.addFileToProject("WithInner.groovy", """
class WithInner {
  static class Inner {}
}
""")
    assertEmpty make()

    myFixture.addFileToProject("Usage.groovy", """
class Usage {
  def foo(WithInner.Inner i) {}
  def foo(JavaClass.InnerJavaClass i) {}
}
""")

    touch(javaFile.containingFile.virtualFile)
    assertEmpty make()
  }

  public void testDollarGroovyInnerClassUsagesInStubs2() throws Exception {
    myFixture.addClass(""" public class JavaClass { } """)
    myFixture.addFileToProject("WithInner.groovy", """
class WithInner {
  static class Inner {}
}
""")

    myFixture.addFileToProject("Usage.groovy", """
class Usage {
  def foo(WithInner.Inner i) {}
}
""")

    assertEmpty make()
  }

  public void testGroovyAnnotations() {
    myFixture.addClass 'public @interface Anno { Class[] value(); }'
    myFixture.addFileToProject 'Foo.groovy', '@Anno([String]) class Foo {}'
    myFixture.addFileToProject 'Bar.java', 'class Bar extends Foo {}'

    assertEmpty make()
  }

  public void testGenericStubs() {
    myFixture.addFileToProject 'Foo.groovy', 'class Foo { List<String> list }'
    myFixture.addFileToProject 'Bar.java', 'class Bar {{ for (String s : new Foo().getList()) {} }}'
    assertEmpty make()
  }

  public void testDuplicateClassDuringCompilation() throws Exception {
    def base = myFixture.addFileToProject('p/Base.groovy', 'package p; class Base { }').virtualFile
    myFixture.addFileToProject('p/Indirect.groovy', '''package p
class Indirect {
  private static class Inner { Base b }

  private Indirect.Inner foo(Indirect.Inner g1, Inner g2, Base b) {}
 }''').virtualFile
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo { p.Indirect foo() {} }').virtualFile
    assertEmpty make()

    touch(foo)
    touch(base)
    assertEmpty make()
  }

  public void testDontRecompileUnneeded() {
    myFixture.addFileToProject('Base.groovy', 'class Base { }')
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo extends Base { }').virtualFile
    myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo { }')
    def main = myFixture.addFileToProject('Main.groovy', 'class Main extends Bar { }').virtualFile
    assertEmpty make()
    long oldBaseStamp = findClassFile("Base").modificationStamp
    long oldMainStamp = findClassFile("Main").modificationStamp

    touch(main)
    touch(foo)
    assertEmpty make()
    assert oldMainStamp != findClassFile("Main").modificationStamp
    assert oldBaseStamp == findClassFile("Base").modificationStamp
  }

  public void testPartialCrossRecompile() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    def java = myFixture.addFileToProject('Java.java', 'class Java { void foo(Used used) {} }')
    def main = myFixture.addFileToProject('Main.groovy', 'class Main extends Java {  }').virtualFile

    assertEmpty compileModule(myModule)
    if (!useJps()) {
      assertEmpty compileFiles(used.virtualFile, main)
    }

    touch(used.virtualFile)
    touch(main)
    assertEmpty make()

    assertEmpty compileModule(myModule)
    assertEmpty compileModule(myModule)

    setFileText(used, 'class Used2 {}')
    shouldFail { make() }
    assert findClassFile('Used') == null

    setFileText(used, 'class Used3 {}')
    setFileText(java, 'class Java { void foo(Used3 used) {} }')
    assertEmpty make()

    assert findClassFile('Used2') == null
  }

  public void testClassLoadingDuringBytecodeGeneration() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    def java = myFixture.addFileToProject('Java.java', '''
abstract class Java {
  Object getProp() { return null; }
  abstract void foo(Used used);
}''')
    def main = myFixture.addFileToProject('Main.groovy', '''
class Main {
  def foo(Java j) {
    return j.prop
  }
}''').virtualFile

    assertEmpty make()

    touch(used.virtualFile)
    touch(main)
    assertEmpty make()
  }

  public void testMakeInDependentModuleAfterChunkRebuild() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    def java = myFixture.addFileToProject('Java.java', 'class Java { void foo(Used used) {} }')
    def main = myFixture.addFileToProject('Main.groovy', 'class Main extends Java {  }').virtualFile

    addGroovyLibrary(addDependentModule())

    def dep = myFixture.addFileToProject("dependent/Dep.java", "class Dep { }")

    assertEmpty make()

    setFileText(used, 'class Used { String prop }')
    touch(main)
    setFileText(dep, 'class Dep { String prop = new Used().getProp(); }')

    assertEmpty make()
  }

  public void "test module cycle"() {
    def dep = addDependentModule()
    ModuleRootModificationUtil.addDependency(myModule, dep)
    addGroovyLibrary(dep)

    myFixture.addFileToProject('Foo.groovy', 'class Foo extends Bar { static void main(String[] args) { println "Hello from Foo" } }')
    myFixture.addFileToProject('FooX.java', 'class FooX extends Bar { }')
    myFixture.addFileToProject("dependent/Bar.groovy", "class Bar { Foo f; static void main(String[] args) { println 'Hello from Bar' } }")
    myFixture.addFileToProject("dependent/BarX.java", "class BarX { Foo f; }")

    def checkClassFiles = {
      assert findClassFile('Foo', myModule)
      assert findClassFile('FooX', myModule)
      assert findClassFile('Bar', dep)
      assert findClassFile('BarX', dep)

      assert !findClassFile('Bar', myModule)
      assert !findClassFile('BarX', myModule)
      assert !findClassFile('Foo', dep)
      assert !findClassFile('FooX', dep)
    }

    assertEmpty(make())
    checkClassFiles()

    assertEmpty(make())
    checkClassFiles()

    assertOutput('Foo', 'Hello from Foo', myModule)
    assertOutput('Bar', 'Hello from Bar', dep)

    checkClassFiles()
  }

  public void testCompileTimeConstants() {
    myFixture.addFileToProject 'Gr.groovy', '''
interface Gr {
  String HELLO = "Hello"
  int MAGIC = 239
  Boolean BOOL = true
  boolean bool = true
}'''
    myFixture.addFileToProject 'Main.java', '''
public class Main {
  public static void main(String[] args) {
    System.out.println(Gr.HELLO + ", " + Gr.BOOL + Gr.bool + Gr.MAGIC);
  }
}
'''
    make()
    assertOutput 'Main', 'Hello, truetrue239'
  }

  public void "test reporting rebuild errors caused by missing files excluded from compilation"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {}')
    myFixture.addFileToProject 'Bar.groovy', 'class Bar extends Foo {}'

    make()

    excludeFromCompilation(foo)

    shouldFail { rebuild() }
  }

  private void excludeFromCompilation(PsiFile foo) {
    final ExcludedEntriesConfiguration configuration =
      ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).getExcludedEntriesConfiguration()
    configuration.addExcludeEntryDescription(new ExcludeEntryDescription(foo.virtualFile, false, true, testRootDisposable))
  }

  public void "test make stub-level error and correct it"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo { }')
    myFixture.addFileToProject('Bar.java', 'class Bar extends Foo {}')

    assertEmpty make()

    setFileText(foo, 'class Foo implements Runnabl {}')

    shouldFail { make() }

    setFileText(foo, 'class Foo {}')

    assertEmpty make()
  }

  //todo jeka: when recompiling module, delete all class files including those with excluded source
  public void "_test reporting module compile errors caused by missing files excluded from compilation"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {}')
    myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo {}')

    make()

    excludeFromCompilation(foo)

    shouldFail { compileModule(myModule) }
  }

  public void "test stubs generated while processing groovy class file dependencies"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo { }')
    def bar = myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo { }')
    def client = myFixture.addFileToProject('Client.groovy', 'class Client { Bar bar = new Bar() }')
    def java = myFixture.addFileToProject('Java.java', 'class Java extends Client { String getName(Bar bar) { return bar.toString();  } }')

    assertEmpty(make())

    setFileText(bar, 'class Bar { }')

    assertEmpty(make())
    assert findClassFile("Client")
  }

  public void "test ignore groovy internal non-existent interface helper inner class"() {
    myFixture.addFileToProject 'Foo.groovy', '''
interface Foo {}

class Zoo {
  Foo foo() {}
  static class Inner implements Foo {}
}

'''
    def bar = myFixture.addFileToProject('Bar.groovy', 'class Bar { def foo = new Zoo.Inner() {}  }')

    assertEmpty make()
    assertEmpty compileFiles(bar.virtualFile)
  }

  public void "test multiline strings"() {
    myFixture.addFileToProject 'Foo.groovy', '''class Foo {
  public static final String s = """
multi
line
string
"""
 } '''
    myFixture.addFileToProject 'Bar.java', 'class Bar extends Foo {} '

    assertEmpty make()
  }

  public static class IdeaModeTest extends GroovyCompilerTest {
    @Override protected boolean useJps() { false }
  }

  public static class JpsModeTest extends GroovyCompilerTest {
    @Override protected boolean useJps() { true }

    @Override
    protected void tearDown() {
      File systemRoot = BuildManager.getInstance().getBuildSystemDirectory()
      try {
        super.tearDown()
      }
      finally {
        FileUtil.delete(systemRoot);
      }
    }
  }
}
