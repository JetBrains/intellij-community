package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.annotations.NotNull

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

}
