package org.jetbrains.plugins.groovy.refactoring.rename;


import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.TestUtils
import com.intellij.psi.*

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
""")
    myFixture.renameElement(someClass, "NewClass")
    myFixture.checkResult """
import foo.bar.Zoo
NewClass c = new NewClass()
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
    PsiElement field = myFixture.getElementAtCaret()
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
    PsiElement field = myFixture.getElementAtCaret()
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
    myFixture.configureByText("a.groovy", """
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
    PsiElement field = myFixture.getElementAtCaret()
    myFixture.renameElement field, "ndame"

    myFixture.checkResult """class A {
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

  public void doTest() {
    final String testFile = getTestName(true).replace('$', '/') + ".test";
    final List<String> list = TestUtils.readInput(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/rename/" + testFile);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, list.get(0));

    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiElement resolved = ref == null ? null : ref.resolve();
    if (resolved instanceof PsiMethod && !(resolved instanceof GrAccessorMethod)) {
      PsiMethod method = (PsiMethod)resolved;
      String name = method.getName();
      String newName = createNewNameForMethod(name);
      myFixture.renameElementAtCaret(newName);
    } else if (resolved instanceof GrAccessorMethod) {
      GrField field = ((GrAccessorMethod)resolved).getProperty();
      RenameProcessor processor = new RenameProcessor(myFixture.getProject(), field, "newName", true, true);
      processor.addElement(resolved, createNewNameForMethod(((GrAccessorMethod)resolved).getName()));
      processor.run();
    } else {
      myFixture.renameElementAtCaret("newName");
    }
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResult(list.get(1));
  }

  private String createNewNameForMethod(final String name) {
    String newName = "newName";
    if (name.startsWith("get")) {
      newName = "get" + StringUtil.capitalize(newName);
    } else if (name.startsWith("is")) {
      newName = "is" + StringUtil.capitalize(newName);
    } else if (name.startsWith("set")) {
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

    myFixture.renameElement myFixture.findClass("Foo").getFields()[0], "newName"
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

    myFixture.renameElement myFixture.findClass("Foo").getFields()[0], "newName"
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
    myFixture.configureByText "Foo.gpp", "class Foo {}"
    myFixture.renameElement myFixture.findClass("Foo"), "Bar"
    assert "gpp", myFixture.file.virtualFile.extension
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
      myFixture.renameElement myFixture.findClass("Foo").getMethods()[0], "'newName'"
    } catch (ConflictsInTestsException e) {
      assertEquals "<b><code>'newName'</code></b> is not a correct identifier to use in <b><code>new Foo().foo</code></b>", e.getMessage()
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
    CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), "foo", myFixture);
    myFixture.checkResultByFile prefix + "_after.groovy"
  }

}
