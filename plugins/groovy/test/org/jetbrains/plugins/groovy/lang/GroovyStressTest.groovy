package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.annotations.NotNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.IdeaTestUtil

/**
 * @author peter
 */
class GroovyStressTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override protected LightProjectDescriptor getProjectDescriptor() {
    LightGroovyTestCase.GROOVY_DESCRIPTOR
  }

  public void testDontWalkLongInferenceChain() throws Exception {
    myFixture.addFileToProject "Foo0.groovy", """class Foo0 {
      def foo() { return 0 }
    }"""
    def max = 100
    for (i in 1..max) {
      myFixture.addFileToProject "Foo${i}.groovy", """class Foo$i {
        def foo() { return Foo${i-1}.foo() }
      }"""
    }

    def deepFile = myFixture.addFileToProject("DeepTest.groovy", "def test() { return Foo${max}.foo() }") as GroovyFile
    assert Object.name == (deepFile.scriptClass.findMethodsByName("test", false)[0] as GrMethod).inferredReturnType.canonicalText

    def shallowFile = myFixture.addFileToProject("ShallowTest.groovy", "def test() { return Foo2.foo() }") as GroovyFile
    assert Integer.name == (shallowFile.scriptClass.findMethodsByName("test", false)[0] as GrMethod).inferredReturnType.canonicalText

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

    def start = System.currentTimeMillis()

    story.toCharArray().each {
      myFixture.type it
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    IdeaTestUtil.assertTiming "slow", 10000, (System.currentTimeMillis() - start)
  }

}
