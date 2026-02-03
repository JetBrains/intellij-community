package com.intellij.cce.testrunner


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
    fun data() = arrayOf(
      "test1",
      "fasterxml__jackson-core-370",
      "fasterxml__jackson-core-380",
      "fasterxml__jackson-dataformat-xml-544",
      "fasterxml__jackson-dataformat-xml-590",
      "trinodb__trino-3638",
      "trinodb__trino-3638_failed"
      )
  }

  override fun getTestDataPath(): String? {
    return getCommunityPath().replace(File.separatorChar, '/') + "/plugins/evaluation-plugin/languages/java/testData/com/intellij/cce/testRunner/maven"
  }

  @Test
  fun doTest() {
    val file = File(testDataPath, fileName)

    val content = file.readText()

    val compilationSuccessful = MavenOutputParser.compilationSuccessful(content)
    val projectIsResolvable = MavenOutputParser.checkIfProjectIsResolvable(content)
    val (passed, failed) = MavenOutputParser.parse(content)
    checkResult(dump(compilationSuccessful, projectIsResolvable, passed, failed))
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

private fun dump(
  compilationSuccessful: Boolean,
  projectIsResolvable: Boolean,
  passed: List<String>,
  failed: List<String>,
): String {
  val sb = StringBuilder()
  sb.appendLine("compilationSuccessful: ${compilationSuccessful}")
  sb.appendLine("projectIsResolvable: ${projectIsResolvable}")
  sb.appendLine("passed: ${passed.size}")
  passed.forEach {
    sb.appendLine("\t$it")
  }
  sb.appendLine("failed: ${failed.size}")
  failed.forEach {
    sb.appendLine("\t$it")
  }

  return sb.toString()
}