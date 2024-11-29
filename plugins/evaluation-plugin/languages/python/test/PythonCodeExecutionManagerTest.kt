import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.cce.python.execution.manager.PythonCodeExecutionManager
import com.intellij.openapi.project.DefaultProjectFactoryImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class PythonCodeExecutionManagerTest : BasePlatformTestCase() {
  // Data class to represent the structure of test samples.
  private data class TestSample(
    val pythonClass: String,
    val pythonTest: String,
    val fileProvided: Boolean
  )

  private var manager = PythonCodeExecutionManager()

  // List of test samples loaded from a JSON file.
  private val testSamples: List<TestSample> = Gson().fromJson(
    File("testData/test_samples.json").readText(),
    object : TypeToken<List<TestSample>>() {}.type
  )

  private val target = "target"

  fun `test no project base path found`() {
    super.setUp()

    for (testSample in testSamples) {
      val log = manager.compileAndExecute(DefaultProjectFactoryImpl().defaultProject, testSample.pythonClass, target)
      assertEquals("No project base path found", log.error)
    }
  }

  fun `test no bash script`() {
    super.setUp()

    var path = project.basePath

    // Create a directory for test files.
    File("$path/tests").mkdirs()

    for (testSample in testSamples) {
      // Extract the class name from the Python class definition.
      val className = testSample.pythonClass.lines().first().split(" ").last()

      // Write the Python class to a file in the project directory.
      File("$path/$className.py").writeText(testSample.pythonClass)

      val log = manager.compileAndExecute(project, testSample.pythonTest, className)

      assertEquals(-1, log.exitCode)
      assertEquals("Bash script file not found", log.error)
    }
  }

  // Test method to verify behavior when the Python SDK is not set.
  fun `test sdk is null`() {
    super.setUp()

    var path = project.basePath

    // Create a directory for test files.
    File("$path/tests").mkdirs()

    for (testSample in testSamples) {
      // Extract the class name from the Python class definition.
      val className = testSample.pythonClass.lines().first().split(" ").last()

      // Write the Python class to a file in the project directory.
      File("$path/$className.py").writeText(testSample.pythonClass)

      // Write Bash scripts required for execution.
      File("$path/setup_test.sh").writeText("#!/bin/bash\n")
      File("$path/run_tests.sh").writeText("#!/bin/bash\n")

      val log = manager.compileAndExecute(project, testSample.pythonTest, className)

      assertEquals(-1, log.exitCode)
      assertEquals("Python SDK not found", log.error)
    }
  }
}
