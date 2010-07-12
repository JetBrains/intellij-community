package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import junit.framework.ComparisonFailure
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil
import org.jetbrains.plugins.groovy.util.TestUtils
import com.intellij.psi.*

/**
 * @author peter
 */
class GppFunctionalTest extends LightCodeInsightFixtureTestCase {
  static def descriptor = new GppProjectDescriptor()

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }

  protected void setUp() {
    super.setUp()
  }

  public void testCastListToIterable() throws Exception {
    myFixture.addClass("class X extends java.util.ArrayList<Integer> {}")
    testAssignability """
X ints = [239, 4.2d]
"""
  }

  public void testCastListToAnything() throws Exception {
    testAssignability """
File f1 = ['path']
File f2 = <warning descr="Cannot assign 'List<Serializable>' to 'File'">['path', 2, true, 42]</warning>
"""
  }

  public void testCastMapToAnotherMap() throws Exception {
    myFixture.addClass """
public class Y extends java.util.HashMap<String, String> {
  public Y(initialCapacity) {
    super(initialCapacity);
  }
}
"""

    testAssignability """
HashMap<String, File> m1 = ['a':['b']]
Y y = <warning descr="Cannot assign 'Map<String, String>' to 'Y'">[a:'b']</warning>
"""
  }

  public void testAnonymousClass() throws Exception {
    testAssignability """
def x = new Object() {
  def foo() {
    HashMap<String, File> m1 = ['a':['b']]
    HashMap<String, File> m2 = <warning descr="Cannot assign 'File' to 'HashMap<String, File>'">new File('aaa')</warning>
  }
}
"""
  }

  public void testCastMapToObject() throws Exception {
    myFixture.addClass("class Foo { String name; void foo() {} }")
    testAssignability """
Foo f = [name: 'aaa', foo: { println 'hi' }, anotherProperty: 42 ]
"""
  }

  void testAssignability(String text) {
    myFixture.enableInspections new GroovyAssignabilityCheckInspection()
    PsiFile file = configureScript(text)
    myFixture.testHighlighting(true, false, false, file.virtualFile)
  }

  private PsiFile configureScript(String text) {
    return myFixture.configureByText("a.groovy", text)
  }

  public void testDeclaredVariableTypeIsMoreImportantThanTheInitializerOne() throws Exception {
    configureScript("""
File f = ['path']
f.mk<caret>
""")
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "mkdir", "mkdirs"
  }
  
  public void testDeclaredVariableTypeIsMoreImportantThanTheInitializerOne2() throws Exception {
    myFixture.addClass """
public class Some {
    public int prop

    public void f_foo() {}
    public void f_bar() {}
}
"""

    configureScript("""
Some s = [prop: 239]
s.f_<caret>
""")
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "f_foo", "f_bar"
  }

  public void testResolveMethod() throws Exception {
    myFixture.configureByText("a.groovy", """
def foo(File f) {}
@Typed def bar() {
  fo<caret>o(['path'])
}
""")
    def reference = findReference()
    def target = reference.resolve()
    assertEquals "foo", ((GrMethod)target).name
  }

  private PsiReference findReference() {
    return myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
  }

  public void testOverloadingWithConversion() throws Exception {
    myFixture.configureByText("a.groovy", """
def foo(List l) {}
def foo(File f) {}
@Typed def bar() {
  fo<caret>o(['path'])
}
""")
    def reference = findReference()
    def target = reference.resolve()
    assertNotNull target
    assert target.text.contains("List l")
  }

  public void testCastClosureToOneMethodClass() throws Exception {
    myFixture.addClass """
public abstract class Foo {
  public abstract void foo(String s);
  public abstract void bar(String s);
}
public interface Action {
  void act();
}
"""

    testAssignability """
Foo f = <warning descr="Cannot assign 'Closure' to 'Foo'">{ println it }</warning>
Function1<String, Object> f1 = { println it }
Function1<String, Object> f2 = { x=42 -> println x }
Function1<String, Object> f3 = <warning descr="Cannot assign 'Closure' to 'Function1<String, Object>'">{ int x -> println x }</warning>
Runnable r = { println it }
Action a = { println it }
Action a1 = { a2 = 2 -> println a2 }
"""
  }

  public void testClosureParameterTypesInAssignment() throws Exception {
    configureScript "Function1<String, Object> f = { it.subs<caret> }"
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testClosureParameterTypesInMethodInvocation() throws Exception {
    myFixture.configureByText "a.groovy", """
def foo(int a = 1, Function1<String, Object> f) {}
def foo(String s) {}
def foo(Function2<Integer, String, Object> f) {}

@Typed def bar() {
  foo { it.subsREF }
  foo(1, { it.subsREF })
  foo 1, { it.subsREF }
  foo(1) { it.subsREF }
  foo { a -> a.subsREF }
  foo { a, int b=2 -> a.subsREF }
  foo { a, b -> b.subsREF }
}
"""
    def text = myFixture.file.text
    def pos = 0
    while (true) {
      pos = text.indexOf("REF", pos+1)
      if (pos < 0) {
        break
      }
      myFixture.editor.caretModel.moveToOffset pos
      myFixture.completeBasic()
      try {
        assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
      }
      catch (ComparisonFailure ex) {
        println "at: " + text[0..<pos] + "<caret>" + text[pos..<text.size()]
        throw ex
      }
      LookupManager.getInstance(project).hideActiveLookup()
    }
  }

  public void testReturnTypeOneMethodInterface() throws Exception {
    myFixture.configureByText "a.groovy", """
@Typed Function1<String, Integer> bar() {
   { it.subs<caret> }
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testClosureInMapInstantiation() throws Exception {
    myFixture.configureByText "a.groovy", """
class Foo<T> {
  int foo(T a) {}
}

@Typed Foo<String> bar() {
    return [foo: { it.subs<caret> }]
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testClosureInMapInstantiationBoxPrimitives() throws Exception {
    myFixture.configureByText "a.groovy", """
class Foo {
  int foo(int a) {}
}

@Typed Foo bar() {
    return [foo: { it.intV<caret>V }]
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "intValue"
  }

  public void testClosureInListInstantiation() throws Exception {
    myFixture.configureByText "a.groovy", """
class Foo {
  def Foo(int a, Function1<String, Integer> f) {}
}

@Typed Foo foo() {
  [239, { s -> s.subs<caret> }]
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testTraitHighlighting() throws Exception {
    myFixture.configureByText "a.groovy", """
@Trait
abstract class Intf {
  abstract void foo()
  void bar() {}
}
class <error descr="Method 'foo' is not implemented">Foo</error> implements Intf {}
class <error descr="Method 'foo' is not implemented">Wrong</error> extends Foo {}
class Bar implements Intf {
  void foo() {}
}
"""
    myFixture.testHighlighting(true, false, false, myFixture.file.virtualFile)
  }

  public void testTraitImplementingAndNavigation() throws Exception {
    myFixture.configureByText "a.groovy", """
@Trait
abstract class <caret>Intf {
  abstract void foo()
  void bar() {}
}
class Foo implements Intf {}
class Bar implements Intf {
  void foo() {}
}
class BarImpl extends Bar {}
"""
    def facade = JavaPsiFacade.getInstance(getProject())
    assertOneElement(GroovyOverrideImplementUtil.getMethodsToOverrideImplement(facade.findClass("Foo"), true))

    GrTypeDefinition barClass = facade.findClass("Bar")
    assertEmpty(GroovyOverrideImplementUtil.getMethodsToOverrideImplement(barClass, true))
    assertTrue "bar" in GroovyOverrideImplementUtil.getMethodsToOverrideImplement(barClass, false).collect { ((PsiMethod) it.element).name }

    assertEmpty(GroovyOverrideImplementUtil.getMethodsToOverrideImplement(facade.findClass("BarImpl"), true))

    def implementations = new GotoImplementationHandler().getSourceAndTargetElements(myFixture.editor, myFixture.file).targets
    assertEquals Arrays.toString(implementations), 3, implementations.size()
  }

  public void testResolveToStdLib() throws Exception {
    configureScript """
@Typed def foo(List<String> l) {
  l.ea<caret>ch { l.substring(1) }
}
"""
    PsiMethod method = resolveReference().navigationElement
    assertEquals "each", method.name
    assertEquals "groovy.util.Iterations", method.containingClass.qualifiedName
  }

  private PsiElement resolveReference() {
    return myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset).resolve()
  }

  public void testMethodTypeParameterInference() throws Exception {
    configureScript """
@Typed package aaa

java.util.concurrent.atomic.AtomicReference<Integer> r = [2]
r.apply { it.intV<caret>i }
"""
    myFixture.completeBasic()
    assertSameElements myFixture.getLookupElementStrings(), "intValue"
  }

  public void testMethodTypeParameterInference2() throws Exception {
    configureScript """
@Typed package aaa

java.util.concurrent.atomic.AtomicReference<Integer> r = [2]
r.apply { it.intV<caret>i } {}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.getLookupElementStrings(), "intValue"
  }

  public void testGotoDeclarationFromMapLiterals() throws Exception {
    PsiClass point = myFixture.addClass("""
class Point {
  int y;
  void setX(int x) {}
  void move(int x, int y) {}
}""")

    configureScript "Point p = [<caret>y:2]"
    assertEquals point.findFieldByName("y", false), resolveReference()

    configureScript "Point p = [<caret>x:2]"
    assertEquals point.findMethodsByName("setX", false)[0], resolveReference()

    configureScript "Point p = [mo<caret>ve: { x, y -> z }]"
    assertEquals point.findMethodsByName("move", false)[0], resolveReference()
  }

  public void testResolveTraitMethod() throws Exception {
    configureScript """
@Trait
class Some {
	public void doSmth() { println "hello" }
}
Some s
s.do<caret>Smth()
"""
    assertEquals "doSmth", ((PsiMethod) findReference().resolve()).name
  }

}

class GppProjectDescriptor extends DefaultLightProjectDescriptor {
  @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY++").getModifiableModel();
    modifiableModel.addRoot(JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.absoluteTestDataPath + "mockGroovypp/groovypp-0.2.3.jar!/"), OrderRootType.CLASSES);
    modifiableModel.addRoot(JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.mockGroovy1_7LibraryName + "!/"), OrderRootType.CLASSES);
    modifiableModel.commit();
  }
}
