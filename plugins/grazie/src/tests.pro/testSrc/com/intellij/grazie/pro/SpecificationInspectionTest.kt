package com.intellij.grazie.pro

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
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
    myFixture.enableInspections(SpecificationTestInspection(emptyList()))
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

  internal class SpecificationTestInspection(private val issues: List<LlmIssue<TestIssue>>) : SpecificationBaseInspection<TestIssue>() {
    override fun getAnalyzer(file: PsiFile): LlmAnalyzer<TestIssue> = TestLlmAnalyzer(issues)
  }

  internal class TestLlmAnalyzer(private val issues: List<LlmIssue<TestIssue>>): LlmAnalyzer<TestIssue>() {
    override fun analyze(text: String, client: SuspendableAPIGatewayClient): WithSpending<List<LlmIssue<TestIssue>>> =
      WithSpending(issues, 0.0)

    // Those methods are not required because `analyze` is overridden
    override fun getAnalysisPrompt(): String = TODO("Not used")
    override fun parseItem(obj: JsonObject): LlmIssue<TestIssue> = TODO("Not used")
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
