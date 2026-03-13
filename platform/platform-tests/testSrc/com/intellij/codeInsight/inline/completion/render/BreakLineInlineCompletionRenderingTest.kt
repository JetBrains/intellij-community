// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionTestCase
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class BreakLineInlineCompletionRenderingTest : InlineCompletionTestCase() {

  @Test
  fun `test simple rendering of single line completion`() = doTest(
    file = """
      val value =<caret>
      val second = 20
    """.trimIndent(),
    completion = listOf(textElement(" "), textElement("10"), textElement(" + 30")),
    expected = listOf(
      listOf(textBlock(" "), textBlock("10"), textBlock(" + 30"))
    )
  )

  @Test
  fun `test simple rendering of multiline completion`() = doTest(
    file = """
      val value =<caret>
      val second = 20
    """.trimIndent(),
    completion = listOf(
      textElement(" "), textElement("10"), textElement("\n  "),
      textElement("20"), textElement("\n  + 40 + 50\n + 100\n + 200"),
      textElement(" + 400"), textElement("\n + 600")
    ),
    expected = listOf(
      listOf(textBlock(" "), textBlock("10")),
      listOf(textBlock("  "), textBlock("20")),
      listOf(textBlock("  + 40 + 50")),
      listOf(textBlock(" + 100")),
      listOf(textBlock(" + 200"), textBlock(" + 400")),
      listOf(textBlock(" + 600"))
    )
  )

  @Test
  fun `test rendering of completion with simple folding of the end of line`() = doTest(
    file = """
      fun <caret> {}
    """.trimIndent(),
    completion = listOf(
      textElement("hello"), textElement("()"), skipElement(" "), skipElement("{"),
      textElement("\n    "), textElement("println("), textElement(")\n"),
    ),
    expected = listOf(
      listOf(textBlock("hello"), textBlock("()")),
      listOf(breakLineBlock()),
      listOf(textBlock("    "), textBlock("println("), textBlock(")")),
      listOf(realBlock("}"))
    )
  )

  @Test
  fun `test rendering of completion with simple folding of lambda block`() = doTest(
    file = """
      fun main() {
        val a =<caret> {}
      }
    """.trimIndent(),
    completion = listOf(
      textElement("10"), textElement(".let"), skipElement(" {"), textElement(" value ->"),
      textElement("\n    "), textElement("println(value"), textElement(")\n"),
      textElement("  "), skipElement("}")
    ),
    expected = listOf(
      listOf(textBlock("10"), textBlock(".let")),
      listOf(textBlock(" value ->"), breakLineBlock()),
      listOf(textBlock("    "), textBlock("println(value"), textBlock(")")),
      listOf(textBlock("  "), realBlock("}"))
    )
  )

  @Test
  fun `tst rendering of completion with folding and skipping the folding on other lines`() = doTest(
    file = """
      fun main() {
        val a = <caret>{{}{}{{}}}
      }
    """.trimIndent(),
    completion = listOf(
      textElement("("), textElement("10 + 10"), textElement(").let "), skipElement("{"), textElement(" value1 ->"),
      textElement("\n    "), textElement("if (10 == 10) "), skipElement("{"),
      textElement("\n"), textElement("      println(value1)\n"),
      textElement("    "), skipElement("}"),
      textElement("\n    if (10 =="), textElement(" 20) "), skipElement("{"),
      textElement("\n      val newValue = value1 + value1\n      println(newValue)\n    "), skipElement("}"),
      textElement("\n    "), textElement("run "), skipElement("{"), textElement(" run "), skipElement("{"),
      textElement("\n        println(42)\n"), textElement("    "), skipElement("}"), textElement(" "), skipElement("}"),
      textElement("\n  "), skipElement("}")
    ),
    expected = listOf(
      listOf(textBlock("("), textBlock("10 + 10"), textBlock(").let ")),
      listOf(textBlock(" value1 ->"), breakLineBlock()),
      listOf(textBlock("    "), textBlock("if (10 == 10) "), realBlock("{"), breakLineBlock()),
      listOf(textBlock("      println(value1)")),
      listOf(textBlock("    "), realBlock("}"), breakLineBlock()),
      listOf(textBlock("    if (10 =="), textBlock(" 20) "), realBlock("{"), breakLineBlock()),
      listOf(textBlock("      val newValue = value1 + value1")),
      listOf(textBlock("      println(newValue)")),
      listOf(textBlock("    "), realBlock("}"), breakLineBlock()),
      listOf(textBlock("    "), textBlock("run "), realBlock("{"), textBlock(" run "), realBlock("{"), breakLineBlock()),
      listOf(textBlock("        println(42)")),
      listOf(textBlock("    "), realBlock("}"), textBlock(" "), realBlock("}"), breakLineBlock()),
      listOf(textBlock("  "), realBlock("}"))
    )
  )

  @Test
  fun `test rendering of completion inserting between words`() = doTest(
    file = """
      fun method():<caret>Int= 222.someMethod(,)
    """.trimIndent(),
    completion = listOf(
      textElement(" "), skipElement("Int"), textElement(" "), skipElement("= "),
      textElement("\n\n("), skipElement("222"), textElement(" + 111)\n\n"),
      textElement("  "), skipElement(".some"), skipElement("Method("),
      textElement("42"), skipElement(","), textElement(" 24"), skipElement(")"),
      textElement(" + "), textElement("1000")
    ),
    expected = listOf(
      listOf(textBlock(" ")),
      listOf(textBlock(" ")),
      listOf(breakLineBlock()),
      listOf(),
      listOf(textBlock("("), realBlock("222"), textBlock(" + 111)"), breakLineBlock()),
      listOf(),
      listOf(
        textBlock("  "), realBlock(".someMethod"), realBlock("("), textBlock("42"),
        realBlock(","), textBlock(" 24"), realBlock(")"), textBlock(" + "), textBlock("1000"),
      )
    )
  )

  private fun doTest(file: String, completion: List<CompletionElement>, expected: List<List<BlockDescriptor>>) {
    LOG.debug("Completion:\n${completion.joinToString("") { it.text }}")
    myFixture.testInlineCompletion {
      init(PlainTextFileType.INSTANCE, file)
      registerSuggestion(*completion.toTypedArray())
      callInlineCompletion()
      delay()
      assertEqualsBlocks(expected, getCurrentRenderBlocks())

      escape()
      assertInlineHidden()

      assertEqualsBlocks(emptyList(), getCurrentRenderBlocks())
    }
  }

  private fun assertEqualsBlocks(expected: List<List<BlockDescriptor>>, actual: List<List<BlockDescriptor>>) {
    fun prettify(blocks: List<List<BlockDescriptor>>): String {
      return blocks.joinToString(separator = ",\n")
    }
    if (expected != actual) {
      assertEquals(prettify(expected), prettify(actual))
      fail("Blocks do not match: expected=$expected, actual=$actual") // just in case they are equal in the string representation...
    }
  }

  private fun registerSuggestion(vararg suggestion: CompletionElement) {
    val elements = suggestion.map {
      when (it) {
        is CompletionElement.Skip -> InlineCompletionSkipTextElement(it.text)
        is CompletionElement.Text -> InlineCompletionGrayTextElement(it.text)
      }
    }
    val provider = object : InlineCompletionProvider {
      override val id: InlineCompletionProviderID = InlineCompletionProviderID("TEST")
      override fun isEnabled(event: InlineCompletionEvent): Boolean = event is InlineCompletionEvent.DirectCall
      override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        return InlineCompletionSingleSuggestion.build {
          elements.forEach { emit(it) }
        }
      }
    }
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
  }

  /**
   * Returns all the rendered blocks in the editor for the currently rendered completion.
   */
  private suspend fun getCurrentRenderBlocks(): List<List<BlockDescriptor>> {
    return withContext(Dispatchers.EDT) {
      coroutineToIndicator {
        val result = mutableListOf<List<BlockDescriptor>>()
        val session = InlineCompletionSession.getOrNull(myFixture.editor)
        if (session == null) {
          return@coroutineToIndicator result
        }

        val startOffset = myFixture.editor.caretModel.offset
        val baseEndOffset = session.context.endOffset()
        assertNotNull("End offset is expected to be non-null.", baseEndOffset)
        val endOffset = myFixture.editor.document.getLineEndOffset(myFixture.editor.document.getLineNumber(baseEndOffset!!))

        myFixture.editor.inlayModel.execute(true) {
          for (offset in startOffset..endOffset) {
            if (myFixture.editor.foldingModel.isOffsetCollapsed(offset)) {
              continue
            }
            val inlineElements = myFixture.editor.inlayModel.getInlineElementsInRange(offset, offset)
            val blockElements = myFixture.editor.inlayModel.getBlockElementsInRange(offset, offset)
            for (element in inlineElements) {
              result.addIfNotNull(element.getBlocksOrNull())
            }
            for (element in blockElements) {
              result.addIfNotNull(element.getBlocksOrNull())
            }
          }
        }

        return@coroutineToIndicator result
      }
    }
  }

  private fun InlineCompletionRenderTextBlock.asDescriptor(): BlockDescriptor {
    return when {
      InlineCompletionTextRenderManager.whetherBlockBreaksLine(this) -> BlockDescriptor.BreakLine
      InlineCompletionTextRenderManager.isRealTextBlock(this) -> BlockDescriptor.RealText(text)
      else -> BlockDescriptor.InlineText(text)
    }
  }

  private sealed interface BlockDescriptor {
    data class InlineText(val text: String) : BlockDescriptor
    data class RealText(val text: String) : BlockDescriptor
    data object BreakLine : BlockDescriptor
  }

  private sealed interface CompletionElement {
    val text: String

    data class Text(override val text: String) : CompletionElement
    data class Skip(override val text: String) : CompletionElement
  }

  private fun textBlock(text: String): BlockDescriptor.InlineText = BlockDescriptor.InlineText(text)
  private fun realBlock(text: String): BlockDescriptor.RealText = BlockDescriptor.RealText(text)
  private fun breakLineBlock(): BlockDescriptor.BreakLine = BlockDescriptor.BreakLine

  private fun textElement(text: String): CompletionElement.Text = CompletionElement.Text(text)
  private fun skipElement(text: String): CompletionElement.Skip = CompletionElement.Skip(text)

  private fun Inlay<*>.getBlocksOrNull(): List<BlockDescriptor>? {
    return (renderer as? InlineCompletionLineRenderer)?.blocks?.map { it.asDescriptor() }
  }
}
