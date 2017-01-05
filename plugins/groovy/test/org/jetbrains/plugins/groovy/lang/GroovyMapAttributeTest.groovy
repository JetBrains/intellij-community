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
package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Sergey Evdokimov
 */
@CompileStatic
class GroovyMapAttributeTest extends LightCodeInsightFixtureTestCase {
  final LightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY").modifiableModel
      final VirtualFile groovyJar = JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.mockGroovy1_7LibraryName + "!/")
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES)
      modifiableModel.commit()
    }
  }

  private void doTestCompletion(String fileText, boolean exists) {
    myFixture.configureByText("a.groovy", fileText)
    def res = myFixture.completeBasic()

    assertNotNull res

    Set<String> variants = new HashSet<String>()

    for (LookupElement element : res) {
      variants.add(element.getLookupString())
    }

    if (exists) {
      assertTrue(variants.contains("sss1"))
      assertTrue(variants.contains("sss2"))
    }
    else {
      assertTrue(!variants.contains("sss1"))
      assertTrue(!variants.contains("sss2"))
    }
  }

  void testEmptyConstructorCompletion() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}

  static {
    new Aaa(<caret>)
  }
}
""", true)
  }

  void testNoDefaultConstructor() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa(String sss) {}

  static {
    new Aaa(<caret>)
  }
}
""", false)
  }

  void testDefaultConstructorAndNonDefault() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}

  public Aaa(String sss) {}

  static {
    new Aaa(<caret>)
  }
}
""", true)
  }

  void testHasMapConstructor() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {
  }

  public Aaa(Map mmm) {}

  static {
    new Aaa(<caret>)
  }
}
""", true)
  }

  void testHasHashMapConstructor() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {
  }

  public Aaa(java.util.HashMap mmm) {}

  static {
    new Aaa(<caret>)
  }
}
""", true)
  }

  void testMapNotFirstConstructor() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}

  public Aaa(int x, Map mmm) {}

  static {
    new Aaa(<caret>)
  }
}
""", true)
  }

  void testCallOtherConstructor() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}

  public Aaa(String s) {
    this(sss<caret>: )
  }
}
""", false)
  }

  void testWithMap() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}

  public Aaa(Map map, String s) {

  }

  static {
    new Aaa(<caret>)
  }
}
""", true)
  }

  void testWithMap2() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}

  public Aaa(Map map, String s = null) {

  }

  static {
    new Aaa(<caret>)
  }
}
""", true)
  }

  void testAlreadyHasNonMapParameter() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}

  static {
    new Aaa(1, <caret>)
  }
}
""", false)
  }

  void testAlreadyHasNonMapParameter1() {
    doTestCompletion("""
class Aaa {
  String sss1
  String sss2

  public Aaa() {}
  public Aaa(String s) {}

  static {
    new Aaa(<caret>, "dff")
  }
}
""", false)
  }

  void testConstructorInJavaClass() {
    myFixture.addFileToProject("Ccc.java", """
public class Ccc {
  private String sss1
  private String sss2

  private void setSss3(String s) {}
  private void setSss4(String s) {}

  public Aaa() {}
  public Aaa(String s) {}
}
""")

    myFixture.configureByText("a.groovy", "new Ccc(ss<caret>)")

    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'sss1', 'sss2', 'sss3', 'sss4'
  }

  void testRenameProperty() {
    def groovyFile = myFixture.addFileToProject("g.groovy", "new Aaa(sss: '1')")
    myFixture.configureByText("Aaa.java", """
public class Aaa {
  public String sss<caret>;
}
""")
    myFixture.renameElementAtCaret("field")

    assertEquals("new Aaa(field: '1')", groovyFile.text)
  }

  void testRenameMethod() {
    def groovyFile = myFixture.addFileToProject("g.groovy", "new Aaa(sss: '1')")
    myFixture.configureByText("Aaa.java", """
public class Aaa {
  public void setSss<caret>(String s){}
}
""")
    myFixture.renameElementAtCaret("setField")

    assertEquals("new Aaa(field: '1')", groovyFile.text)
  }

  private void doTestHighlighting(String text) {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection)
    myFixture.enableInspections(GroovyUncheckedAssignmentOfMemberOfRawTypeInspection)

    myFixture.configureByText("a.groovy", text)
    myFixture.checkHighlighting(true, false, true)
  }

  void testCheckingTypeString() {
    doTestHighlighting """
class Ccc {
  String foo

  static {
println(new Ccc(foo: 123))
println(new Ccc(foo: 123L))
println(new Ccc(foo: 123f))
println(new Ccc(foo: 'text'))
println(new Ccc(foo: null))
println(new Ccc(foo: new Object()))
println(new Ccc(foo: new Object[1]))
println(new Ccc(foo: Collections.singletonList("as")))
  }
}
"""
  }

  void testCheckingTypeInt() {
    doTestHighlighting """
class Ccc {
  int foo

  static {
println(new Ccc(foo: 123))
println(new Ccc(foo: new Integer(123)))
println(new Ccc(foo: 123L))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Boolean'">true</warning>))
println(new Ccc(foo: 123f))
println(new Ccc(foo: new Float(123f)))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'String'">'1111'</warning>))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'null'">null</warning>))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object'">new Object()</warning>))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object[]'">new Object[1]</warning>))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'List<String>'">Collections.singletonList("as")</warning>))
  }
}
"""
  }

  void testCheckingTypeInteger() {
    doTestHighlighting """
class Ccc {
  Integer foo

  static {
println(new Ccc(foo: 123))
println(new Ccc(foo: new Integer(123)))
println(new Ccc(foo: 123L))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Boolean'">true</warning>))
println(new Ccc(foo: 123f))
println(new Ccc(foo: new Float(123f)))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'String'">'1111'</warning>))
println(new Ccc(foo: null))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object'">new Object()</warning>))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object[]'">new Object[1]</warning>))
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'List<String>'">Collections.singletonList("as")</warning>))
  }
}
"""
  }

  void testCheckingTypeList() {
    doTestHighlighting """
class Ccc {
  List foo

  static {
println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Integer'">123</warning>))
println(new Ccc(foo: null))
println(new Ccc(foo: new Object[1]))
println(new Ccc(foo: []))
println(new Ccc(foo: [1,2,3]))
println(new Ccc(foo: Collections.singletonList("as")))
println(new Ccc(foo: Collections.singletonList(1)))
  }
}
"""
  }

  void testCheckingTypeGeneric() {
    myFixture.addFileToProject("Ccc.groovy", """
class Ccc<T> {
  public void setFoo(T t) {}
}

class CccMap extends Ccc<Map> {}

class CccList extends Ccc<ArrayList> {}
""")

    doTestHighlighting """
println(new CccMap(foo: [:]))
println(new CccList(foo: []))

println(new CccMap(foo: <warning descr="Type of argument 'foo' can not be 'List'">[]</warning>))
println(new CccList(foo: <warning descr="Type of argument 'foo' can not be 'LinkedHashMap'">[:]</warning>))
"""
  }

  void testCompletionFieldClosureParam() {
    doTestCompletion("""
class Test {
  def field = {attr ->
    return attr.sss1 + attr.sss2
  };

  {
    field(ss<caret>)
  }
}
""", true)
  }

  void testCompletionVariableClosureParam() {
    doTestCompletion("""
class Test {
  {
    def variable = {attr ->
      return attr.sss1 + attr.sss2
    }

    variable(ss<caret>)
  }
}
""", true)
  }

  void testCompletionReturnMethod() {
    myFixture.addFileToProject("Ccc.groovy", """
class Ccc {
  String sss1;
  String sss2;
}
""")

    doTestCompletion """
class Test {
  public Ccc createC(HashMap m) {
    return new Ccc(m)
  }

  public static void main(String[] args) {
    println(new Test().createC(<caret>))
  }
}
""", true
  }

  void 'test completion within some map'() {
    doTestCompletionWithinMap '[<caret>]', '[bar: <caret>]'
  }

  void 'test completion within map in argument list'() {
    doTestCompletionWithinMap 'foo(1, 2, 3, [<caret>])', 'foo(1, 2, 3, [bar: <caret>])'
  }

  private doTestCompletionWithinMap(String text, String text2 = null) {
    PlatformTestUtil.registerExtension GroovyNamedArgumentProvider.EP_NAME, new GroovyNamedArgumentProvider() {
      @Override
      Map<String, NamedArgumentDescriptor> getNamedArguments(@NotNull GrListOrMap literal) {
        ['foo': NamedArgumentDescriptor.SIMPLE_NORMAL, 'bar': NamedArgumentDescriptor.SIMPLE_NORMAL]
      }
    }, testRootDisposable

    myFixture.with {
      configureByText '_.groovy', text

      completeBasic()
      lookupElementStrings.with {
        assert 'foo' in it
        assert 'bar' in it
      }

      type 'ba\n'
      if (text2) checkResult text2

      type ',' as char
      completeBasic()
      lookupElementStrings.with {
        assert 'foo' in it
        assert !('bar' in it)
      }
    }
  }
}
