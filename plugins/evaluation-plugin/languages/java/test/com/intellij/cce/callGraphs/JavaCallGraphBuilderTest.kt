package com.intellij.cce.callGraphs

import com.intellij.cce.java.callGraphs.JavaCallGraphBuilder
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class JavaCallGraphBuilderTest(private val scenario: String) : BasePlatformTestCase() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<String>> {
      val base = File(getStaticTestDataPath(), "callGraphs")
      val scenarios = base.listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList()
      return scenarios.sorted().map { arrayOf(it) }
    }

    fun getStaticTestDataPath(): String {
      return getCommunityPath().replace(File.separatorChar, '/') + "/plugins/evaluation-plugin/languages/java/testData"
    }
  }

  override fun getTestDataPath(): String = getStaticTestDataPath()


  @Test
  fun testCallGraphAgainstExpected() {
    CallGraphTestUtil.doTestGeneratedGraphEqualsExpected(
      scenario,
      myFixture,
      JavaCallGraphBuilder(),
    )
  }
}