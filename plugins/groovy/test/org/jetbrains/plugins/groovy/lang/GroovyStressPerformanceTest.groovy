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
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager

/**
 * @author peter
 */
class GroovyStressPerformanceTest extends LightGroovyTestCase {

  final String basePath = ''

  ThrowableRunnable configureAndHighlight(String text) {
    return {
      myFixture.configureByText 'a.groovy', text
      myFixture.doHighlighting()
    } as ThrowableRunnable
  }

  public void testDontWalkLongInferenceChain() throws Exception {
    //RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    Map<Integer, PsiClass> classes = [:]
    myFixture.addFileToProject "Foo0.groovy", """class Foo0 {
      def foo() { return 0 }
    }"""
    def max = 100
    for (i in 1..max) {
      def file = myFixture.addFileToProject("Foo${i}.groovy", """class Foo$i {
        def foo() { return Foo${i - 1}.foo() }
      }""")
      classes[i] = (file as GroovyFile).classes[0]
    }

    def deepFile = myFixture.addFileToProject("DeepTest.groovy", "def test() { return Foo${max}.foo() }") as GroovyFile
    assert Object.name ==  inferredType(deepFile.scriptClass, 'test')

    def shallowFile = myFixture.addFileToProject("ShallowTest.groovy", "def test() { return Foo2.foo() }") as GroovyFile
    assert Integer.name == inferredType(shallowFile.scriptClass, 'test')

    int border = (1..max).find { int i ->
      GroovyPsiManager.getInstance(project).dropTypesCache()
      return inferredType(classes[i], 'foo') == Object.name
    }

    assert border

    GroovyPsiManager.getInstance(project).dropTypesCache()
    assert inferredType(classes[border], 'foo') == Object.name
    assert inferredType(classes[border - 1], 'foo') == Integer.name
  }

  private static String inferredType(PsiClass clazz, String method) {
    final grMethod = clazz.findMethodsByName(method, false)[0] as GrMethod
    grMethod.inferredReturnType.canonicalText
  }


  public void testQuickIncrementalReparse() {
    def story = '''scenario {
  given "some precondition", {
    // do something
  }
  when "I do some stuff", {
    // foo bar code
  }
  then "something I expect happens", {
    // some verification
  }
}
'''
    myFixture.configureByText 'a.groovy', story * 200 + "<caret>"
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    myFixture.type 'foo {}\n'
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    def start = System.currentTimeMillis()

    story.toCharArray().each {
      myFixture.type it
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    IdeaTestUtil.assertTiming "slow", 10000, (System.currentTimeMillis() - start)
  }

  public void testManyAnnotatedFields() {
    String text = "class Foo {\n"
    for (i in 1..10) {
      text += "@Deprecated String foo$i\n"
    }
    text += "}"

    measureHighlighting(text, 5000)
  }

  private void measureHighlighting(String text, int time) {
    IdeaTestUtil.startPerformanceTest("slow", time, configureAndHighlight(text)).cpuBound().usesAllCPUCores().assertTiming()
  }

  public void testDeeplyNestedClosures() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    String text = "println 'hi'"
    String defs = ""
    for (i in 1..10) {
      text = "foo$i { $text }"
      defs += "def foo$i(Closure cl) {}\n"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    measureHighlighting(defs + text, 10000)
  }

  public void testDeeplyNestedClosuresInGenericCalls() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    String text = "println it"
    for (i in 1..10) {
      text = "foo(it) { $text }"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    measureHighlighting("def <T> foo(T t, Closure cl) {}\n" + text, 10000)
  }

  public void testDeeplyNestedClosuresInGenericCalls2() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    String text = "println it"
    for (i in 1..10) {
      text = "foo(it) { $text }"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    measureHighlighting("def <T> foo(T t, Closure<T> cl) {}\n" + text, 10000)
  }

  public void testManyAnnotatedScriptVariables() {
    measureHighlighting((0..100).collect { "@Anno String i$it = null" }.join("\n"), 10000)
  }

  public void "test no recursion prevention when resolving supertype"() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    myFixture.addClass("interface Bar {}")
    measureHighlighting("class Foo implements Bar {}", 200)
  }

  public void "test no recursion prevention when contributing constructors"() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    myFixture.addClass("interface Bar {}")
    def text = """
@groovy.transform.TupleConstructor
class Foo implements Bar {
  int a
  Foo b
  int getBar() {}
  void setBar(int bar) {}
  void someMethod(int a = 1) {}
}"""
    measureHighlighting(text, 200)
  }

  public void "test using non-reassigned for loop parameters"() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    def text = """
def foo(List<File> list) {
  for (file in list) {
${
"   println bar(file)\n" * 100
}
  }
}
def bar(File file) { file.path }
"""
    measureHighlighting(text, 2000)
  }

  public void "test using SSA variables in a for loop"() {
    def text = """
def foo(List<String> list, SomeClass sc) {
  List<String> result
  for (s in list) {
${
'''
    bar(s, result)
    bar2(s, result, sc)
    bar3(foo:s, bar:result, sc)
    sc.someMethod(s)
''' * 100
    }
  }
}
def bar(String s, List<String> result) { result << s }
def bar2(String s, List<String> result) { result << s }
def bar2(int s, List<String> result, SomeClass sc) { result << s as String }
def bar3(Map args, List<String> result, SomeClass sc) { result << s as String }

class SomeClass {
  void someMethod(String s) {}
}
"""
    measureHighlighting(text, 8000)
  }

  public void "test infer only the variable types that are needed"() {
    addGdsl '''contribute(currentType(String.name)) {
  println 'sleeping'
  Thread.sleep(1000)
  method name:'foo', type:String, params:[:], namedParams:[
    parameter(name:'param1', type:String),
  ]
}'''
    def text = '''
  String s = "abc"
while (true) {
  s = "str".foo(s)
  File f = new File('path')
  f.canoPath<caret>
}
'''
    IdeaTestUtil.startPerformanceTest("slow", 300, configureAndComplete(text)).cpuBound().usesAllCPUCores().assertTiming()
  }

  ThrowableRunnable configureAndComplete(String text) {
    return {
      myFixture.configureByText 'a.groovy', text
      myFixture.completeBasic()
    } as ThrowableRunnable
  }

  private def addGdsl(String text) {
    final PsiFile file = myFixture.addFileToProject("Enhancer.gdsl", text)
    GroovyDslFileIndex.activateUntilModification(file.virtualFile)
  }

}
