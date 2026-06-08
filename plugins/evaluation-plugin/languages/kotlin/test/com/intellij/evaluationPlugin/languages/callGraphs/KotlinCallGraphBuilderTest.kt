package com.intellij.evaluationPlugin.languages.callGraphs

import com.intellij.evaluationPlugin.languages.kotlin.callGraphs.KotlinCallGraphBuilder
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class KotlinCallGraphBuilderTest(private val scenario: String) : BasePlatformTestCase() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<String>> {
      val base = File(getStaticTestDataPath(), "callGraphs")
      val scenarios = base.listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList()
      return scenarios.sorted().map { arrayOf(it) }
    }

    fun getStaticTestDataPath(): String {
      return getCommunityPath().replace(File.separatorChar, '/') + "/plugins/evaluation-plugin/languages/kotlin/testData"
    }
  }

  override fun getTestDataPath() = getStaticTestDataPath()

  @Test
  fun testCallGraphAgainstExpected() {
    CallGraphTestUtil.doTestGeneratedGraphEqualsExpected(
      scenario,
      "callGraphs/${scenario}",
      myFixture,
      KotlinCallGraphBuilder()
    )
  }
}
