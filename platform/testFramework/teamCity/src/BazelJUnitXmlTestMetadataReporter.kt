// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.teamCity

import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.Path

@ApiStatus.Internal
object BazelJUnitXmlTestMetadataReporter {

  private const val JAVA_RUNFILES_ENV = "JAVA_RUNFILES"
  private const val DEFAULT_METADATA_NAME = "Logs"
  private const val TEST_XML_FILE_NAME = "test.xml"
  private const val TEST_LOG_FILE_NAME = "test.log"

  enum class TestNameFormat(private val optionName: String) {
    NAME("name") {
      override fun format(testCase: JUnitXmlTestCase): String = testCase.name
    },
    CLASSNAME_AND_NAME("classname.name") {
      override fun format(testCase: JUnitXmlTestCase): String {
        return testCase.className?.let { cn -> "$cn.${testCase.name}" } ?: testCase.name
      }
    };

    abstract fun format(testCase: JUnitXmlTestCase): String

    override fun toString(): String = optionName

    companion object {
      fun parse(value: String): TestNameFormat =
        entries.firstOrNull { it.optionName == value }
        ?: error("Unsupported test name format '$value'. Supported values: ${entries.joinToString()}")
    }
  }

  data class JUnitXmlTestCase(
    val name: String,
    val className: String?,
  )

  data class JUnitXmlReport(val path: Path, val testCases: List<JUnitXmlTestCase>)
  data class BazelTestReports(val testLogsPath: Path, val junitXmlReports: List<JUnitXmlReport>)

  fun interface BazelTestReportsWorkflow {
    fun run(testReports: BazelTestReports)
  }

  data class MetadataScenario(
    val metadataName: String,
    val metadataType: TeamCityReporter.MetadataType,
    val metadataValue: (junitXmlPath: Path, testCase: JUnitXmlTestCase) -> String,
  )

  class TeamCityMetadataWorkflow(
    private val scenario: MetadataScenario,
    private val testNameFormat: TestNameFormat = TestNameFormat.CLASSNAME_AND_NAME,
  ) : BazelTestReportsWorkflow {
    override fun run(testReports: BazelTestReports) {
      for (report in testReports.junitXmlReports) {
        for (testCase in report.testCases) {
          TeamCityReporter.reportTestMetadata(
            testName = testNameFormat.format(testCase),
            value = scenario.metadataValue(report.path, testCase),
            name = scenario.metadataName,
            type = scenario.metadataType,
          )
        }
      }
    }
  }

  fun createBazelTestLogMetadataWorkflow(
    reportedPathBase: String,
    metadataName: String = DEFAULT_METADATA_NAME,
    testNameFormat: TestNameFormat = TestNameFormat.CLASSNAME_AND_NAME,
  ): BazelTestReportsWorkflow =
    TeamCityMetadataWorkflow(
      scenario = MetadataScenario(
        metadataName = metadataName,
        metadataType = TeamCityReporter.MetadataType.ARTIFACT,
        metadataValue = { junitXmlPath, _ ->
          reportedArtifactPath(junitXmlPath.resolveSibling(TEST_LOG_FILE_NAME), reportedPathBase)
        },
      ),
      testNameFormat = testNameFormat,
    )

  @JvmStatic
  fun main(args: Array<String>) {
    runToolNonFatal(args = args)
  }

  fun runTool(args: Array<String>, javaRunfilesPath: Path) {
    val options = parseOptions(args)
    runBazelTestReportsWorkflow(
      javaRunfilesPath = javaRunfilesPath,
      workflow = options.workflow.createWorkflow(options),
    )
  }

  fun runToolNonFatal(args: Array<String>, javaRunfilesPath: () -> Path): Boolean {
    try {
      runTool(args = args, javaRunfilesPath = javaRunfilesPath())
      return true
    }
    catch (t: Exception) {
      reportNonFatalFailure(t)
      return false
    }
  }

  private fun runToolNonFatal(args: Array<String>): Boolean =
    runToolNonFatal(args, javaRunfilesPath = ::getJavaRunfilesPath)

  private fun reportNonFatalFailure(t: Throwable) {
    println(TeamCityReporter.serviceMessage("message", mapOf(
      "text" to "Failed to report Bazel JUnit XML test metadata: ${t.message ?: t.javaClass.name}",
      "status" to "WARNING",
      "errorDetails" to t.stackTraceToString(),
    )))
  }

  fun runBazelTestReportsWorkflow(javaRunfilesPath: Path, workflow: BazelTestReportsWorkflow) {
    workflow.run(readBazelTestReports(resolveBazelTestLogsPath(javaRunfilesPath)))
  }

  fun findJUnitXmlReports(bazelTestLogsPath: Path): List<Path> =
    Files.walk(bazelTestLogsPath).use { paths ->
      paths
        .filter { Files.isRegularFile(it) && it.fileName.toString() == TEST_XML_FILE_NAME }
        .sorted()
        .toList()
    }

  fun readBazelTestReports(bazelTestLogsPath: Path): BazelTestReports =
    BazelTestReports(testLogsPath = bazelTestLogsPath, junitXmlReports = readJUnitXmlReports(bazelTestLogsPath))

  fun readJUnitXmlReports(bazelTestLogsPath: Path): List<JUnitXmlReport> =
    findJUnitXmlReports(bazelTestLogsPath).map { readJUnitXmlReport(it) }

  fun reportedArtifactPath(artifactPath: Path, reportedPathBase: String): String =
    "$reportedPathBase/${artifactPathInsideArchive(artifactPath).toPortablePath()}"

  fun resolveBazelTestLogsPath(javaRunfilesPath: Path): Path {
    return javaRunfilesPath.toRealPath().bazelConfigurationOutputPath().resolve("testlogs").toRealPath()
  }

  private fun getJavaRunfilesPath(): Path =
    Path(System.getenv(JAVA_RUNFILES_ENV) ?: error("${JAVA_RUNFILES_ENV} environment variable is not set"))

  private fun Path.bazelConfigurationOutputPath(): Path {
    val normalizedPath = toAbsolutePath().normalize()
    val bazelOutIndex = (0 until normalizedPath.nameCount).firstOrNull { normalizedPath.getName(it).toString() == "bazel-out" }
                        ?: error("Cannot resolve Bazel execroot from JAVA_RUNFILES=$this")
    if (bazelOutIndex + 1 >= normalizedPath.nameCount) {
      error("Cannot resolve Bazel configuration output path from JAVA_RUNFILES=$this")
    }
    return normalizedPath.prefix(bazelOutIndex + 2)
  }

  private fun Path.prefix(endExclusive: Int): Path {
    val relativePrefix = subpath(0, endExclusive)
    return root?.resolve(relativePrefix) ?: relativePrefix
  }

  private fun artifactPathInsideArchive(artifactPath: Path): Path {
    val normalizedPath = artifactPath.toAbsolutePath().normalize()
    val execrootIndex = (0 until normalizedPath.nameCount).firstOrNull { normalizedPath.getName(it).toString() == "execroot" }
    if (execrootIndex != null && execrootIndex > 0) {
      return normalizedPath.subpath(execrootIndex - 1, normalizedPath.nameCount)
    }
    return normalizedPath.fileName
  }

  private fun Path.toPortablePath(): String =
    joinToString("/") { it.toString() }

  fun readJUnitXmlReport(junitXmlPath: Path): JUnitXmlReport {
    val document = Files.newInputStream(junitXmlPath).use { documentBuilderFactory().newDocumentBuilder().parse(it) }
    return JUnitXmlReport(path = junitXmlPath, testCases = readTestCases(document.documentElement))
  }

  fun readTestCases(junitXmlPath: Path): List<JUnitXmlTestCase> =
    readJUnitXmlReport(junitXmlPath).testCases

  private fun readTestCases(rootElement: Element): List<JUnitXmlTestCase> {
    val suites = rootElement.getElementsByTagName("testsuite")
    return buildList {
      for (suiteIndex in 0 until suites.length) {
        val suiteElement = suites.item(suiteIndex) as? Element ?: continue
        for (testCase in suiteElement.childElements("testcase")) {
          val name = testCase.nonBlankAttribute("name") ?: continue
          val className = testCase.nonBlankAttribute("classname")
          add(JUnitXmlTestCase(name = name, className = className))
        }
      }
    }
  }

  private fun Element.nonBlankAttribute(name: String): String? =
    getAttribute(name).takeIf { it.isNotBlank() }

  private fun Element.childElements(tagName: String): Sequence<Element> =
    (0 until childNodes.length).asSequence()
      .mapNotNull { childNodes.item(it) as? Element }
      .filter { it.tagName == tagName }

  @Suppress("HttpUrlsUsage")
  private fun documentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
      setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      isExpandEntityReferences = false
    }

  private enum class CommandLineWorkflow(private val optionName: String) {
    BAZEL_TEST_LOG_METADATA("bazel-test-log-metadata") {
      override fun createWorkflow(options: Options): BazelTestReportsWorkflow {
        val reportedPathBase = options.reportedPathBase ?: error("Missing required argument for workflow '$this': --logs-base")
        return createBazelTestLogMetadataWorkflow(
          reportedPathBase = reportedPathBase,
          metadataName = options.metadataName,
          testNameFormat = options.testNameFormat,
        )
      }
    };

    abstract fun createWorkflow(options: Options): BazelTestReportsWorkflow

    override fun toString(): String = optionName

    companion object {
      fun parse(value: String): CommandLineWorkflow =
        entries.firstOrNull { it.optionName == value }
        ?: error("Unsupported workflow '$value'. Supported values: ${entries.joinToString()}")
    }
  }

  private data class Options(
    val workflow: CommandLineWorkflow,
    val reportedPathBase: String?,
    val metadataName: String,
    val testNameFormat: TestNameFormat,
  )

  private fun parseOptions(args: Array<String>): Options {
    if (args.contains("--help")) {
      printUsage()
      kotlin.system.exitProcess(0)
    }

    var reportedPathBase: String? = null
    var workflow: CommandLineWorkflow? = null
    var metadataName = DEFAULT_METADATA_NAME
    var testNameFormat = TestNameFormat.CLASSNAME_AND_NAME

    var i = 0
    while (i < args.size) {
      val option = args[i]
      fun readValue(): String {
        i++
        return args.getOrNull(i) ?: error("Missing value for $option")
      }

      when (option) {
        "--workflow" -> workflow = CommandLineWorkflow.parse(readValue())
        "--logs-base" -> reportedPathBase = readValue()
        "--metadata-name" -> metadataName = readValue()
        "--test-name-format" -> testNameFormat = TestNameFormat.parse(readValue())
        else -> error("Unknown argument: $option")
      }
      i++
    }

    return Options(
      workflow = workflow ?: error("Missing required argument: --workflow"),
      reportedPathBase = reportedPathBase,
      metadataName = metadataName,
      testNameFormat = testNameFormat,
    )
  }

  private fun printUsage() {
    System.err.println(
      """
      Usage: report_junit_xml_test_metadata_build_target --workflow <workflow> [options]

      Options:
        --workflow <workflow>            Workflow to run. Supported values: ${CommandLineWorkflow.entries.joinToString()}
        --logs-base <artifact>           Required for bazel-test-log-metadata. Artifact path base for reported test.log links
        --metadata-name <name>           Metadata label shown by TeamCity. Default: Logs
        --test-name-format <format>      name or classname.name. Default: classname.name
      """.trimIndent()
    )
  }
}
