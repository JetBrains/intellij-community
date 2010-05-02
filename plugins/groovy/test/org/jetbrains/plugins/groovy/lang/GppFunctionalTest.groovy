package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import junit.framework.ComparisonFailure

/**
 * @author peter
 */
class GppFunctionalTest extends LightCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyHighlightingTest.GROOVY_17_PROJECT_DESCRIPTOR;
  }

  protected void setUp() {
    super.setUp()
    myFixture.addClass"package groovy.lang; public @interface Typed {}"
    myFixture.addClass """
package groovy.lang;
public interface Function1<T,R> {
  public abstract R call(T param);
}"""
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
File f2 = <warning descr="Cannot assign 'List' to 'File'">['path', 2, true, 42]</warning>
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
Y y = <warning descr="Cannot assign 'Map' to 'Y'">[a:'b']</warning>
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
    PsiFile file = configureTyped(text)
    myFixture.testHighlighting(true, false, false, file.virtualFile)
  }

  private PsiFile configureTyped(String text) {
    return myFixture.configureByText("a.groovy", """
@Typed def foo() {
  $text
}""")
  }

  public void testDeclaredVariableTypeIsMoreImportantThanTheInitializerOne() throws Exception {
    configureTyped("""
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

    configureTyped("""
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
    def reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    def target = reference.resolve()
    assertEquals "foo", ((GrMethod)target).name
  }

  public void testOverloadingWithConversion() throws Exception {
    myFixture.configureByText("a.groovy", """
def foo(List l) {}
def foo(File f) {}
@Typed def bar() {
  fo<caret>o(['path'])
}
""")
    def reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
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
Action a1 = { a = 2 -> println a }
"""
  }

  public void testClosureParameterTypesInAssignment() throws Exception {
    configureTyped "Function1<String, Object> f = { it.subs<caret> }"
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testClosureParameterTypesInMethodInvocation() throws Exception {
    myFixture.addClass """
package groovy.lang;
public interface Function2<T,V,R> {
  public abstract R call(T param, V param2);
}"""

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
  return { it.subsREF }
}
"""
  }

}
