/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.refactoring.rename.inplace.GrVariableInplaceRenameHandler
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ven
 */
public class RenameTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    TestUtils.testDataPath + 'groovy/refactoring/rename/'
  }

  public void testClosureIt() throws Throwable { doTest(); }
  public void testTo_getter() throws Throwable { doTest(); }
  public void testTo_prop() throws Throwable { doTest(); }
  public void testTo_setter() throws Throwable { doTest(); }
  public void testScriptMethod() throws Throwable { doTest(); }

  public void testParameterIsNotAUsageOfGroovyParameter() throws Exception {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
def foo(f) {
  // Parameter
  println 'Parameter' // also
  return <caret>f
}
""")
    def txt = "Just the Parameter word, which shouldn't be renamed"
    def txtFile = myFixture.addFileToProject("a.txt", txt)

    def parameter = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset).resolve()
    myFixture.renameElement(parameter, "newName", true, true)
    myFixture.checkResult """
def foo(newName) {
  // Parameter
  println 'Parameter' // also
  return <caret>newName
}
"""
    assertEquals txt, txtFile.text
  }

  public void testPreserveUnknownImports() throws Exception {
    def someClass = myFixture.addClass("public class SomeClass {}")

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
import foo.bar.Zoo
SomeClass c = new SomeClass()
Zoo zoo
""")
    myFixture.renameElement(someClass, "NewClass")
    myFixture.checkResult """
import foo.bar.Zoo
NewClass c = new NewClass()
Zoo zoo
"""
  }

  public void testRenameGetter() throws Exception {
    myFixture.addFileToProject("Foo.groovy", "class Foo { def getFoo(){return 2}}")
    final PsiClass clazz = myFixture.findClass("Foo")
    def methods = clazz.findMethodsByName("getFoo", false)
    myFixture.configureByText("a.groovy", "print new Foo().foo")
    myFixture.renameElement methods[0], "get"
    myFixture.checkResult "print new Foo().get()"
  }

  public void testRenameSetter() throws Exception {
    myFixture.addFileToProject("Foo.groovy","class Foo { def setFoo(def foo){}}")
    def clazz = myFixture.findClass("Foo")
    def methods = clazz.findMethodsByName("setFoo", false)
    myFixture.configureByText("a.groovy", "print new Foo().foo = 2")
    myFixture.renameElement methods[0], "set"
    myFixture.checkResult "print new Foo().set(2)"
  }

  public void testProperty() {
    myFixture.configureByText("a.groovy", """
class Foo {
  def p<caret>rop

  def foo() {
    print prop

    print getProp()

    setProp(2)
  }
}""")
    PsiElement field = myFixture.elementAtCaret
    myFixture.renameElement field, "newName"

    myFixture.checkResult """
class Foo {
  def newName

  def foo() {
    print newName

    print getNewName()

    setNewName(2)
  }
}"""
  }

  public void testPropertyWithLocalCollision() {
    myFixture.configureByText("a.groovy", """
class Foo {
  def p<caret>rop

  def foo() {
    def newName = a;
    print prop

    print getProp()

    setProp(2)

    print newName
  }
}""")
    PsiElement field = myFixture.elementAtCaret
    myFixture.renameElement field, "newName"

    myFixture.checkResult """
class Foo {
  def newName

  def foo() {
    def newName = a;
    print this.newName

    print getNewName()

    setNewName(2)

    print newName
  }
}"""
  }

  public void testPropertyWithFieldCollision() {
    myFixture.configureByText("a.groovy", """\
class A {
  String na<caret>me;

  class X {

    String ndame;
    void foo() {
        print name

        print getName()

        setName("a")
    }
  }
}""")
    PsiElement field = myFixture.elementAtCaret
    myFixture.renameElement field, "ndame"

    myFixture.checkResult """\
class A {
  String ndame;

  class X {

    String ndame;
    void foo() {
        print A.this.ndame

        print A.this.getNdame()

        A.this.setNdame("a")
    }
  }
}"""
  }

  public void testRenameFieldWithNonstandardName() {
    def file = myFixture.configureByText("a.groovy", """
class SomeBean {
  String xXx<caret> = "field"
  public String getxXx() {
    return "method"
  }
  public static void main(String[] args) {
    println(new SomeBean().xXx)
    println(new SomeBean().getxXx())
  }
}
""")
    final PsiClass clazz = myFixture.findClass('SomeBean')
    myFixture.renameElement new PropertyForRename([clazz.findFieldByName('xXx', false), clazz.findMethodsByName('getxXx', false)[0]], 'xXx', PsiManager.getInstance(project)), "xXx777"
    assertEquals """
class SomeBean {
  String xXx777 = "field"
  public String getxXx777() {
    return "method"
  }
  public static void main(String[] args) {
    println(new SomeBean().xXx777)
    println(new SomeBean().getxXx777())
  }
}
""", file.text
  }
  
  void testRenameClassWithConstructorWithOptionalParams() {
    myFixture.configureByText('a.groovy', '''\
class Test {
  def Test(def abc = null){}
}

print new Test()
print new Test(1)
''')
    def clazz = myFixture.findClass('Test')
    myFixture.renameElement clazz, 'Foo'
    myFixture.checkResult '''\
class Foo {
  def Foo(def abc = null){}
}

print new Foo()
print new Foo(1)
'''
  }

  public void doTest() {
    final String testFile = getTestName(true).replace('$', '/') + ".test";
    final List<String> list = TestUtils.readInput(TestUtils.absoluteTestDataPath + "groovy/refactoring/rename/" + testFile);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, list.get(0));

    PsiReference ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset);
    final PsiElement resolved = ref == null ? null : ref.resolve();
    if (resolved instanceof PsiMethod && !(resolved instanceof GrAccessorMethod)) {
      PsiMethod method = (PsiMethod)resolved;
      String name = method.name;
      String newName = createNewNameForMethod(name);
      myFixture.renameElementAtCaret(newName);
    } else if (resolved instanceof GrAccessorMethod) {
      GrField field = ((GrAccessorMethod)resolved).property;
      RenameProcessor processor = new RenameProcessor(myFixture.project, field, "newName", true, true);
      processor.addElement(resolved, createNewNameForMethod(((GrAccessorMethod)resolved).name));
      processor.run();
    } else {
      myFixture.renameElementAtCaret("newName");
    }
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
    myFixture.checkResult(list.get(1));
  }

  private static String createNewNameForMethod(final String name) {
    String newName = "newName";
    if (name.startsWith("get")) {
      newName = "get" + StringUtil.capitalize(newName);
    }
    else if (name.startsWith("is")) {
      newName = "is" + StringUtil.capitalize(newName);
    }
    else if (name.startsWith("set")) {
      newName = "set" + StringUtil.capitalize(newName);
    }
    return newName;
  }

  public void testRecursivePathRename() {
    def file = myFixture.configureByText("SomeBean.groovy", """
class SomeBean {

  SomeBean someBean<caret>

  static {
    new SomeBean().someBean.someBean.someBean.someBean.toString()
  }
}
""")
    myFixture.renameElementAtCaret "b"

    assertEquals """
class SomeBean {

  SomeBean b

  static {
    new SomeBean().b.b.b.b.toString()
  }
}
""", file.text
  }

  public void testDontAutoRenameDynamicallyTypeUsage() throws Exception {
    myFixture.configureByText "a.groovy", """
class Goo {
  def pp<caret>roject() {}
}

new Goo().pproject()

def foo(p) {
  p.pproject()
}
"""
    def method = PsiTreeUtil.findElementOfClassAtOffset(myFixture.file, myFixture.editor.caretModel.offset, GrMethod.class, false)
    def usages = RenameUtil.findUsages(method, "project", false, false, [(method):"project"])
    assert !usages[0].isNonCodeUsage
    assert usages[1].isNonCodeUsage
  }

  public void testRenameAliasImportedProperty() {
    myFixture.addFileToProject("Foo.groovy", """class Foo {
static def bar
}""")
    myFixture.configureByText("a.groovy", """
import static Foo.ba<caret>r as foo

print foo
print getFoo()
setFoo(2)
foo = 4""")

    myFixture.renameElement myFixture.findClass("Foo").fields[0], "newName"
    myFixture.checkResult """
import static Foo.newName as foo

print foo
print getFoo()
setFoo(2)
foo = 4"""
  }

  public void testRenameAliasImportedClass() {
    myFixture.addFileToProject("Foo.groovy", """class Foo {
static def bar
}""")
    myFixture.configureByText("a.groovy", """
import Foo as Bar
Bar bar = new Bar()
""")

    myFixture.renameElement myFixture.findClass("Foo"), "F"
    myFixture.checkResult """
import F as Bar
Bar bar = new Bar()
"""
  }

  public void testRenameAliasImportedMethod() {
    myFixture.addFileToProject("Foo.groovy", """class Foo {
static def bar(){}
}""")
    myFixture.configureByText("a.groovy", """
import static Foo.bar as foo
foo()
""")

    myFixture.renameElement myFixture.findClass("Foo").findMethodsByName("bar", false)[0], "b"
    myFixture.checkResult """
import static Foo.b as foo
foo()
"""
  }

  public void testRenameAliasImportedField() {
    myFixture.addFileToProject("Foo.groovy", """class Foo {
public static bar
}""")
    myFixture.configureByText("a.groovy", """
import static Foo.ba<caret>r as foo

print foo
foo = 4""")

    myFixture.renameElement myFixture.findClass("Foo").fields[0], "newName"
    myFixture.checkResult """
import static Foo.newName as foo

print foo
foo = 4"""
  }

  public void testInplaceRename() {
   doInplaceRenameTest();
  }

  public void testInplaceRenameWithGetter() {
   doInplaceRenameTest();
  }

  public void testInplaceRenameWithStaticField() {
   doInplaceRenameTest();
  }

  void testInplaceRenameOfClosureImplicitParameter(){
    doInplaceRenameTest()
  }

  public void testRenameClassWithLiteralUsages() throws Exception {
    def file = myFixture.addFileToProject("aaa.groovy", """
      class Foo {
        Foo(int a) {}
      }
      def x = [2] as Foo
      def y  = ['super':2] as Foo
""")
    myFixture.configureFromExistingVirtualFile file.virtualFile

    myFixture.renameElement myFixture.findClass("Foo"), "Bar"
    myFixture.checkResult """
      class Bar {
        Bar(int a) {}
      }
      def x = [2] as Bar
      def y  = ['super':2] as Bar
"""
  }

  public void testExtensionOnClassRename() {
    myFixture.configureByText "Foo.gy", "class Foo {}"
    myFixture.renameElement myFixture.findClass("Foo"), "Bar"
    assert "gy", myFixture.file.virtualFile.extension
  }

  public void testRenameJavaUsageFail() {
    myFixture.addFileToProject "Bar.java", """
class Bar {
  void bar() {
    new Foo().foo();
  }
}"""
    myFixture.configureByText "Foo.groovy", """
class Foo {
  def foo() {}
}"""
    try {
      myFixture.renameElement myFixture.findClass("Foo").methods[0], "'newName'"
    } catch (ConflictsInTestsException e) {
      assertEquals "<b><code>'newName'</code></b> is not a correct identifier to use in <b><code>new Foo().foo</code></b>", e.message
      return;
    }
    assertTrue false
  }

  public void testRenameJavaPrivateField() {
    myFixture.addFileToProject "Foo.java", """
public class Foo {
  private int field;
}"""
    myFixture.configureByText "Bar.groovy", """
print new Foo(field: 2)
"""
    myFixture.renameElement myFixture.findClass("Foo").fields[0], "anotherOneName"

    myFixture.checkResult """
print new Foo(anotherOneName: 2)
"""
  }

  void testRenameProp() {
    myFixture.configureByText("Foo.groovy", """
class Book {
    String title
}

class Test {
 def testBook(){
      def book = new Book()

      book.with {
          title = 'Test'
      }
  }
}""")
    new PropertyRenameHandler().invoke(project, [myFixture.findClass('Book').fields[0]] as PsiElement[], null);
    myFixture.checkResult """
class Book {
    String s
}

class Test {
 def testBook(){
      def book = new Book()

      book.with {
          s = 'Test'
      }
  }
}"""
  }


  private def doInplaceRenameTest() {
    String prefix = "/${getTestName(false)}"
    myFixture.configureByFile prefix + ".groovy";
    WriteCommandAction.runWriteCommandAction project, {
      CodeInsightTestUtil.doInlineRename(new GrVariableInplaceRenameHandler(), "foo", myFixture);
    }
    myFixture.checkResultByFile prefix + "_after.groovy"
  }

  void testRenameJavaGetter() {
    myFixture.configureByText('J.java', '''
class J {
  int ge<caret>tFoo() {return 2;}
}
''')

    PsiFile groovyFile = myFixture.addFileToProject('g.groovy', '''print new J().foo''')

    myFixture.renameElementAtCaret('getAbc')
    assertEquals('''print new J().abc''', groovyFile.text)
  }

  void testMethodWithSpacesRename() {
    def file = myFixture.configureByText('_A.groovy', '''\
class X {
  def foo(){}
}

new X().foo()
''') as GroovyFile

    def method = (file.classes[0] as GrTypeDefinition).codeMethods[0]

    myFixture.renameElement(method, 'f oo');

    myFixture.checkResult('''\
class X {
  def 'f oo'(){}
}

new X().'f oo'()
''')
  }

  void testMethodWithSpacesRenameInJava() {
    def file = myFixture.addFileToProject('_A.groovy', '''\
class X {
  def foo(){}
}

new X().foo()
''') as GroovyFile

    def method = (file.classes[0] as GrTypeDefinition).codeMethods[0]

    myFixture.configureByText('Java.java', '''\
class Java {
  void ab() {
    new X().foo()
  }
}''')

    try {
      myFixture.renameElement(method, 'f oo');
      assert false
    }
    catch (ConflictsInTestsException ignored) {
      assert true
    }

  }

  void testTupleConstructor() {
    myFixture.with {
      configureByText('a.groovy', '''\
import groovy.transform.TupleConstructor

@TupleConstructor
class X<caret>x {}
''')

    renameElementAtCaret('Yy')
    checkResult("""\
import groovy.transform.TupleConstructor

@TupleConstructor
class Y<caret>y {}
""")
    }
  }

  void testConstructor() {
    myFixture.with {
      configureByText('a.groovy', '''\
class Foo {
  def Fo<caret>o() {}
}
''')
      renameElementAtCaret('Bar')
      checkResult('''\
class Bar {
  def Ba<caret>r() {}
}
''')
    }
  }

  void testStringNameForMethod() {
    myFixture.with {
      configureByText(GroovyFileType.GROOVY_FILE_TYPE, 'def fo<caret>o() {}')
      renameElementAtCaret('import')
      checkResult("def 'import'() {}")
    }
  }

  void testConstructorAndSuper() {
    myFixture.with {
      configureByText(GroovyFileType.GROOVY_FILE_TYPE, '''\
class B<caret>ase {
  def Base() {}
}
class Inheritor extends Base {
  def Inheritor() {
    super()
  }
}
''')
      renameElementAtCaret('Bassse')
      checkResult('''\
class Bassse {
  def Bassse() {}
}
class Inheritor extends Bassse {
  def Inheritor() {
    super()
  }
}
''')
    }
  }

  void testOverridenMethodWithOptionalParams() {
    myFixture.with {
      configureByText(GroovyFileType.GROOVY_FILE_TYPE, '''\
class Base {
  void fo<caret>o(){}
}

class Inheritor extends Base {
  void foo(int x = 5) {}
}

new Base().foo()
new Inheritor().foo()
new Inheritor().foo(2)
''')
      renameElementAtCaret('bar')
      checkResult('''\
class Base {
  void ba<caret>r(){}
}

class Inheritor extends Base {
  void bar(int x = 5) {}
}

new Base().bar()
new Inheritor().bar()
new Inheritor().bar(2)
''')
    }
  }

  void testRenameScriptFile() {
    myFixture.with {
      final PsiFile file = configureByText('Abc.groovy', '''\
print new Abc()
''')
      renameElement(file, 'Abcd.groovy')
      checkResult('''\
print new Abcd()
''')
    }
  }

  void testTraitField() {
    myFixture.with {
      configureByText('a.groovy', '''\
trait T {
    public int f<caret>oo = 5

    def bar() {
        print foo
    }
}

class X implements T {
   def bar() {
      print T__foo
   }
}

trait T2 extends T {
    def bar() {
        print T__foo
    }
}
''')
      renameElementAtCaret("baz")
      checkResult('''\
trait T {
    public int baz = 5

    def bar() {
        print baz
    }
}

class X implements T {
   def bar() {
      print T__baz
   }
}

trait T2 extends T {
    def bar() {
        print T__baz
    }
}
''')
    }
  }
}
