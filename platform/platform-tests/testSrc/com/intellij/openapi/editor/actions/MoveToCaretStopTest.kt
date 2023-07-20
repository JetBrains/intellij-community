// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.actions.MoveToCaretStopTest.Action.DELETE
import com.intellij.openapi.editor.actions.MoveToCaretStopTest.Action.MOVE
import com.intellij.openapi.editor.actions.MoveToCaretStopTest.Direction.BACKWARD
import com.intellij.openapi.editor.actions.MoveToCaretStopTest.Direction.FORWARD
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MoveToCaretStopTest : BasePlatformTestCase() {
  enum class Action { MOVE, DELETE }

  enum class Direction : Comparator<Int> {
    BACKWARD, FORWARD;

    override fun compare(o1: Int, o2: Int): Int {
      return when (this) {
        FORWARD -> o1.compareTo(o2)
        BACKWARD -> o2.compareTo(o1)
      }
    }
  }

  fun `test Move to Next Word IDEA default`() {
    doTest(MOVE, FORWARD, """doTest^(^"^test^"^,^ Direction^.^FORWARD^)^""")
    doTest(MOVE, FORWARD, """
^      assert^(^i^ <^ expectedCaretOffset^)^ {^ "^Duplicate^ carets^:^ '^${'$'}stringWithCaretStops^'^"^ }^
""")

    doTest(MOVE, FORWARD, """
^  private^ fun^ doTest^(^stringWithCaretStops^:^ String^,^
^                     direction^:^ Direction^,^
^                     isDelete^:^ Boolean^ =^ false^)^ {^
""")

    doTest(MOVE, FORWARD, """
^data^ class^ CaretStopOptions^(^@^OptionTag^(^"^BACKWARD^"^)^ val^ backwardPolicy^:^ CaretStopPolicy^,^
^                            @^OptionTag^(^"^FORWARD^"^)^ val^ forwardPolicy^:^ CaretStopPolicy^)^ {^
""")

    doTest(MOVE, FORWARD, """
^  public^ void^ setCaretStopOptions^(^@^NotNull^ CaretStopOptions^ options^)^ {^
^    myOsSpecificState^.^CARET_STOP_OPTIONS^ =^ options^;^
^  }^
""")

    doTest(MOVE, FORWARD, """
^my_list^ =^ [^"^one^"^,^ "^two^"^,^ "^three^"^,^
^           "^four^"^,^ "^five^"^,^
^           "^six^"^.^six^,^ "^seven^"^seven^,^
^           eight^"^eight^"^,^ nine^/^"^nine^"^,^
^           "^...^"^"^ten^"^,^ (^"^...^"^"^eleven^"^"^...^"^)^,^ "^twelve^"^"^...^"^]^
""")

    doTest(MOVE, FORWARD, """
^  [^  "^word^"^"^...^"^]^  ,^
^  [^"^word^"^  "^...^"^]^  ,^
^  [^"^word^"^"^...^"^  ]^  ,^
^  [^ "^word^"^"^...^"^ ]^  ,^
^  [^"^  word^"^"^...^"^]^  ,^
^  [^"^ word^ "^"^...^"^]^  ,^
^  [^"^word^  "^"^...^"^]^  ,^
^  [^"^word^"^"^  ...^"^]^  ,^
^  [^"^word^"^"^ ...^ "^]^  ,^
^  [^"^word^"^"^...^  "^]^  ,^
""")
  }

  fun `test Move to Previous Word IDEA default`() {
    doTest(MOVE, BACKWARD, """^doTest^(^"^test^"^, ^Direction^.^FORWARD^)""")
    doTest(MOVE, BACKWARD, """
      ^assert^(^i ^< ^expectedCaretOffset^) ^{ ^"^Duplicate ^carets^: ^'^${'$'}stringWithCaretStops^'^" ^}^
""")

    doTest(MOVE, BACKWARD, """
  ^private ^fun ^doTest^(^stringWithCaretStops^: ^String^,^
                     ^direction^: ^Direction^,^
                     ^isDelete^: ^Boolean ^= ^false^) ^{^
""")

    doTest(MOVE, BACKWARD, """
^data ^class ^CaretStopOptions^(^@^OptionTag^(^"^BACKWARD^"^) ^val ^backwardPolicy^: ^CaretStopPolicy^,^
                            ^@^OptionTag^(^"^FORWARD^"^) ^val ^forwardPolicy^: ^CaretStopPolicy^) ^{^
""")

    doTest(MOVE, BACKWARD, """
  ^public ^void ^setCaretStopOptions^(^@^NotNull ^CaretStopOptions ^options^) ^{^
    ^myOsSpecificState^.^CARET_STOP_OPTIONS ^= ^options^;^
  ^}^
""")

    doTest(MOVE, BACKWARD, """
^my_list ^= ^[^"^one^"^, ^"^two^"^, ^"^three^"^,^
           ^"^four^"^, ^"^five^"^,^
           ^"^six^"^.^six^, ^"^seven^"^seven^,^
           ^eight^"^eight^"^, ^nine^/^"^nine^"^,^
           ^"^...^"^"^ten^"^, ^(^"^...^"^"^eleven^"^"^...^"^)^, ^"^twelve^"^"^...^"^]]^
""")

    doTest(MOVE, BACKWARD, """
  ^[  ^"^word^"^"^...^"^]  ^,^
  ^[^"^word^"  ^"^...^"^]  ^,^
  ^[^"^word^"^"^...^"  ^]  ^,^
  ^[ ^"^word^"^"^...^" ^]  ^,^
  ^[^"  ^word^"^"^...^"^]  ^,^
  ^[^" ^word ^"^"^...^"^]  ^,^
  ^[^"^word  ^"^"^...^"^]  ^,^
  ^[^"^word^"^"  ^...^"^]  ^,^
  ^[^"^word^"^" ^... ^"^]  ^,^
  ^[^"^word^"^"^...  ^"^]  ^,^
""")
  }

  fun `test Move to Next Word on Unix`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_UNIX)

    doTest(MOVE, FORWARD, """doTest^(^"^test^"^,^ Direction^.^FORWARD^)^""")
    doTest(MOVE, FORWARD, """
      assert^(^i^ <^ expectedCaretOffset^)^ {^ "^Duplicate^ carets^:^ '^${'$'}stringWithCaretStops^'^"^ }^
""")

    doTest(MOVE, FORWARD, """
  private^ fun^ doTest^(^stringWithCaretStops^:^ String^,^
                     direction^:^ Direction^,^
                     isDelete^:^ Boolean^ =^ false^)^ {^
""")

    doTest(MOVE, FORWARD, """
data^ class^ CaretStopOptions^(^@^OptionTag^(^"^BACKWARD^"^)^ val^ backwardPolicy^:^ CaretStopPolicy^,^
                            @^OptionTag^(^"^FORWARD^"^)^ val^ forwardPolicy^:^ CaretStopPolicy^)^ {^
""")

    doTest(MOVE, FORWARD, """
  public^ void^ setCaretStopOptions^(^@^NotNull^ CaretStopOptions^ options^)^ {^
    myOsSpecificState^.^CARET_STOP_OPTIONS^ =^ options^;^
  }^
""")

    doTest(MOVE, FORWARD, """
my_list^ =^ [^"^one^"^,^ "^two^"^,^ "^three^"^,^
           "^four^"^,^ "^five^"^,^
           "^six^"^.^six^,^ "^seven^"^seven^,^
           eight^"^eight^"^,^ nine^/^"^nine^"^,^
           "^...^"^"^ten^"^,^ (^"^...^"^"^eleven^"^"^...^"^)^,^ "^twelve^"^"^...^"^]^
""")

    doTest(MOVE, FORWARD, """
  [^  "^word^"^"^...^"^]^  ,^
  [^"^word^"^  "^...^"^]^  ,^
  [^"^word^"^"^...^"^  ]^  ,^
  [^ "^word^"^"^...^"^ ]^  ,^
  [^"^  word^"^"^...^"^]^  ,^
  [^"^ word^ "^"^...^"^]^  ,^
  [^"^word^  "^"^...^"^]^  ,^
  [^"^word^"^"^  ...^"^]^  ,^
  [^"^word^"^"^ ...^ "^]^  ,^
  [^"^word^"^"^...^  "^]^  ,^
""")
  }

  fun `test Move to Previous Word on Unix`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_UNIX)

    doTest(MOVE, BACKWARD, """^doTest^(^"^test^"^, ^Direction^.^FORWARD^)""")
    doTest(MOVE, BACKWARD, """
      ^assert^(^i ^< ^expectedCaretOffset^) ^{ ^"^Duplicate ^carets^: ^'^${'$'}stringWithCaretStops^'^" ^}
""")

    doTest(MOVE, BACKWARD, """
  ^private ^fun ^doTest^(^stringWithCaretStops^: ^String^,
                     ^direction^: ^Direction^,
                     ^isDelete^: ^Boolean ^= ^false^) ^{
""")

    doTest(MOVE, BACKWARD, """
^data ^class ^CaretStopOptions^(^@^OptionTag^(^"^BACKWARD^"^) ^val ^backwardPolicy^: ^CaretStopPolicy^,
                            ^@^OptionTag^(^"^FORWARD^"^) ^val ^forwardPolicy^: ^CaretStopPolicy^) ^{
""")

    doTest(MOVE, BACKWARD, """
  ^public ^void ^setCaretStopOptions^(^@^NotNull ^CaretStopOptions ^options^) ^{
    ^myOsSpecificState^.^CARET_STOP_OPTIONS ^= ^options^;
  ^}
""")

    doTest(MOVE, BACKWARD, """
^my_list ^= ^[^"^one^"^, ^"^two^"^, ^"^three^"^,
           ^"^four^"^, ^"^five^"^,
           ^"^six^"^.^six^, ^"^seven^"^seven^,
           ^eight^"^eight^"^, ^nine^/^"^nine^"^,
           ^"^...^"^"^ten^"^, ^(^"^...^"^"^eleven^"^"^...^"^)^, ^"^twelve^"^"^...^"^]]
""")

    doTest(MOVE, BACKWARD, """
  ^[  ^"^word^"^"^...^"^]  ^,
  ^[^"^word^"  ^"^...^"^]  ^,
  ^[^"^word^"^"^...^"  ^]  ^,
  ^[ ^"^word^"^"^...^" ^]  ^,
  ^[^"  ^word^"^"^...^"^]  ^,
  ^[^" ^word ^"^"^...^"^]  ^,
  ^[^"^word  ^"^"^...^"^]  ^,
  ^[^"^word^"^"  ^...^"^]  ^,
  ^[^"^word^"^" ^... ^"^]  ^,
  ^[^"^word^"^"^...  ^"^]  ^,
""")
  }

  fun `test Move to Next Word on Windows`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_WINDOWS)
    `do test Move to Neighbor Word on Windows`(FORWARD)
  }

  fun `test Move to Previous Word on Windows`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_WINDOWS)
    `do test Move to Neighbor Word on Windows`(BACKWARD)
  }

  private fun `do test Move to Neighbor Word on Windows`(direction: Direction) {
    doTest(MOVE, direction, """doTest^(^"^test^"^, ^Direction^.^FORWARD^)""")
    doTest(MOVE, direction, """
^      ^assert^(^i ^< ^expectedCaretOffset^) ^{ ^"^Duplicate ^carets^: ^'^${'$'}stringWithCaretStops^'^" ^}^
""")

    doTest(MOVE, direction, """
^  ^private ^fun ^doTest^(^stringWithCaretStops^: ^String^,^
^                     ^direction^: ^Direction^,^
^                     ^isDelete^: ^Boolean ^= ^false^) ^{^
""")

    doTest(MOVE, direction, """
^data ^class ^CaretStopOptions^(^@^OptionTag^(^"^BACKWARD^"^) ^val ^backwardPolicy^: ^CaretStopPolicy^,^
^                            ^@^OptionTag^(^"^FORWARD^"^) ^val ^forwardPolicy^: ^CaretStopPolicy^) ^{^
""")

    doTest(MOVE, direction, """
^  ^public ^void ^setCaretStopOptions^(^@^NotNull ^CaretStopOptions ^options^) ^{^
^    ^myOsSpecificState^.^CARET_STOP_OPTIONS ^= ^options^;^
^  ^}^
""")

    doTest(MOVE, direction, """
^my_list ^= ^[^"^one^"^, ^"^two^"^, ^"^three^"^,^
^           ^"^four^"^, ^"^five^"^,^
^           ^"^six^"^.^six^, ^"^seven^"^seven^,^
^           ^eight^"^eight^"^, ^nine^/^"^nine^"^,^
^           ^"^...^"^"^ten^"^, ^(^"^...^"^"^eleven^"^"^...^"^)^, ^"^twelve^"^"^...^"^]^
""")

    doTest(MOVE, direction, """
^  ^[  ^"^word^"^"^...^"^]  ^,^
^  ^[^"^word^"  ^"^...^"^]  ^,^
^  ^[^"^word^"^"^...^"  ^]  ^,^
^  ^[ ^"^word^"^"^...^" ^]  ^,^
^  ^[^"  ^word^"^"^...^"^]  ^,^
^  ^[^" ^word ^"^"^...^"^]  ^,^
^  ^[^"^word  ^"^"^...^"^]  ^,^
^  ^[^"^word^"^"  ^...^"^]  ^,^
^  ^[^"^word^"^" ^... ^"^]  ^,^
^  ^[^"^word^"^"^...  ^"^]  ^,^
""")
  }

  fun `test Delete to Word End on Unix`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_UNIX)
    `do test Delete to Word End`()
  }

  fun `test Delete to Word End on Windows`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_WINDOWS)
    `do test Delete to Word End`()
  }

  private fun `do test Delete to Word End`() {
    doTest(DELETE, FORWARD, """doTest^(^"`test`"^,^ ^Direction^.^FORWARD^)^""")
    doTest(DELETE, FORWARD, """
^      ^assert^(^i^ ^<^ ^expectedCaretOffset^)^ ^{^ ^"^Duplicate^ ^carets^:^ ^'^${'$'}stringWithCaretStops^'^"^ ^}^
""")

    doTest(DELETE, FORWARD, """
^  ^private^ ^fun^ ^doTest^(^stringWithCaretStops^:^ ^String^,^
^                     ^direction^:^ ^Direction^,^
^                     ^isDelete^:^ ^Boolean^ ^=^ ^false^)^ ^{^
""")

    doTest(DELETE, FORWARD, """
^data^ ^class^ ^CaretStopOptions^(^@^OptionTag^(^"`BACKWARD`"^)^ ^val^ ^backwardPolicy^:^ ^CaretStopPolicy^,^
^                            ^@^OptionTag^(^"`FORWARD`"^)^ ^val^ ^forwardPolicy^:^ ^CaretStopPolicy^)^ ^{^
""")

    doTest(DELETE, FORWARD, """
^  ^public^ ^void^ ^setCaretStopOptions^(^@^NotNull^ ^CaretStopOptions^ ^options^)^ ^{^
^    ^myOsSpecificState^.^CARET_STOP_OPTIONS^ ^=^ ^options^;^
^  ^}^
""")

    doTest(DELETE, FORWARD, """
^my_list^ ^=^ ^[^"`one`"^,^ ^"`two`"^,^ ^"`three`"^,^
^           ^"`four`"^,^ ^"`five`"^,^
^           ^"`six`"^.^six^,^ ^"`seven`"^seven^,^
^           ^eight^"`eight`"^,^ ^nine^/^"`nine`"^,^
^           ^"`...`"^"`ten`"^,^ ^(^"`...`"^"`eleven`"^"`...`"^)^,^ ^"`twelve`"^"`...`"^]^
""")
  }

  fun `test Delete to Word Start on Unix`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_UNIX)
    `do test Delete to Word Start`()
  }

  fun `test Delete to Word Start on Windows`() {
    setupTestCaretStops(CaretStopOptionsTransposed.DEFAULT_WINDOWS)
    `do test Delete to Word Start`()
  }

  // TODO for some reason this test performs 4-5 time slower than its twin "Delete to Word End" test
  private fun `do test Delete to Word Start`() {
    doTest(DELETE, BACKWARD, """^doTest^(^"`test`"^, ^Direction^.^FORWARD^)""")
    doTest(DELETE, BACKWARD, """
^      ^assert^(^i ^< ^expectedCaretOffset^) ^{ ^"^Duplicate ^carets^: ^'^${'$'}stringWithCaretStops^'^" ^}^
""")

    doTest(DELETE, BACKWARD, """
^  ^private ^fun ^doTest^(^stringWithCaretStops^: ^String^,^
^                     ^direction^: ^Direction^,^
^                     ^isDelete^: ^Boolean ^= ^false^) ^{^
""")

    doTest(DELETE, BACKWARD, """
^data ^class ^CaretStopOptions^(^@^OptionTag^(^"`BACKWARD`"^) ^val ^backwardPolicy^: ^CaretStopPolicy^,^
^                            ^@^OptionTag^(^"`FORWARD`"^) ^val ^forwardPolicy^: ^CaretStopPolicy^) ^{^
""")

    doTest(DELETE, BACKWARD, """
^  ^public ^void ^setCaretStopOptions^(^@^NotNull ^CaretStopOptions ^options^) ^{^
^    ^myOsSpecificState^.^CARET_STOP_OPTIONS ^= ^options^;^
^  ^}^
""")

    doTest(DELETE, BACKWARD, """
^my_list ^= ^[^"`one`"^, ^"`two`"^, ^"`three`"^,^
^           ^"`four`"^, ^"`five`"^,^
^           ^"`six`"^.^six^, ^"`seven`"^seven^,^
^           ^eight^"`eight`"^, ^nine^/^"`nine`"^,^
^           ^"`...`"^"`ten`"^, ^(^"`...`"^"`eleven`"^"`...`"^)^, ^"`twelve`"^"`...`"^]^
""")
  }

  private fun doTest(action: Action,
                     direction: Direction,
                     stringWithCaretStops: String) {
    val (str, caretOffsets: List<CaretOffset>) = stringWithCaretStops.extractCaretOffsets()

    myFixture.configureByText("a.java", str)
    val editor = myFixture.editor
    val document = editor.document

    str.offsets(direction).forEach { i ->
      val expectedCaretOffset = caretOffsets.nextTo(i, direction)

      editor.caretModel.moveToOffset(i)

      try {
        myFixture.performEditorAction(ideAction(action, direction))

        val expectedWithReadableCarets = when (action) {
          MOVE -> str.withCaretMarkerAt(i, expectedCaretOffset.offset)
          DELETE -> str.withCaretMarkerBetween(i, expectedCaretOffset.offset)
        }
        val actualWithReadableCarets = when (action) {
          MOVE -> document.text.withCaretMarkerAt(i, myFixture.caretOffset)
          DELETE -> document.text.withCaretMarkerAt(myFixture.caretOffset)
        }
        assertEquals(expectedWithReadableCarets, actualWithReadableCarets)
      }
      finally {
        if (action == DELETE) {
          WriteCommandAction.runWriteCommandAction(project) {
            // for some reason moving the caret forward makes "Delete to Word Start" tests run 2-3 times faster
            val caretOffset = (myFixture.caretOffset + 1).coerceIn(0, document.textLength)
            editor.caretModel.moveToOffset(caretOffset)
            document.setText(str)
          }
        }
      }
    }
  }

  private fun setupTestCaretStops(caretStopSettings: CaretStopOptionsTransposed) {
    val savedCaretStopOptions = EditorSettingsExternalizable.getInstance().caretStopOptions
    EditorSettingsExternalizable.getInstance().caretStopOptions = caretStopSettings.toCaretStopOptions()
    disposeOnTearDown(Disposable {
      EditorSettingsExternalizable.getInstance().caretStopOptions = savedCaretStopOptions
    })
  }

  companion object {
    private const val READABLE_CARET = '^'
    private const val SKIP_INNARDS_CARET = '`' // delete word consumes "quoted" words

    private fun isTestCaret(ch: Char) =
      ch == READABLE_CARET || ch == SKIP_INNARDS_CARET

    private data class CaretOffset(val offset: Int, val isSkipInnards: Boolean = false)

    private fun String.extractCaretOffsets(): Pair<String, List<CaretOffset>> {
      val caretOffsets: List<CaretOffset> = trim(READABLE_CARET, SKIP_INNARDS_CARET)
        .let { "^$it^" }
        .mapIndexedNotNull { offset, ch ->
          (offset to (ch == SKIP_INNARDS_CARET)).takeIf { isTestCaret(ch) }
        }.mapIndexed { i, (offset, isSkipInnards) ->
          CaretOffset(offset - i, isSkipInnards)
        }
      val str = filterNot(::isTestCaret)
      return str to caretOffsets
    }

    private fun List<CaretOffset>.nextTo(offset: Int, direction: Direction): CaretOffset {
      val foundIndex = binarySearch { it.offset.compareTo(offset) }
      val skip = !when {
        foundIndex >= 0 -> this[foundIndex].isSkipInnards
        else -> -foundIndex - 1 in indices && this[-foundIndex - 1].isSkipInnards &&
                -foundIndex - 2 in indices && this[-foundIndex - 2].isSkipInnards
      }
      return when (direction) {
        FORWARD -> first { it.offset > offset && !(skip && it.isSkipInnards) }
        BACKWARD -> last { it.offset < offset && !(skip && it.isSkipInnards) }
      }
    }

    private fun ideAction(action: Action, direction: Direction): String =
      when (action) {
        MOVE -> when (direction) {
          FORWARD -> IdeActions.ACTION_EDITOR_NEXT_WORD
          BACKWARD -> IdeActions.ACTION_EDITOR_PREVIOUS_WORD
        }
        DELETE -> when (direction) {
          FORWARD -> IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END
          BACKWARD -> IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START
        }
      }

    private fun CharSequence.offsets(direction: Direction): IntProgression =
      when (direction) {
        FORWARD -> indices
        BACKWARD -> length downTo 1
      }

    private fun String.withCaretMarkerAt(firstOffset: Int, secondOffset: Int = firstOffset): String =
      if (firstOffset == secondOffset)
        withCaretMarkerBetween(firstOffset, secondOffset)
      else
        withCaretMarkerBetween(maxOf(firstOffset, secondOffset))
          .withCaretMarkerBetween(minOf(firstOffset, secondOffset))

    private fun String.withCaretMarkerBetween(firstOffset: Int, secondOffset: Int = firstOffset): String =
      substring(0, minOf(firstOffset, secondOffset)) + READABLE_CARET +
      substring(maxOf(firstOffset, secondOffset))
  }
}