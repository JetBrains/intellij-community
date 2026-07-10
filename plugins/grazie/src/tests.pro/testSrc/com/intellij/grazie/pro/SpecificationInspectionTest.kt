package com.intellij.grazie.pro

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.LlmContradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.Replacement
import com.google.gson.JsonObject
import com.intellij.grazie.ide.language.markdown.semantics.inspection.SpecificationBaseInspection
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SpecificationInspectionTest : BaseTestCase() {
  @BeforeEach
  fun setUp() {
    Registry.get("grazie.specification.semantics.enabled").setValue(true, testRootDisposable)
    GrazieTestUtil.registerGrazieCloudConnectorWithQuota(testRootDisposable)
  }

  @Test
  fun `test regular Markdown files are not checked`() {
    myFixture.enableInspections(SpecificationTestInspection(emptyList<TestIssue>()))
    myFixture.configureByText("README.md", "This is some specification")
    myFixture.checkHighlighting()
  }

  @Test
  fun `test issues without ranges are not reported`() {
    myFixture.enableInspections(
      SpecificationTestInspection(
        listOf(TestIssue("Use `the` instead of `some`", -1, -1, emptyList()))
      )
    )
    myFixture.configureByText("AGENT.md", "This is some specification")
    myFixture.checkHighlighting()
  }

  @Test
  fun `test issue without quick fixes`() {
    myFixture.enableInspections(
      SpecificationTestInspection(
        listOf(TestIssue("Use `the` instead of `some`", 8, 12, emptyList()))
      )
    )
    myFixture.configureByText("AGENT.md", "This is <warning descr=\"Use `the` instead of `some`\">some</warning> specification")
    myFixture.checkHighlighting()
  }

  @Test
  fun `test quick fixes`() {
    myFixture.enableInspections(
      SpecificationTestInspection(
        listOf(TestIssue("Use `the` instead of `some`", 8, 12, listOf("the")))
      )
    )
    myFixture.configureByText("AGENT.md", "This is <warning descr=\"Use `the` instead of `some`\"><caret>some</warning> specification")
    myFixture.checkHighlighting()
    myFixture.launchAction(findSingleIntention("the"))
    myFixture.checkResult("This is the specification")
  }

  @Test
  fun `test remove quick fix`() {
    myFixture.enableInspections(
      SpecificationTestInspection(
        listOf(TestIssue("Security issues", 8, 17, listOf("")))
      )
    )
    myFixture.configureByText("AGENT.md", "This is <warning descr=\"Security issues\"><caret>security </warning>issue")
    myFixture.checkHighlighting()
    myFixture.launchAction(findSingleIntention("Remove"))
    myFixture.checkResult("This is issue")
  }

  @Test
  fun `test contradiction is mirrored`() {
    val text = """
      - This is beautiful text
      - This is awful text
    """.trimIndent()

    val file = myFixture.configureByText("AGENT.md", """
      - This is <warning descr="Contradicts to awful">beautiful</warning> text
      - This is <warning descr="This statement contradicts another requirement">awful</warning> text
    """.trimIndent())

    val startOffset = text.indexOf("beautiful")
    val endOffset = startOffset + "beautiful".length
    val issue = Contradiction(
      "NOT_USED", "NOT_USED", "Contradicts to awful",
      startOffset, endOffset,
      text.indexOf("awful"), text.indexOf("awful") + "awful".length,
      emptyList<String>(), file.virtualFile.path, file.virtualFile.path
    )

    myFixture.enableInspections(SpecificationTestInspection(listOf(LlmContradiction(issue, startOffset, endOffset))))
    myFixture.checkHighlighting()
  }

  internal class SpecificationTestInspection<T>(private val issues: List<LlmIssue<T>>) : SpecificationBaseInspection<T>() {
    override fun getAnalyzer(file: PsiFile): LlmAnalyzer<T> = TestLlmAnalyzer(issues)
  }

  internal class TestLlmAnalyzer<T>(private val issues: List<LlmIssue<T>>): LlmAnalyzer<T>() {
    override fun analyze(text: String, client: SuspendableAPIGatewayClient): WithSpending<List<LlmIssue<T>>> =
      WithSpending(issues, 0.0)

    // Those methods are not required because `analyze` is overridden
    override fun getAnalysisPrompt(): String = TODO("Not used")
    override fun parseItem(obj: JsonObject): LlmIssue<T> = TODO("Not used")
  }

  internal class TestIssue(
    val issueMessage: String,
    val issueStartOffset: Int,
    val issueEndOffset: Int,
    val issueReplacements: List<String>,
  ) : LlmIssue<TestIssue> {
    override fun getText(): String = TODO("Not used")
    override fun getMessage(): String = issueMessage
    override fun getIssue(): TestIssue = this
    override fun startOffset(): Int = issueStartOffset
    override fun endOffset(): Int = issueEndOffset
    override fun getReplacements(): List<Replacement> = issueReplacements.map { Replacement(it) }
    override fun withOffsets(startOffset: Int, endOffset: Int): LlmIssue<TestIssue> =
      TestIssue(issueMessage, startOffset, endOffset, issueReplacements)
  }
}
