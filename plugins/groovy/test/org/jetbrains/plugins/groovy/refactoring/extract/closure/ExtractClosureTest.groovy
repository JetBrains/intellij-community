/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.extract.closure;


import com.intellij.psi.PsiElement
import gnu.trove.TIntArrayList
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
public class ExtractClosureTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}groovy/refactoring/extractMethod/";
  }


  private void doTest(String before, String after) {
    doTest(before, after, [], [])
  }

  private void doTest(String before, String after, List<Integer> toRemove, List<Integer> notToUseAsParams) {
    myFixture.configureByText 'a.groovy', before
    def model = myFixture.editor.selectionModel
    def handler = new ExtractClosureHandler() {
      @Override
      protected ExtractClosureHelper getSettings(@NotNull InitialInfo initialInfo, GrParametersOwner owner, PsiElement toSearchFor) {
        def settings = new ExtractClosureHelper(initialInfo, owner, toSearchFor, "closure", true)
        settings.setDeclareFinal(false)
        settings.setGenerateDelegate(false)
        settings.setName("closure")
        settings.setToRemove(new TIntArrayList(toRemove as int[]))

        def infos = settings.parameterInfos
        for (int i: notToUseAsParams) {
          infos[i].setPassAsParameter(false)
        }
        return settings
      }
    }
    
    handler.invoke myFixture.project, myFixture.editor, myFixture.file, model.selectionStart, model.selectionEnd
    myFixture.checkResult after
  }


  void testSimple() {
    doTest('''
def foo(String s) {
    s+=2
    <selection>print s</selection>
}

foo('a')
''', '''
def foo(String s, Closure closure) {
    s+=2
    <selection>closure(s)</selection>
}

foo('a') {String s ->
    print s
}
''')
  }

  void testRemoveUnused() {
    doTest('''
class X {
    def foo(String s) {
        <selection>print s</selection>
    }
}

new X().foo('a')
''', '''
class X {
    def foo(Closure closure) {
        <selection>closure()</selection>
    }
}

new X().foo {->
    print 'a'
}
''', [0], [0])

  }

  void testRemoveUnusedAndGenerateLocal() {
    doTest('''
def foo(String s) {
    <selection>s+=2
    print s</selection>
}

foo('a')
''', '''
def foo(Closure closure) {
    closure()
}

foo {->
    String s = 'a'
    s += 2
    print s
}
''', [0], [0])
  }

  void testInsertQualifier() {
    doTest('''
class X {
    def foo(String s) {
        <selection>bar()</selection>
    }
    def bar(){}
}

new X().foo('a')
''', '''
class X {
    def foo(Closure closure) {
        <selection>closure()</selection>
    }
    def bar(){}
}

final X x = new X()
x.foo {->
    x.bar()
}
''', [0], [])

  }
}
