/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.actions.generate

import com.intellij.codeInsight.generation.*
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.RunResult
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.actions.generate.accessors.GroovyGenerateGetterSetterAction
import org.jetbrains.plugins.groovy.actions.generate.constructors.GroovyGenerateConstructorHandler
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class GroovyGenerateMembersTest extends LightCodeInsightFixtureTestCase {
  public void testConstructorAtOffset() {
    doConstructorTest();
  }

  public void testConstructorAtEnd() {
    doConstructorTest();
  }

  public void testLonelyConstructor() {
    doConstructorTest();
  }

  public void testConstructorInJavaInheritor() {
    myFixture.configureByText "GrBase.groovy", """
abstract class GrBase {
    GrBase(int i) { }
}
"""
    myFixture.configureByText "Inheritor.java", """
class Inheritor extends GrBase {
    <caret>
}
"""
    generateConstructor(true)
    myFixture.checkResult """
class Inheritor extends GrBase {
    public Inheritor(int i) {
        super(i);
    }
}
"""
  }

  public void testExplicitArgumentTypes() {
    myFixture.configureByText("a.groovy", """
class Super {
  def Super(a, int b) {}
}

class Foo extends Super {
  int c
  Object d
  final e
  <caret>
}
""")
    generateConstructor()
    myFixture.checkResult """
class Super {
  def Super(a, int b) {}
}

class Foo extends Super {
  int c
  Object d
  final e

    Foo(a, int b, int c, Object d, e) {
        super(a, b)
        this.c = c
        this.d = d
        this.e = e
    }
}
"""
  }

  void testSubstitutionInConstructor() {
    myFixture.configureByText("a.groovy", '''
class Super<E> {
  def Super(Collection<E> c) {}
}

class X {}

class Foo extends Super<X> {
  <caret>
}
''')
    generateConstructor()
    myFixture.checkResult('''
class Super<E> {
  def Super(Collection<E> c) {}
}

class X {}

class Foo extends Super<X> {
    Foo(Collection<X> c) {
        super(c)
    }
}
''')
  }

  void testGetter1() {
    myFixture.configureByText 'a.groovy', '''
class Test {
    def foo
    <caret>
}'''
    generateGetter()

    myFixture.checkResult '''
class Test {
    def foo

    def getFoo() {
        return foo
    }
}'''
  }

  void testGetter2() {
    myFixture.configureByText 'a.groovy', '''
  class Test {
      int foo
      <caret>
  }'''
    generateGetter()

    myFixture.checkResult '''
  class Test {
      int foo

      int getFoo() {
          return foo
      }
  }'''
  }

  void testGetter3() {
    myFixture.configureByText 'a.groovy', '''
  class Test {
      static foo
      <caret>
  }'''
    generateGetter()

    myFixture.checkResult '''
  class Test {
      static foo

      static getFoo() {
          return foo
      }
  }'''
  }

  void testGetter4() {
    myFixture.addFileToProject('org/jetbrains/annotations/Nullable.java', 'package org.jetbrains.annotations; public @interface Nullable {}')

    myFixture.configureByText 'a.groovy', '''
  import org.jetbrains.annotations.Nullable

  class Test {
      @Nullable
      def foo
      <caret>
  }'''
    generateGetter()

    myFixture.checkResult '''
  import org.jetbrains.annotations.Nullable

  class Test {
      @Nullable
      def foo

      @Nullable
      getFoo() {
          return foo
      }
  }'''
  }

  void testSetter1() {
    myFixture.configureByText 'a.groovy', '''
class Test {
    def foo
    <caret>
}'''

    generateSetter()

    myFixture.checkResult '''
class Test {
    def foo

    void setFoo(foo) {
        this.foo = foo
    }
}'''
  }

  void testSetter2() {
    myFixture.configureByText 'a.groovy', '''
class Test {
    int foo
    <caret>
}'''

    generateSetter()

    myFixture.checkResult '''
class Test {
    int foo

    void setFoo(int foo) {
        this.foo = foo
    }
}'''
  }

  void testSetter3() {

    myFixture.configureByText 'a.groovy', '''
class Test {
    static foo
    <caret>
}'''

    generateSetter()

    myFixture.checkResult '''
class Test {
    static foo

    static void setFoo(foo) {
        Test.foo = foo
    }
}'''
  }

  void testSetter4() {
    myFixture.addFileToProject('org/jetbrains/annotations/Nullable.java', 'package org.jetbrains.annotations; public @interface Nullable {}')

    myFixture.configureByText 'a.groovy', '''
import org.jetbrains.annotations.Nullable

class Test {
    @Nullable
    def foo
    <caret>
}'''

    generateSetter()

    myFixture.checkResult '''
import org.jetbrains.annotations.Nullable

class Test {
    @Nullable
    def foo

    void setFoo(@Nullable foo) {
        this.foo = foo
    }
}'''
  }

  void testConstructorInTheMiddle() {
    doConstructorTest """\
class Foo {
    def foo() {}

    <caret>

    def bar() {}
}
""", """\
class Foo {
    def foo() {}

    Foo() {
    }

    def bar() {}
}
"""
  }

  void testConstructorWithOptionalParameter() {
    doConstructorTest('''\
class Base {
  Base(int x = 0){}
}

class Inheritor extends Base {
  <caret>
}
''', '''\
class Base {
  Base(int x = 0){}
}

class Inheritor extends Base {
    Inheritor(int x) {
        super(x)
    }
}
''')
  }

  void testGetterInTheEnd() {
    myFixture.configureByText 'a.groovy', '''
class GrImportStatementStub {
    private final String myAlias;
    private final String mySymbolName;

    protected GrImportStatementStub(String symbolName, String alias) {
    }
    <caret>
}
'''
    generateGetter()

    myFixture.checkResult '''
class GrImportStatementStub {
    private final String myAlias;
    private final String mySymbolName;

    protected GrImportStatementStub(String symbolName, String alias) {
    }

    String getMyAlias() {
        return myAlias
    }

    String getMySymbolName() {
        return mySymbolName
    }
}
'''

  }

  private void generateGetter() {
    //noinspection GroovyResultOfObjectAllocationIgnored
    new GroovyGenerateGetterSetterAction() //don't remove it!!!
    new WriteCommandAction(project, PsiFile.EMPTY_ARRAY) {
      protected void run(@NotNull Result result) throws Throwable {
        new GenerateGetterHandler() {
          @Nullable
          protected ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmptySelection, boolean copyJavadocCheckbox, Project project, Editor editor) {
            return members
          }
        }.invoke(project, myFixture.editor, myFixture.file);
        UIUtil.dispatchAllInvocationEvents()
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      }
    }.execute()
  }

  private void generateSetter() {
    //noinspection GroovyResultOfObjectAllocationIgnored
    new GroovyGenerateGetterSetterAction() //don't remove it!!!
    new WriteCommandAction(project, PsiFile.EMPTY_ARRAY) {
      protected void run(@NotNull Result result) throws Throwable {
        new GenerateSetterHandler() {
          @Nullable
          protected ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmptySelection, boolean copyJavadocCheckbox, Project project, Editor editor) {
            return members
          }
        }.invoke(project, myFixture.editor, myFixture.file);
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      }
    }.execute()
  }

  private void doConstructorTest(String before = null, String after = null) {
    if (before != null) {
      myFixture.configureByText('_a.groovy', before)
    }
    else {
      myFixture.configureByFile(getTestName(false) + ".groovy");
    }
    generateConstructor();
    if (after != null) {
      myFixture.checkResult(after)
    }
    else {
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
  }

  private RunResult generateConstructor(boolean javaHandler = false) {
    GenerateMembersHandlerBase handler
    if (javaHandler) {
      handler = new GenerateConstructorHandler() {
        @Override
        protected ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmptySelection, boolean copyJavadocCheckbox, Project project, Editor editor) {
          return members;
        }
      }
    }
    else {
      handler = new GroovyGenerateConstructorHandler() {
        @Override
        protected ClassMember[] chooseOriginalMembersImpl(PsiClass aClass, Project project) {
          List<ClassMember> members = aClass.fields.collect { new PsiFieldMember(it) }
          members << new PsiMethodMember(aClass.superClass.constructors[0])
          return members as ClassMember[]
        }
      }
    }

    return new WriteCommandAction(project, new PsiFile[0]) {
      protected void run(@NotNull Result result) throws Throwable {
        handler.invoke(project, myFixture.editor, myFixture.file);
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      }
    }.execute()
  }

  final String basePath = TestUtils.testDataPath + "generate"
}
