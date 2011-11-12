package org.jetbrains.plugins.groovy.lang

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import com.intellij.util.ThrowableRunnable

/**
 * @author peter
 */
class GroovyStressTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override protected LightProjectDescriptor getProjectDescriptor() {
    LightGroovyTestCase.GROOVY_DESCRIPTOR
  }

  ThrowableRunnable configureAndHighlight(String text) {
    return {
      myFixture.configureByText 'a.groovy', text
      myFixture.doHighlighting()
    } as ThrowableRunnable
  }

  public void testDontWalkLongInferenceChain() throws Exception {
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
    String text = "println it"
    for (i in 1..10) {
      text = "foo(it) { $text }"
    }
    myFixture.enableInspections(new MissingReturnInspection())
    IdeaTestUtil.startPerformanceTest("slow", 10000, configureAndHighlight("def <T> foo(T t, Closure cl) {}\n" + text)).cpuBound().usesAllCPUCores().assertTiming()
  }

  public void testDeeplyNestedClosuresInGenericCalls2() {
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
}
