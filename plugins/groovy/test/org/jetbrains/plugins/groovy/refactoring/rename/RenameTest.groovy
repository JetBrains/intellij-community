// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename


import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.refactoring.rename.inplace.GrVariableInplaceRenameHandler
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.Ignore
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

@CompileStatic
class RenameTest extends GroovyLatestTest implements BaseTest {

  RenameTest() {
    super('groovy/refactoring/rename/')
  }

  @Test
  void closureIt() { doTest() }

  @Test
  void to_getter() { doTest() }

  @Test
  void to_prop() { doTest() }

  @Test
  void to_setter() { doTest() }

  @Test
  void scriptMethod() { doTest() }

  @Test
  void parameterIsNotAUsageOfGroovyParameter() throws Exception {
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
def foo(f) {
  // Parameter
  println 'Parameter' // also
  return <caret>f
}
""")
    def txt = "Just the Parameter word, which shouldn't be renamed"
    def txtFile = fixture.addFileToProject("a.txt", txt)

    def parameter = fixture.file.findReferenceAt(fixture.editor.caretModel.offset).resolve()
    fixture.renameElement(parameter, "newName", true, true)
    fixture.checkResult """
def foo(newName) {
  // Parameter
  println 'Parameter' // also
  return <caret>newName
}
"""
    assertEquals txt, txtFile.text
  }

  @Test
  void preserveUnknownImports() throws Exception {
    def someClass = fixture.addClass("public class SomeClass {}")

    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
import foo.bar.Zoo
SomeClass c = new SomeClass()
Zoo zoo
""")
    fixture.renameElement(someClass, "NewClass")
    fixture.checkResult """
import foo.bar.Zoo
NewClass c = new NewClass()
Zoo zoo
"""
  }

  @Test
  void renameGetter() throws Exception {
    fixture.addFileToProject("Foo.groovy", "class Foo { def getFoo(){return 2}}")
    final PsiClass clazz = fixture.findClass("Foo")
    def methods = clazz.findMethodsByName("getFoo", false)
    fixture.configureByText("a.groovy", "print new Foo().foo")
    fixture.renameElement methods[0], "get"
    fixture.checkResult "print new Foo().get()"
  }

  @Test
  void renameSetter() throws Exception {
    fixture.addFileToProject("Foo.groovy", "class Foo { def setFoo(def foo){}}")
    def clazz = fixture.findClass("Foo")
    def methods = clazz.findMethodsByName("setFoo", false)
    fixture.configureByText("a.groovy", "print new Foo().foo = 2")
    fixture.renameElement methods[0], "set"
    fixture.checkResult "print new Foo().set(2)"
  }

  @Test
  void property() {
    fixture.configureByText("a.groovy", """
class Foo {
  def p<caret>rop

  def foo() {
    print prop

    print getProp()

    setProp(2)
  }
}""")
    PsiElement field = fixture.elementAtCaret
    fixture.renameElement field, "newName"

    fixture.checkResult """
class Foo {
  def newName

  def foo() {
    print newName

    print getNewName()

    setNewName(2)
  }
}"""
  }

  @Test
  void propertyWithLocalCollision() {
    fixture.configureByText("a.groovy", """
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
    PsiElement field = fixture.elementAtCaret
    fixture.renameElement field, "newName"

    fixture.checkResult """
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

  @Test
  void propertyWithFieldCollision() {
    fixture.configureByText("a.groovy", """\
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
    PsiElement field = fixture.elementAtCaret
    fixture.renameElement field, "ndame"

    fixture.checkResult """\
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

  @Test
  void renameFieldWithNonstandardName() {
    def file = fixture.configureByText("a.groovy", """
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
    final PsiClass clazz = fixture.findClass('SomeBean')
    fixture.renameElement new PropertyForRename([clazz.findFieldByName('xXx', false), clazz.findMethodsByName('getxXx', false)[0]], 'xXx',
                                                PsiManager.getInstance(project)), "xXx777"
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

  @Test
  void renameClassWithConstructorWithOptionalParams() {
    fixture.configureByText('a.groovy', '''\
class Test {
  def Test(def abc = null){}
}

print new Test()
print new Test(1)
''')
    def clazz = fixture.findClass('Test')
    fixture.renameElement clazz, 'Foo'
    fixture.checkResult '''\
class Foo {
  def Foo(def abc = null){}
}

print new Foo()
print new Foo(1)
'''
  }

  void doTest() {
    final String testFile = testName.replace('$', '/') + ".test"
    final List<String> list = TestUtils.readInput(TestUtils.absoluteTestDataPath + "groovy/refactoring/rename/" + testFile)

    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, list.get(0))

    PsiReference ref = fixture.file.findReferenceAt(fixture.editor.caretModel.offset)
    final PsiElement resolved = ref == null ? null : ref.resolve()
    if (resolved instanceof PsiMethod && !(resolved instanceof GrAccessorMethod)) {
      PsiMethod method = (PsiMethod)resolved
      String name = method.name
      String newName = createNewNameForMethod(name)
      fixture.renameElementAtCaret(newName)
    }
    else if (resolved instanceof GrAccessorMethod) {
      GrField field = ((GrAccessorMethod)resolved).property
      RenameProcessor processor = new RenameProcessor(fixture.project, field, "newName", true, true)
      processor.addElement(resolved, createNewNameForMethod(((GrAccessorMethod)resolved).name))
      processor.run()
    }
    else {
      fixture.renameElementAtCaret("newName")
    }
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    fixture.checkResult(list.get(1))
  }

  private static String createNewNameForMethod(final String name) {
    String newName = "newName"
    if (name.startsWith("get")) {
      newName = "get" + StringUtil.capitalize(newName)
    }
    else if (name.startsWith("is")) {
      newName = "is" + StringUtil.capitalize(newName)
    }
    else if (name.startsWith("set")) {
      newName = "set" + StringUtil.capitalize(newName)
    }
    return newName
  }

  @Ignore("The fix requires major changes in property resolution and platform renamer")
  @Test
  void recursivePathRename() {
    def file = fixture.configureByText("SomeBean.groovy", """
class SomeBean {

  SomeBean someBean<caret>

  static {
    new SomeBean().someBean.someBean.someBean.someBean.toString()
  }
}
""")
    fixture.renameElementAtCaret "b"

    assertEquals """
class SomeBean {

  SomeBean b

  static {
    new SomeBean().b.b.b.b.toString()
  }
}
""", file.text
  }

  @Test
  void dontAutoRenameDynamicallyTypeUsage() throws Exception {
    fixture.configureByText "a.groovy", """
class Goo {
  def pp<caret>roject() {}
}

new Goo().pproject()

def foo(p) {
  p.pproject()
}
"""
    def method = PsiTreeUtil.findElementOfClassAtOffset(fixture.file, fixture.editor.caretModel.offset, GrMethod.class, false)
    def usages = RenameUtil.findUsages(method, "project", false, false, [(method): "project"])
    assert (usages[0].isNonCodeUsage ? 1 : 0) + (usages[1].isNonCodeUsage ? 1 : 0) == 1
  }

  @Test
  void renameAliasImportedProperty() {
    fixture.addFileToProject("Foo.groovy", """class Foo {
static def bar
}""")
    fixture.configureByText("a.groovy", """
import static Foo.ba<caret>r as foo

print foo
print getFoo()
setFoo(2)
foo = 4""")

    fixture.renameElement fixture.findClass("Foo").fields[0], "newName"
    fixture.checkResult """
import static Foo.newName as foo

print foo
print getFoo()
setFoo(2)
foo = 4"""
  }

  @Test
  void renameAliasImportedClass() {
    fixture.addFileToProject("Foo.groovy", """class Foo {
static def bar
}""")
    fixture.configureByText("a.groovy", """
import Foo as Bar
Bar bar = new Bar()
""")

    fixture.renameElement fixture.findClass("Foo"), "F"
    fixture.checkResult """
import F as Bar
Bar bar = new Bar()
"""
  }

  @Test
  void renameAliasImportedMethod() {
    fixture.addFileToProject("Foo.groovy", """class Foo {
static def bar(){}
}""")
    fixture.configureByText("a.groovy", """
import static Foo.bar as foo
foo()
""")

    fixture.renameElement fixture.findClass("Foo").findMethodsByName("bar", false)[0], "b"
    fixture.checkResult """
import static Foo.b as foo
foo()
"""
  }

  @Test
  void renameAliasImportedField() {
    fixture.addFileToProject("Foo.groovy", """class Foo {
public static bar
}""")
    fixture.configureByText("a.groovy", """
import static Foo.ba<caret>r as foo

print foo
foo = 4""")

    fixture.renameElement fixture.findClass("Foo").fields[0], "newName"
    fixture.checkResult """
import static Foo.newName as foo

print foo
foo = 4"""
  }

  @Test
  void inplaceRename() {
    doInplaceRenameTest()
  }

  @Test
  void inplaceRenameWithGetter() {
    doInplaceRenameTest()
  }

  @Test
  void inplaceRenameWithStaticField() {
    doInplaceRenameTest()
  }

  @Test
  void inplaceRenameOfClosureImplicitParameter() {
    doInplaceRenameTest()
  }

  @Test
  void renameClassWithLiteralUsages() throws Exception {
    def file = fixture.addFileToProject("aaa.groovy", """
      class Foo {
        Foo(int a) {}
      }
      def x = [2] as Foo
      def y  = ['super':2] as Foo
""")
    fixture.configureFromExistingVirtualFile file.virtualFile

    fixture.renameElement fixture.findClass("Foo"), "Bar"
    fixture.checkResult """
      class Bar {
        Bar(int a) {}
      }
      def x = [2] as Bar
      def y  = ['super':2] as Bar
"""
  }

  @Test
  void extensionOnClassRename() {
    fixture.configureByText "Foo.gy", "class Foo {}"
    fixture.renameElement fixture.findClass("Foo"), "Bar"
    assert "gy", fixture.file.virtualFile.extension
  }

  @Test
  void renameJavaUsageFail() {
    fixture.addFileToProject "Bar.java", """
class Bar {
  void bar() {
    new Foo().foo();
  }
}"""
    fixture.configureByText "Foo.groovy", """
class Foo {
  def foo() {}
}"""
    try {
      fixture.renameElement fixture.findClass("Foo").methods[0], "'newName'"
    }
    catch (ConflictsInTestsException e) {
      assertEquals "<b><code>'newName'</code></b> is not a correct identifier to use in <b><code>new Foo().foo</code></b>", e.message
      return
    }
    assertTrue false
  }

  @Test
  void renameJavaPrivateField() {
    fixture.addFileToProject "Foo.java", """
public class Foo {
  private int field;
}"""
    fixture.configureByText "Bar.groovy", """
print new Foo(field: 2)
"""
    fixture.renameElement fixture.findClass("Foo").fields[0], "anotherOneName"

    fixture.checkResult """
print new Foo(anotherOneName: 2)
"""
  }

  @Test
  void renameProp() {
    fixture.configureByText("Foo.groovy", """
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
    new PropertyRenameHandler().invoke(project, [fixture.findClass('Book').fields[0]] as PsiElement[], null)
    fixture.checkResult """
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
    String prefix = "/${testName.capitalize()}"
    fixture.configureByFile prefix + ".groovy"
    CommandProcessor.instance.executeCommand(
      project,
      { CodeInsightTestUtil.doInlineRename(new GrVariableInplaceRenameHandler(), "foo", fixture) },
      "Rename", null)
    fixture.checkResultByFile prefix + "_after.groovy"
  }

  @Test
  void renameJavaGetter() {
    fixture.configureByText('J.java', '''
class J {
  int ge<caret>tFoo() {return 2;}
}
''')

    PsiFile groovyFile = fixture.addFileToProject('g.groovy', '''print new J().foo''')

    fixture.renameElementAtCaret('getAbc')
    assertEquals('''print new J().abc''', groovyFile.text)
  }

  @Test
  void methodWithSpacesRename() {
    def file = fixture.configureByText('_A.groovy', '''\
class X {
  def foo(){}
}

new X().foo()
''') as GroovyFile

    def method = (file.classes[0] as GrTypeDefinition).codeMethods[0]

    fixture.renameElement(method, 'f oo')

    fixture.checkResult('''\
class X {
  def 'f oo'(){}
}

new X().'f oo'()
''')
  }

  @Test
  void methodWithSpacesRenameInJava() {
    def file = fixture.addFileToProject('_A.groovy', '''\
class X {
  def foo(){}
}

new X().foo()
''') as GroovyFile

    def method = (file.classes[0] as GrTypeDefinition).codeMethods[0]

    fixture.configureByText('Java.java', '''\
class Java {
  void ab() {
    new X().foo()
  }
}''')

    try {
      fixture.renameElement(method, 'f oo')
      assert false
    }
    catch (ConflictsInTestsException ignored) {
      assert true
    }
  }

  @Test
  void tupleConstructor() {
    fixture.with {
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

  @Test
  void constructor() {
    fixture.with {
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

  @Test
  void stringNameForMethod() {
    fixture.with {
      configureByText(GroovyFileType.GROOVY_FILE_TYPE, 'def fo<caret>o() {}')
      renameElementAtCaret('import')
      checkResult("def 'import'() {}")
    }
  }

  @Test
  void constructorAndSuper() {
    fixture.with {
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

  @Test
  void overridenMethodWithOptionalParams() {
    fixture.with {
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

  @Test
  void renameScriptFile() {
    fixture.with {
      final PsiFile file = configureByText('Abc.groovy', '''\
print new Abc()
''')
      renameElement(file, 'Abcd.groovy')
      checkResult('''\
print new Abcd()
''')
    }
  }

  @Test
  void traitField() {
    fixture.with {
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

  @Test
  void 'rename reflected method with overloads'() {
    fixture.with {
      configureByText '_.groovy', '''\
class A {
  def fo<caret>o(a, b, c = 1) {}
  def foo(d = 2) {}
}
'''
      renameElementAtCaretUsingHandler 'foo1'
      checkResult '''\
class A {
  def foo1(a, b, c = 1) {}
  def foo1(d = 2) {}
}
'''
    }
  }

  @Test
  void 'import collision in Java after class rename'() {
    def usage = fixture.addFileToProject 'Usage.java', '''
import java.util.*;

class C implements List, p.MyList {}
'''
    fixture.addFileToProject "p/intentionallyNonClassName.groovy", "package p; class MyList {}"
    fixture.renameElement(fixture.findClass('p.MyList'), 'List')
    assert usage.text == '''
import java.util.*;

class C implements List, p.List {}
'''
  }
}
