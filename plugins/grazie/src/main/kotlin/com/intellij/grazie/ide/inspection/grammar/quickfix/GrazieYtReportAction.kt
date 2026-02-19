package com.intellij.grazie.ide.inspection.grammar.quickfix

import ai.grazie.nlp.langs.Language
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.seemsCloudConnected
import com.intellij.grazie.mlec.MlecChecker
import com.intellij.grazie.spellcheck.TypoProblem
import com.intellij.grazie.text.GrazieProblem
import com.intellij.grazie.text.TextProblem
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import org.jetbrains.annotations.Nls
import java.net.URLEncoder
import javax.swing.Icon
import kotlin.math.abs

private const val NEW_ISSUE_URL = "https://youtrack.jetbrains.com/newissue"
private val SUPPORTED_LANGUAGES = setOf(Language.ENGLISH, Language.GERMAN)
private val TRIM_REGEX = Regex("^(?:\\s*\\n)+|(?:\\s*\\n)+$")
private val SUPPORTED_CLASSES = setOf(TypoProblem::class.java, GrazieProblem::class.java)

internal class GrazieYtReportAction(problem: TextProblem) : IntentionAndQuickFixAction(), Iconable {
  private val naturalLanguage: Language = problem.rule.language
  private val programmingLanguages = problem.text.containingFile.viewProvider.languages
  private val isCloud = seemsCloudConnected()
  private val message = problem.shortMessage
  private val clazz: Class<*> = problem.javaClass
  private val elementRange: SmartPsiFileRange
  private val textRange: SmartPsiFileRange

  init {
    val file = problem.text.containingFile
    val manager = SmartPointerManager.getInstance(file.project)
    elementRange = manager.createSmartPsiFileRangePointer(file, problem.text.commonParent.textRange)
    textRange = manager.createSmartPsiFileRangePointer(file, problem.text.rangesInFile.reduce(TextRange::union))
  }

  // maybe expensive, so invoke only if fix is applied
  private val title by lazy {
    GrazieBundle.message(
      "grazie.report.bug.${if (problem.isSpellingProblem) "spelling" else "grammar"}",
      words.joinToString(),
    )
  }
  private val words by lazy { problem.highlightRanges.map { it.substring(problem.text.toString()) } }
  @get:Nls
  private val problemKind by lazy {
    if (problem.isSpellingProblem) GrazieBundle.message("grazie.report.bug.spelling.name")
    else if (problem is MlecChecker.MlecProblem) problem.errorText
    else problem.rule.globalId
  }
  private val suggestions by lazy { problem.suggestions.map { it.presentableText } }


  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
  override fun getName(): @IntentionName String = familyName
  override fun getFamilyName(): @IntentionFamilyName String = GrazieBundle.message("grazie.report.bug.action.name")
  override fun getIcon(flags: Int): Icon = AllIcons.ToolbarDecorator.AddYouTrack
  override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
    val problemText = extractAndTrimText(editor) ?: return
    BrowserUtil.browse(generateReportURL(problemText))
  }
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
    editor != null &&
    clazz in SUPPORTED_CLASSES &&
    naturalLanguage in SUPPORTED_LANGUAGES &&
    programmingLanguages
      .mapNotNull { PluginManager.getPluginByClass(it.javaClass) }
      .let { it.isNotEmpty() && it.all { descriptor -> PluginManagerCore.isDevelopedByJetBrains(descriptor)} }

  private fun generateReportURL(problemText: String): String {
    val description = buildString {
      appendLine("**File's languages:** ${programmingLanguages.joinToString(", ") { it.displayName }}")
      appendLine("**Text:**\n```\n$problemText\n```")
      appendLine("**Highlighted words:** ${words.joinToString(" … ")}")
      appendLine("**Message:** $message")
      appendLine("**Suggestions:** ${suggestions.joinToString(", ")}")
      appendLine("**Problem Kind:** $problemKind")
      appendLine("**Processing:** ${if (isCloud) "Cloud" else "Local"}")
      appendLine("**${GrazieBundle.message("grazie.report.bug.additional.information")}:**")
    }

    val paramsString = buildList {
      add("project=IJPL")
      add("summary=${URLEncoder.encode(title, "UTF-8")}")
      add("c=${URLEncoder.encode("Subsystem Tools. Natural Languages", "UTF-8")}")
      add("c=${URLEncoder.encode("Affected versions ${ApplicationInfo.getInstance().fullVersion}", "UTF-8")}")
      add("description=${URLEncoder.encode(description, "UTF-8")}")
    }

    return buildString {
      append(NEW_ISSUE_URL)
      if (paramsString.isNotEmpty()) {
        append("?")
        append(paramsString.joinToString("&"))
      }
    }
  }

  private fun extractAndTrimText(editor: Editor?): String? = extractText(editor)?.replace(TRIM_REGEX, "")

  private fun extractText(editor: Editor?): String? {
    val editor = editor ?: return null
    val document = editor.document
    val elementRange = elementRange.range?.let(TextRange::create)
    val textRange = textRange.range?.let(TextRange::create)
    if (elementRange == null && textRange == null) return null
    if (elementRange != null && textRange == null) return document.getText(elementRange)
    if (elementRange == null && textRange != null) return extractWithAdditionalLines(document, textRange)
    if (abs(elementRange!!.length - textRange!!.length) < 100) {
      return document.getText(elementRange)
    }
    return extractWithAdditionalLines(document, textRange)
  }

  private fun extractWithAdditionalLines(document: Document, range: TextRange): String {
    val startLine = (document.getLineNumber(range.startOffset) - 1).coerceAtLeast(0)
    val endLine = (document.getLineNumber(range.endOffset) + 1).coerceAtMost(document.lineCount - 1)
    val expandedRange = TextRange(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine))
    return document.getText(expandedRange)
  }
}
