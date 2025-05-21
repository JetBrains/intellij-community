package com.intellij.cce.testrunner


import com.intellij.cce.test.TestRunResult
import com.intellij.cce.java.test.MavenOutputParser
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class MavenOutputParserTest(private val fileName: String) : BasePlatformTestCase() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "Test file \"{0}\"")
    fun data() = arrayOf("test1")
  }

  override fun getTestDataPath(): String? {
    return getCommunityPath().replace(File.separatorChar, '/') + "/plugins/evaluation-plugin/languages/java/testData/com/intellij/cce/testRunner/maven"
  }

  @Test
  fun doTest() {
    val file = File(testDataPath, fileName)

    val content = file.readText()

    val parser = MavenOutputParser()
    val result = parser.parse(content)
    checkResult(dump(result))
  }

  // copypast from ActionsProcessorTest.
  // todo extract to common code
  private fun checkResult(result: String) {
    val goldFile = File(testDataPath, "${fileName}_gold")
    if (!goldFile.exists()) {
      val tempFile = File("${goldFile.absolutePath}_temp")
      tempFile.createNewFile()
      tempFile.writeText(result)
    }
    assert(goldFile.exists()) { "gold file does not exist" }
    val goldText = goldFile.readText()
    assertTextEquals(goldText, result)
  }
}

private fun dump(result: TestRunResult): String {
  val sb = StringBuilder()
  sb.appendLine("passed: ${result.passed.size}")
  result.passed.forEach {
    sb.appendLine("\t$it")
  }
  sb.appendLine("failed: ${result.failed.size}")
  result.failed.forEach {
    sb.appendLine("\t$it")
  }

  return sb.toString()
}