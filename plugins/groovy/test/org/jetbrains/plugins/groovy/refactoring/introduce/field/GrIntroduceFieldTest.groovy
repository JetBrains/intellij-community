/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;


import com.intellij.openapi.application.WriteAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.*

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    "${TestUtils.testDataPath}refactoring/introduceField/";
  }

  public void testSimple() {
    doTest(false, false, false, CUR_METHOD, false, null);
  }

  public void testDeclareFinal() {
    doTest(false, false, true, FIELD_DECLARATION, false, null);
  }

  public void testCreateConstructor() {
    doTest(false, false, true, CONSTRUCTOR, true, null);
  }

  public void testManyConstructors() {
    doTest(false, false, true, CONSTRUCTOR, true, null);
  }

  public void testDontReplaceStaticOccurrences() {
    doTest(false, false, true, FIELD_DECLARATION, true, null);
  }

  public void testQualifyUsages() {
    doTest(false, false, true, FIELD_DECLARATION, true, null);
  }

  public void testReplaceLocalVar() {
    doTest(false, true, false, CUR_METHOD, true, null);
  }

  public void testIntroduceLocalVarByDeclaration() {
    doTest(false, true, false, FIELD_DECLARATION, true, null);
  }

  public void testReplaceExpressionWithAssignment() {
    doTest(false, false, false, CUR_METHOD, false, null);
  }

  public void testAnonymousClass() {
    doTest(false, false, false, CUR_METHOD, false, null);
  }

  public void testAnonymous2() {
    doTest(false, false, false, CONSTRUCTOR, false, null);
  }

  public void testAnonymous3() {
    doTest(false, false, false, CONSTRUCTOR, false, null);
  }

  public void testInitializeInCurrentMethod() {
    doTest(false, true, true, CUR_METHOD, false, null);
  }

  public void testScriptBody() {
    addGroovyTransformField()
    doTest('''\
print <selection>'abc'</selection>
''', '''\
import groovy.transform.Field

@Field f = 'abc'
print f<caret>
''', false, false, false, FIELD_DECLARATION)
  }

  public void testScriptMethod() {
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

  public void testStaticScriptMethod() {
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

  public void testScriptMethod2() {
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

  public void testSetUp1() throws Exception {
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

  public void testSetUp2() throws Exception {
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


  private void doTest(final boolean isStatic,
                      final boolean removeLocal,
                      final boolean declareFinal,
                      @NotNull final GrIntroduceFieldSettings.Init initIn,
                      final boolean replaceAll = false,
                      @Nullable final String selectedType = null) {
    myFixture.configureByFile("${getTestName(false)}.groovy")
    performRefactoring(selectedType, isStatic, removeLocal, declareFinal, initIn, replaceAll)
    myFixture.checkResultByFile("${getTestName(false)}_after.groovy");
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
    myFixture.checkResult(textAfter);
  }


  private void performRefactoring(String selectedType, boolean isStatic, boolean removeLocal, boolean declareFinal, GrIntroduceFieldSettings.Init initIn, boolean replaceAll) {
    final PsiType type = selectedType == null ? null : JavaPsiFacade.getElementFactory(project).createTypeFromText(selectedType, myFixture.file)
    def accessToken = WriteAction.start()
    try {
      final IntroduceFieldTestHandler handler = new IntroduceFieldTestHandler(isStatic, removeLocal, declareFinal, initIn, replaceAll, type)
      handler.invoke(project, myFixture.editor, myFixture.file, null);
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
    }
    finally {
      accessToken.finish()
    }
  }
}
