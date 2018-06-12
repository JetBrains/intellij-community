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
package org.jetbrains.plugins.groovy.refactoring.introduce.field

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser.ReplaceChoice
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.refactoring.introduce.IntroduceConstantTest
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init
import org.jetbrains.plugins.groovy.util.TestUtils

import static com.intellij.refactoring.introduce.inplace.OccurrencesChooser.ReplaceChoice.ALL
import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.*
/**
 * @author Maxim.Medvedev
 */
class GrIntroduceFieldTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    "${TestUtils.testDataPath}refactoring/introduceField/"
  }

  void testSimple() {
    doTest(false, false, false, CUR_METHOD, false, null)
  }

  void testDeclareFinal() {
    doTest(false, false, true, FIELD_DECLARATION, false, null)
  }

  void testCreateConstructor() {
    doTest(false, false, true, CONSTRUCTOR, true, null)
  }

  void testManyConstructors() {
    doTest(false, false, true, CONSTRUCTOR, true, null)
  }

  void testDontReplaceStaticOccurrences() {
    doTest(false, false, true, FIELD_DECLARATION, true, null)
  }

  void testQualifyUsages() {
    doTest(false, false, true, FIELD_DECLARATION, true, null)
  }

  void testReplaceLocalVar() {
    doTest(false, true, false, CUR_METHOD, true, null)
  }

  void testIntroduceLocalVarByDeclaration() {
    doTest(false, true, false, FIELD_DECLARATION, true, null)
  }

  void testReplaceExpressionWithAssignment() {
    doTest(false, false, false, CUR_METHOD, false, null)
  }

  void testAnonymousClass() {
    doTest(false, false, false, CUR_METHOD, false, null)
  }

  void testAnonymous2() {
    doTest(false, false, false, CONSTRUCTOR, false, null)
  }

  void testAnonymous3() {
    doTest(false, false, false, CONSTRUCTOR, false, null)
  }

  void testInitializeInCurrentMethod() {
    doTest(false, true, true, CUR_METHOD, false, null)
  }

  void testScriptBody() {
    addGroovyTransformField()
    doTest('''\
print <selection>'abc'</selection>
''', '''\
import groovy.transform.Field

@Field f = 'abc'
print f<caret>
''', false, false, false, FIELD_DECLARATION)
  }

  void testScriptMethod() {
    addGroovyTransformField()
    doTest('''\
def foo() {
  print <selection>'abc'</selection>
}
''', '''\
import groovy.transform.Field

@Field final f = 'abc'

def foo() {
  print f<caret>
}
''', false, false, true, FIELD_DECLARATION)
  }

  void testStaticScriptMethod() {
    addGroovyTransformField()
    doTest('''\
static def foo() {
  print <selection>'abc'</selection>
}
''', '''\
import groovy.transform.Field

@Field static f = 'abc'

static def foo() {
  print f<caret>
}
''', true, false, false, FIELD_DECLARATION)
  }

  void testScriptMethod2() {
    addGroovyTransformField()
    doTest('''\
def foo() {
  print <selection>'abc'</selection>
}
''', '''\
import groovy.transform.Field

@Field f

def foo() {
    f = 'abc'
    print f<caret>
}
''', false, false, false, CUR_METHOD)
  }

  void testSetUp1() throws Exception {
    addTestCase()
    doTest('''\
class MyTest extends GroovyTestCase {
    void foo() {
        print <selection>'ac'</selection>
    }
}
''', '''\
class MyTest extends GroovyTestCase {
    def f

    void foo() {
        print f<caret>
    }

    void setUp() {
        super.setUp()
        f = 'ac'
    }
}
''',
false, false, false, SETUP_METHOD)
  }

  void testSetUp2() throws Exception {
    addTestCase()

    doTest('''\
class MyTest extends GroovyTestCase {
    void setUp() {
        super.setUp()
        def x = 'abc'
    }

    void foo() {
        print <selection>'ac'</selection>
    }
}
''', '''\
class MyTest extends GroovyTestCase {
    def f

    void setUp() {
        super.setUp()
        def x = 'abc'
        f = 'ac'
    }

    void foo() {
        print f<caret>
    }
}
''',
           false, false, false, SETUP_METHOD)
  }

  void testStringPart0() {
    doTest('''\
class A {
    def foo() {
        print 'a<selection>b</selection>c'
    }
}''', '''\
class A {
    def f = 'b'

    def foo() {
        print 'a' + f<caret> + 'c'
    }
}''', false, false, false, FIELD_DECLARATION, false, null)
  }

  void testStringPart1() {
    doTest('''\
class A {
    def foo() {
        print 'a<selection>b</selection>c'
    }
}''', '''\
class A {
    def f

    def foo() {
        f = 'b'
        print 'a' + f<caret> + 'c'
    }
}''', false, false, false, CUR_METHOD, false, null)
  }

  void testStringPart2() {
    doTest('''\
class A {
    def foo() {
        def f = 5
        print 'a<selection>b</selection>c'
    }
}''', '''\
class A {
    def f

    def foo() {
        def f = 5
        this.f = 'b'
        print 'a' + this.f<caret> + 'c'
    }
}''', false, false, false, CUR_METHOD, false, null)
  }

  void testGStringInjection() {
    doTest('''\
class GroovyLightProjectDescriptor  {
    public void configureModule() {
        print ("$<selection>mockGroovy2_1LibraryName</selection>!/");
    }

    def getMockGroovy2_1LibraryName() {''}
}
''', '''\
class GroovyLightProjectDescriptor  {
    def f

    public void configureModule() {
        f = mockGroovy2_1LibraryName
        print ("${f}!/");
    }

    def getMockGroovy2_1LibraryName() {''}
}
''', false, false, false, CUR_METHOD)
  }

  void testGStringInjection2() {
    doTest('''\
class GroovyLightProjectDescriptor  {
    public void configureModule() {
        print ("$<selection>mockGroovy2_1LibraryName</selection>.bytes!/");
    }

    def getMockGroovy2_1LibraryName() {''}
}
''', '''\
class GroovyLightProjectDescriptor  {
    def f

    public void configureModule() {
        f = mockGroovy2_1LibraryName
        print ("${f.bytes}!/");
    }

    def getMockGroovy2_1LibraryName() {''}
}
''', false, false, false, CUR_METHOD)
  }

  void 'test GString closure injection and initialize in current method'() {
    doTest '''\
class GroovyLightProjectDescriptor  {
    public void configureModule() {
        print ("${<selection>mockGroovy2_1LibraryName</selection>}!/");
    }

    def getMockGroovy2_1LibraryName() {''}
}
''', '''\
class GroovyLightProjectDescriptor  {
    def f

    public void configureModule() {
        f = mockGroovy2_1LibraryName
        print ("${f}!/");
    }

    def getMockGroovy2_1LibraryName() {''}
}
''', false, false, false, CUR_METHOD
  }

  void testInitializeInMethodInThenBranch() {
    doTest('''\
class A {
    def foo() {
        if (abc) print <selection>2</selection>
    }
}
''', '''\
class A {
    def f

    def foo() {
        if (abc) {
            f = 2
            print f
        }
    }
}
''', false, false, false, CUR_METHOD, false, null)
  }

  void testFromVar() {
    doTest('''\
class A {
    def foo() {
        def <selection>a = 5</selection>
        print a
    }
}''', '''\
class A {
    def f = 5

    def foo() {
        print f
    }
}''', false, true, false, FIELD_DECLARATION, true, null)
  }

  void 'test replace top level expression within constructor and initialize in current method'() {
    doTest '''\
class TestClass {
    TestClass() {
        new St<caret>ring()
    }

    TestClass(a) {
    }
}
''', '''\
class TestClass {
    def f

    TestClass() {
        f = new String()
    }

    TestClass(a) {
    }
}
''', false, false, false, CUR_METHOD
  }

  void 'test replace top level expression within constructor and initialize field'() {
    doTest '''\
class TestClass {
    TestClass() {
        new St<caret>ring()
    }

    TestClass(a) {
    }
}
''', '''\
class TestClass {
    def f = new String()

    TestClass() {
        f
    }

    TestClass(a) {
    }
}
''', false, false, false, FIELD_DECLARATION
  }

  void 'test replace top level expression within constructor and initialize in constructor'() {
    doTest '''\
class TestClass {
    TestClass() {
        new St<caret>ring()
    }

    TestClass(a) {
    }
}
''', '''\
class TestClass {
    def f

    TestClass() {
        f = new String()
    }

    TestClass(a) {
        f = new String()
    }
}
''', false, false, false, CONSTRUCTOR
  }

  void 'test replace non top level expression within constructor and initialize in current method'() {
    doTest '''\
class TestClass {
    TestClass() {
        <selection>new String()</selection>.empty
    }

    TestClass(a) {
    }
}
''', '''\
class TestClass {
    def f

    TestClass() {
        f = new String()
        f.empty
    }

    TestClass(a) {
    }
}
''', false, false, false, CUR_METHOD
  }

  void 'test replace non top level expression within constructor and initialize field'() {
    doTest '''\
class TestClass {
    TestClass() {
        <selection>new String()</selection>.empty
    }

    TestClass(a) {
    }
}
''', '''\
class TestClass {
    def f = new String()

    TestClass() {
        f.empty
    }

    TestClass(a) {
    }
}
''', false, false, false, FIELD_DECLARATION
  }

  void 'test replace non top level expression within constructor and initialize in constructor'() {
    doTest '''\
class TestClass {
    TestClass() {
        <selection>new String()</selection>.empty
    }

    TestClass(a) {
    }
}
''', '''\
class TestClass {
    def f

    TestClass() {
        f = new String()
        f.empty
    }

    TestClass(a) {
        f = new String()
    }
}
''', false, false, false, CONSTRUCTOR
  }

  void 'test replace string injection and initialize in constructor'() {
    doTest '''\
class TestClass {
    TestClass() {
        "${<selection>new String()</selection>}"
    }
    TestClass(a) {
    }
}
''','''\
class TestClass {
    def f

    TestClass() {
        f = new String()
        "${f}"
    }
    TestClass(a) {
        f = new String()
    }
}
''', false, false, false, CONSTRUCTOR
  }

  void 'test introduce field in script with invalid class name'() {
    myFixture.configureByText "abcd-efgh.groovy", '''\
def aaa = "foo"
def bbb = "bar"
println(<selection>aaa + bbb</selection>)
'''
    performRefactoring(null, false, false, false, CUR_METHOD, false)
    myFixture.checkResult '''\
import groovy.transform.Field

@Field f
def aaa = "foo"
def bbb = "bar"
f = aaa + bbb
println(f)
'''
  }

  void 'test cannot initialize in current method when introducing from field initializer'() {
    doTestInitInTarget '''
class A {
  def object = <selection>new Object()</selection>
}
''', EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION)

    doTestInitInTarget '''
class A {
  def object = <selection>new Object()</selection>
  def object2 = new Object()
}
''', EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION)

    doTestInitInTarget '''
class A {
  def object = <selection>new Object()</selection>
  def object2 = new Object()
}
''', EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION), ReplaceChoice.NO
  }

  void 'test can not initialize in current method with some occurence outside'() {
    doTestInitInTarget '''
class A {
  def field = new Object()
  def foo() {
    def a = <selection>new Object()</selection>
  }
}
''', EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION)
  }

  void 'test can initialize in current method from within method'() {
    doTestInitInTarget '''
class A {
  def foo() {
    def a = <selection>new Object()</selection>
  }
}
''', EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION, CUR_METHOD)

    doTestInitInTarget '''
class A {
  def field = new Object()
  def foo() {
    def a = <selection>new Object()</selection>
  }
}
''', EnumSet.of(CONSTRUCTOR, FIELD_DECLARATION, CUR_METHOD), ReplaceChoice.NO
  }

  void 'test can initialize script field in current method only'() {
    doTestInitInTarget '''
def a = 1
def b = 2
println(<selection>a + b</selection>)
''', EnumSet.of(CUR_METHOD)

    doTestInitInTarget '''
def a = 1
def b = 2
println(<selection>a + b</selection>)
''', EnumSet.of(CUR_METHOD), ReplaceChoice.NO

    doTestInitInTarget '''
def a = 1
def b = 2
def c = a + b
println(<selection>a + b</selection>)
''', EnumSet.of(CUR_METHOD)

    doTestInitInTarget '''
def a = 1
def b = 2
def c = a + b
println(<selection>a + b</selection>)
''', EnumSet.of(CUR_METHOD), ReplaceChoice.NO
  }

  void 'test introduce field from this'() {
    doTest '''\
class A {
    def bar 
    def foo() {
        th<caret>is.bar
    }
}
''', '''\
class A {
    def bar
    def f = this

    def foo() {
        f.bar
    }
}
''', false, false, false, FIELD_DECLARATION
  }

  private void doTest(final boolean isStatic,
                      final boolean removeLocal,
                      final boolean declareFinal,
                      @NotNull final GrIntroduceFieldSettings.Init initIn,
                      final boolean replaceAll = false,
                      @Nullable final String selectedType = null) {
    myFixture.configureByFile("${getTestName(false)}.groovy")
    performRefactoring(selectedType, isStatic, removeLocal, declareFinal, initIn, replaceAll)
    myFixture.checkResultByFile("${getTestName(false)}_after.groovy")
  }

  private void doTest(@NotNull final String textBefore,
                      @NotNull String textAfter,
                      final boolean isStatic,
                      final boolean removeLocal,
                      final boolean declareFinal,
                      @NotNull final GrIntroduceFieldSettings.Init initIn,
                      final boolean replaceAll = false,
                      @Nullable final String selectedType = null) {
    myFixture.configureByText("_.groovy", textBefore)
    performRefactoring(selectedType, isStatic, removeLocal, declareFinal, initIn, replaceAll)
    myFixture.checkResult(textAfter)
  }

  private void performRefactoring(String selectedType, boolean isStatic, boolean removeLocal, boolean declareFinal, GrIntroduceFieldSettings.Init initIn, boolean replaceAll) {
    final PsiType type = selectedType == null ? null : JavaPsiFacade.getElementFactory(project).createTypeFromText(selectedType, myFixture.file)
    final IntroduceFieldTestHandler handler = new IntroduceFieldTestHandler(isStatic, removeLocal, declareFinal, initIn, replaceAll, type)
    handler.invoke(project, myFixture.editor, myFixture.file, null)
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
  }

  private void doTestInitInTarget(String text, EnumSet<Init> expected = EnumSet.noneOf(Init), ReplaceChoice replaceChoice = ALL) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text)
    def handler = new GrIntroduceFieldHandler()

    def expression = IntroduceConstantTest.findExpression(myFixture)
    def variable = IntroduceConstantTest.findVariable(myFixture)
    def stringPart = IntroduceConstantTest.findStringPart(myFixture)
    def scopes = handler.findPossibleScopes(expression, variable, stringPart, editor)
    assert scopes.length == 1
    def scope = scopes[0]

    def context = handler.getContext(getProject(), myFixture.editor, expression, variable, stringPart, scope)
    def initPlaces = GrInplaceFieldIntroducer.getApplicableInitPlaces(context, replaceChoice == ALL)
    assert initPlaces == expected
  }
}
