package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager

/**
 * @author peter
 */
class GroovyStressPerformanceTest extends LightGroovyTestCase {

  @Override
  protected String getBasePath() {''}

  ThrowableRunnable configureAndHighlight(String text) {
    return {
      myFixture.configureByText 'a.groovy', text
      myFixture.doHighlighting()
    } as ThrowableRunnable
  }

  public void testDontWalkLongInferenceChain() throws Exception {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
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

  String inferredType(PsiClass clazz, String method) {
    (clazz.findMethodsByName(method, false)[0] as GrMethod).inferredReturnType.canonicalText
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

    IdeaTestUtil.startPerformanceTest("slow", 5000, configureAndHighlight(text)).cpuBound().usesAllCPUCores().assertTiming()
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
    IdeaTestUtil.startPerformanceTest("slow", 10000, configureAndHighlight(defs + text)).cpuBound().usesAllCPUCores().assertTiming()
  }

  public void testDeeplyNestedClosuresInGenericCalls() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    String text = "println it"
    for (i in 1..10) {
      text = "foo(it) { $text }"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    IdeaTestUtil.startPerformanceTest("slow", 10000, configureAndHighlight("def <T> foo(T t, Closure cl) {}\n" + text)).cpuBound().usesAllCPUCores().assertTiming()
  }

  public void testDeeplyNestedClosuresInGenericCalls2() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    String text = "println it"
    for (i in 1..10) {
      text = "foo(it) { $text }"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    IdeaTestUtil.startPerformanceTest("slow", 10000, configureAndHighlight("def <T> foo(T t, Closure<T> cl) {}\n" + text)).cpuBound().usesAllCPUCores().assertTiming()

  }

  public void testManyAnnotatedScriptVariables() {
    IdeaTestUtil.startPerformanceTest("slow", 10000, configureAndHighlight((0..100).collect { "@Anno String i$it = null" }.join("\n"))).cpuBound().usesAllCPUCores().assertTiming()
  }

  public void "test no recursion prevention when resolving supertype"() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    myFixture.addClass("interface Bar {}")
    IdeaTestUtil.startPerformanceTest("slow", 200, configureAndHighlight("class Foo implements Bar {}")).cpuBound().usesAllCPUCores().assertTiming()
  }
}
