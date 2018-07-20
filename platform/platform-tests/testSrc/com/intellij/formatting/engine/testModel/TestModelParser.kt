/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.formatting.engine.testModel

enum class Label {
  SPACE,
  LPARENTH,
  RPARENTH,
  ATTR_START,
  ATTR_END,
  TEXT
}

private class TestBlockTreeBuilder {
  private val stack = mutableListOf(TestBlock.Composite("i_none"))
  private var recentSeenAttributes: String? = null

  fun attributes(attributes: String) {
    recentSeenAttributes = attributes
  }

  fun compositeBlockStart() {
    val lastChild = TestBlock.Composite(recentSeenAttributes!!)
    recentSeenAttributes = null

    top().addChild(lastChild)
    stack.add(lastChild)
  }

  fun space(text: String) {
    if (recentSeenAttributes != null) leafBlock("")
    
    val space = TestBlock.Space(text)
    top().addChild(space)
  }

  fun leafBlock(text: String) {
    top().addChild(TestBlock.Leaf(recentSeenAttributes!!, text))
    recentSeenAttributes = null
  }

  private fun top() = stack.last()

  fun compositeBlockEnd() {
    if (recentSeenAttributes != null) leafBlock("")
    
    stack.removeAt(stack.lastIndex)
  }

  fun rootBlock(): TestBlock.Composite {
    if (recentSeenAttributes != null) leafBlock("")

    return stack.first()
  }

}

class TestModelParser(private val text: String) {
  private val builder = TestBlockTreeBuilder()

  private fun getLabel(c: Char): Label {
    return when (c) {
      ' ', '\n' -> Label.SPACE
      '[' -> Label.ATTR_START
      ']' -> Label.ATTR_END
      '(' -> Label.LPARENTH
      ')' -> Label.RPARENTH
      else -> Label.TEXT
    }
  }

  fun parse(): TestBlock {
    var currentIndex = 0

    while (currentIndex < text.length) {
      val char = text[currentIndex]
      val label = getLabel(char)
      
      currentIndex = when (label) {
        Label.ATTR_START -> handleAttributes(currentIndex)
        Label.ATTR_END -> currentIndex + 1
        Label.TEXT -> handleText(currentIndex)
        Label.LPARENTH -> handleLParenth(currentIndex)
        Label.RPARENTH -> handleRParenth(currentIndex)
        Label.SPACE -> handleSpace(currentIndex)
      }
    }

    return builder.rootBlock()
  }

  private fun handleSpace(currentIndex: Int): Int {
    val spaceEnd = text.firstIndexOfAnyLabel(currentIndex, Label.ATTR_START)
    builder.space(text.substring(currentIndex, spaceEnd))
    return spaceEnd
  }

  private fun handleLParenth(currentIndex: Int): Int {
    builder.compositeBlockStart()
    return currentIndex + 1
  }

  private fun handleRParenth(currentIndex: Int): Int {
    builder.compositeBlockEnd()
    return currentIndex + 1
  }

  private fun handleText(currentIndex: Int): Int {
    val textEnd = text.firstIndexOfAnyLabel(currentIndex, Label.SPACE, Label.ATTR_START, Label.RPARENTH)
    val blockText = text.substring(currentIndex, textEnd)
    builder.leafBlock(blockText)
    return textEnd
  }

  private fun handleAttributes(currentIndex: Int): Int {
    val attrsEnd = text.firstIndexOfLabel(currentIndex, Label.ATTR_END)
    builder.attributes(text.substring(currentIndex + 1, attrsEnd))
    return attrsEnd
  }

  private fun String.firstIndexOfLabel(start: Int, label: Label): Int {
    val indexOfLabel = substring(start).indexOfFirst { getLabel(it) == label }
    return if (indexOfLabel > 0) start + indexOfLabel else length
  }

  private fun String.firstIndexOfAnyLabel(start: Int, vararg labels: Label): Int {
    val allLabels = labels.toHashSet()
    val indexOfLabel = substring(start).indexOfFirst { allLabels.contains(getLabel(it)) }
    return if (indexOfLabel > 0) start + indexOfLabel else length
  }
  
}


sealed class TestBlock {
  abstract val text: String

  class Space(override val text: String) : TestBlock()

  class Leaf(val attributes: String, override val text: String) : TestBlock()

  class Composite(val attributes: String) : TestBlock() {
    val children: MutableList<TestBlock> = mutableListOf<TestBlock>()
    fun addChild(block: TestBlock): Boolean = children.add(block)
    override val text: String
      get() = children.joinToString("", transform = { it.text })
  }
}

fun getRoot(text: String): TestBlock.Composite {
  val parser = TestModelParser(text)
  return parser.parse() as TestBlock.Composite
}
