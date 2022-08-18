// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.formatter.KotlinObsoleteCodeStyle
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class TypedHandlerTest : KotlinLightCodeInsightFixtureTestCase() {
    private val dollar = '$'

    fun testTypeStringTemplateStart() = doTypeTest(
        '{',
        """val x = "$<caret>" """,
        """val x = "$dollar{}" """
    )

    fun testAutoIndentGetter() = doTypeTest(
        ch = '(',
        beforeText = """
            val i: Int
            get<caret>
        """.trimIndent(),
        afterText = """
            val i: Int
                get(<caret>)
        """.trimIndent(),
    )

    fun testAutoIndentSetter() = doTypeTest(
        ch = '(',
        beforeText = """
            var i: Int
            set<caret>
        """.trimIndent(),
        afterText = """
            var i: Int
                set(<caret>)
        """.trimIndent(),
    )

    fun testAutoIndentSetterAndGetter() = doTypeTest(
        ch = '(',
        beforeText = """
            var i: Int
                get() = 42
            set<caret>
        """.trimIndent(),
        afterText = """
            var i: Int
                get() = 42
                set(<caret>)
        """.trimIndent(),
    )

    fun testAutoIndentSetterAndGetter2() = doTypeTest(
        ch = '(',
        beforeText = """
            var i: Int
                get() = 42
                set<caret>
        """.trimIndent(),
        afterText = """
            var i: Int
                get() = 42
                set(<caret>)
        """.trimIndent(),
    )

    fun testAutoIndentGetterWithModifierBefore() = doTypeTest(
        ch = '(',
        beforeText = """
            val i: Int
            private get<caret>
        """.trimIndent(),
        afterText = """
            val i: Int
                private get(<caret>)
        """.trimIndent(),
    )

    fun testAutoIndentSetterWithModifierBefore() = doTypeTest(
        ch = '(',
        beforeText = """
            var i: Int
            private set<caret>
        """.trimIndent(),
        afterText = """
            var i: Int
                private set(<caret>)
        """.trimIndent(),
    )

    fun testAutoIndentRightOpenBrace() = doTypeTest(
        '{',

        "fun test() {\n" +
                "<caret>\n" +
                "}",

        "fun test() {\n" +
                "    {<caret>}\n" +
                "}"
    )

    fun testAutoIndentLeftOpenBrace() = doTypeTest(
        '{',

        "fun test() {\n" +
                "      <caret>\n" +
                "}",

        "fun test() {\n" +
                "    {<caret>}\n" +
                "}"
    )

    fun testTypeStringTemplateStartWithCloseBraceAfter() = doTypeTest(
        '{',
        """fun foo() { "$<caret>" }""",
        """fun foo() { "$dollar{}" }"""
    )

    fun testTypeStringTemplateStartBeforeStringWithExistingDollar() = doTypeTest(
        '{',
        """fun foo() { "$<caret>something" }""",
        """fun foo() { "$dollar{something" }"""
    )

    fun testTypeStringTemplateStartBeforeStringWithNoDollar() = doTypeTest(
        "$dollar{",
        """fun foo() { "<caret>something" }""",
        """fun foo() { "$dollar{<caret>}something" }"""
    )

    fun testTypeStringTemplateWithUnmatchedBrace() = doTypeTest(
        "$dollar{",
        """val a = "<caret>bar}foo"""",
        """val a = "$dollar{<caret>bar}foo""""
    )

    fun testTypeStringTemplateWithUnmatchedBraceComplex() = doTypeTest(
        "$dollar{",
        """val a = "<caret>bar + more}foo"""",
        """val a = "$dollar{<caret>}bar + more}foo""""
    )

    fun testTypeStringTemplateStartInStringWithBraceLiterals() = doTypeTest(
        "$dollar{",
        """val test = "{ code <caret>other }"""",
        """val test = "{ code $dollar{<caret>}other }""""
    )

    fun testTypeStringTemplateStartInEmptyString() = doTypeTest(
        '{',
        """fun foo() { "$<caret>" }""",
        """fun foo() { "$dollar{<caret>}" }"""
    )

    fun testKT3575() = doTypeTest(
        '{',
        """val x = "$<caret>]" """,
        """val x = "$dollar{}]" """
    )

    fun testAutoCloseRawStringInEnd() = doTypeTest(
        '"',
        """val x = ""<caret>""",
        """val x = ""${'"'}<caret>""${'"'}"""
    )

    fun testNoAutoCloseRawStringInEnd() = doTypeTest(
        '"',
        """val x = ""${'"'}<caret>""",
        """val x = ""${'"'}""""
    )

    fun testAutoCloseRawStringInMiddle() = doTypeTest(
        '"',
        """
            val x = ""<caret>
            val y = 12
            """.trimIndent(),
        """
            val x = ""${'"'}<caret>""${'"'}
            val y = 12
            """.trimIndent()
    )

    fun testNoAutoCloseBetweenMultiQuotes() = doTypeTest(
        '"',
        """val x = ""${'"'}<caret>${'"'}""/**/""",
        """val x = ""${'"'}${'"'}<caret>""/**/"""
    )

    fun testNoAutoCloseBetweenMultiQuotes1() = doTypeTest(
        '"',
        """val x = ""${'"'}"<caret>"${'"'}/**/""",
        """val x = ""${'"'}""<caret>${'"'}/**/"""
    )

    fun testNoAutoCloseAfterEscape() = doTypeTest(
        '"',
        """val x = "\""<caret>""",
        """val x = "\""${'"'}<caret>""""
    )

    fun testAutoCloseBraceInFunctionDeclaration() = doTypeTest(
        '{',
        "fun foo() <caret>",
        "fun foo() {<caret>}"
    )

    fun testAutoCloseBraceInLocalFunctionDeclaration() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    fun bar() <caret>\n" +
                "}",

        "fun foo() {\n" +
                "    fun bar() {<caret>}\n" +
                "}"
    )

    fun testAutoCloseBraceInAssignment() = doTypeTest(
        '{',
        "fun foo() {\n" +
                "    val a = <caret>\n" +
                "}",

        "fun foo() {\n" +
                "    val a = {<caret>}\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedIfSurroundOnSameLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    if() <caret>foo()\n" +
                "}",

        "fun foo() {\n" +
                "    if() {foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedElseSurroundOnSameLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    if(true) {} else <caret>foo()\n" +
                "}",

        "fun foo() {\n" +
                "    if(true) {} else {foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedTryOnSameLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    try <caret>foo()\n" +
                "}",

        "fun foo() {\n" +
                "    try {foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedCatchOnSameLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    try {} catch (e: Exception) <caret>foo()\n" +
                "}",

        "fun foo() {\n" +
                "    try {} catch (e: Exception) {foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedFinallyOnSameLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    try {} catch (e: Exception) finally <caret>foo()\n" +
                "}",

        "fun foo() {\n" +
                "    try {} catch (e: Exception) finally {foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedWhileSurroundOnSameLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    while() <caret>foo()\n" +
                "}",

        "fun foo() {\n" +
                "    while() {foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedWhileSurroundOnNewLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    while()\n" +
                "<caret>\n" +
                "    foo()\n" +
                "}",

        "fun foo() {\n" +
                "    while()\n" +
                "    {\n" +
                "    foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedIfSurroundOnOtherLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    if(true) <caret>\n" +
                "    foo()\n" +
                "}",

        "fun foo() {\n" +
                "    if(true) {<caret>\n" +
                "    foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedElseSurroundOnOtherLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    if(true) {} else <caret>\n" +
                "    foo()\n" +
                "}",

        "fun foo() {\n" +
                "    if(true) {} else {<caret>\n" +
                "    foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedTryOnOtherLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    try <caret>\n" +
                "    foo()\n" +
                "}",

        "fun foo() {\n" +
                "    try {<caret>\n" +
                "    foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedIfSurroundOnNewLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    if(true)\n" +
                "        <caret>\n" +
                "    foo()\n" +
                "}",

        "fun foo() {\n" +
                "    if(true)\n" +
                "    {<caret>\n" +
                "    foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedElseSurroundOnNewLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    if(true) {} else\n" +
                "        <caret>\n" +
                "    foo()\n" +
                "}",

        "fun foo() {\n" +
                "    if(true) {} else\n" +
                "    {<caret>\n" +
                "    foo()\n" +
                "}"
    )

    fun testDoNotAutoCloseBraceInUnfinishedTryOnNewLine() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    try\n" +
                "        <caret>\n" +
                "    foo()\n" +
                "}",

        "fun foo() {\n" +
                "    try\n" +
                "    {<caret>\n" +
                "    foo()\n" +
                "}"
    )

    fun testAutoCloseBraceInsideFor() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    for (elem in some.filter <caret>) {\n" +
                "    }\n" +
                "}",

        "fun foo() {\n" +
                "    for (elem in some.filter {<caret>}) {\n" +
                "    }\n" +
                "}"
    )

    fun testAutoCloseBraceInsideForAfterCloseParen() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    for (elem in some.foo(true) <caret>) {\n" +
                "    }\n" +
                "}",

        "fun foo() {\n" +
                "    for (elem in some.foo(true) {<caret>}) {\n" +
                "    }\n" +
                "}"
    )

    fun testAutoCloseBraceBeforeIf() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    <caret>if (true) {}\n" +
                "}",

        "fun foo() {\n" +
                "    {<caret>if (true) {}\n" +
                "}"
    )

    fun testAutoCloseBraceInIfCondition() = doTypeTest(
        '{',

        "fun foo() {\n" +
                "    if (some.hello (12) <caret>)\n" +
                "}",

        "fun foo() {\n" +
                "    if (some.hello (12) {<caret>})\n" +
                "}"
    )

    fun testInsertSpaceAfterRightBraceOfNestedLambda() = doTypeTest(
        '{',
        "val t = Array(100) { Array(200) <caret>}",
        "val t = Array(100) { Array(200) {<caret>} }"
    )

    fun testAutoInsertParenInStringLiteral() = doTypeTest(
        '(',
        """fun f() { println("$dollar{f<caret>}") }""",
        """fun f() { println("$dollar{f(<caret>)}") }"""
    )

    fun testAutoInsertParenInCode() = doTypeTest(
        '(',
        """fun f() { val a = f<caret> }""",
        """fun f() { val a = f(<caret>) }"""
    )

    fun testTypeLtInFunDeclaration() {
        doLtGtTest("fun <caret>")
    }

    fun testTypeLtInOngoingConstructorCall() {
        doLtGtTest("fun test() { Collection<caret> }")
    }

    fun testTypeLtInClassDeclaration() {
        doLtGtTest("class Some<caret> {}")
    }

    fun testTypeLtInPropertyType() {
        doLtGtTest("val a: List<caret> ")
    }

    fun testTypeLtInExtensionFunctionReceiver() {
        doLtGtTest("fun <T> Collection<caret> ")
    }

    fun testTypeLtInFunParam() {
        doLtGtTest("fun some(a : HashSet<caret>)")
    }

    fun testTypeLtInFun() {
        doLtGtTestNoAutoClose("fun some() { <<caret> }")
    }

    fun testTypeLtInLess() {
        doLtGtTestNoAutoClose("fun some() { val a = 12; a <<caret> }")
    }

    fun testColonOfSuperTypeList() {
        doTypeTest(
            ':',
            """
                |open class A
                |class B
                |<caret>
                """,
            """
                |open class A
                |class B
                |    :<caret>
                """
        )
    }

    fun testColonOfSuperTypeListInObject() {
        doTypeTest(
            ':',
            """
                |interface A
                |object B
                |<caret>
                """,
            """
                |interface A
                |object B
                |    :<caret>
                """
        )
    }

    fun testColonOfSuperTypeListInCompanionObject() {
        doTypeTest(
            ':',
            """
                |interface A
                |class B {
                |    companion object
                |    <caret>
                |}
                """,
            """
                |interface A
                |class B {
                |    companion object
                |        :<caret>
                |}
                """
        )
    }

    fun testColonOfSuperTypeListBeforeBody() {
        doTypeTest(
            ':',
            """
                |open class A
                |class B
                |<caret> {
                |}
                """,
            """
                |open class A
                |class B
                |    :<caret> {
                |}
                """
        )
    }

    fun testColonOfSuperTypeListNotNullIndent() {
        doTypeTest(
            ':',
            """
                |fun test() {
                |    open class A
                |    class B
                |    <caret>
                |}
                """,
            """
                |fun test() {
                |    open class A
                |    class B
                |        :<caret>
                |}
                """
        )
    }

    fun testChainCallContinueWithDot() {
        doTypeTest(
            '.',
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |    <caret>
                |}
                """,
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |            .<caret>
                |}
                """,
            enableKotlinObsoleteCodeStyle,
        )
    }

    fun testChainCallContinueWithDotWithOfficialCodeStyle() {
        doTypeTest(
            '.',
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |    <caret>
                |}
                """,
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |        .<caret>
                |}
                """,
        )
    }

    fun testChainCallContinueWithSafeCall() {
        doTypeTest(
            '.',
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |    ?<caret>
                |}
                """,
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |            ?.<caret>
                |}
                """,
            enableKotlinObsoleteCodeStyle
        )
    }

    fun testChainCallContinueWithSafeCallWithOfficialCodeStyle() {
        doTypeTest(
            '.',
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |    ?<caret>
                |}
                """,
            """
                |class Test{ fun test() = this }
                |fun some() {
                |    Test()
                |            ?.<caret>
                |}
                """,
            enableKotlinObsoleteCodeStyle
        )
    }

    fun testContinueWithElvis() {
        doTypeTest(
            ':',
            """
                |fun test(): Any? = null
                |fun some() {
                |    test()
                |    ?<caret>
                |}
            """,
            """
                |fun test(): Any? = null
                |fun some() {
                |    test()
                |            ?:<caret>
                |}
            """,
            enableKotlinObsoleteCodeStyle
        )
    }

    fun testContinueWithElvisWithOfficialCodeStyle() {
        doTypeTest(
            ':',
            """
                |fun test(): Any? = null
                |fun some() {
                |    test()
                |    ?<caret>
                |}
            """,
            """
                |fun test(): Any? = null
                |fun some() {
                |    test()
                |        ?:<caret>
                |}
            """
        )
    }

    fun testContinueWithOr() {
        doTypeTest(
            '|',
            """
                |fun some() {
                |    if (true
                |    |<caret>)
                |}
            """,
            """
                |fun some() {
                |    if (true
                |            ||<caret>)
                |}
            """,
            enableKotlinObsoleteCodeStyle
        )
    }

    fun testContinueWithOrWithOfficialCodeStyle() {
        doTypeTest(
            '|',
            """
                |fun some() {
                |    if (true
                |    |<caret>)
                |}
            """,
            """
                |fun some() {
                |    if (true
                |        ||<caret>)
                |}
            """
        )
    }

    fun testContinueWithAnd() {
        doTypeTest(
            '&',
            """
                |fun some() {
                |    val test = true
                |    &<caret>
                |}
            """,
            """
                |fun some() {
                |    val test = true
                |            &&<caret>
                |}
            """
        )
    }

    fun testSpaceAroundRange() {
        doTypeTest(
            '.',
            """
                | val test = 1 <caret>
                """,
            """
                | val test = 1 .<caret>
                """
        )
    }

    fun testIndentOnFinishedVariableEndAfterEquals() {
        doTypeTest(
            '\n',
            """
                |fun test() {
                |    val a =<caret>
                |    foo()
                |}
                """,
            """
                |fun test() {
                |    val a =
                |            <caret>
                |    foo()
                |}
                """,
            enableKotlinObsoleteCodeStyle
        )
    }

    fun testIndentNotFinishedVariableEndAfterEquals() {
        doTypeTest(
            '\n',
            """
                |fun test() {
                |    val a =<caret>
                |}
                """,
            """
                |fun test() {
                |    val a =
                |            <caret>
                |}
                """,
            enableKotlinObsoleteCodeStyle
        )
    }

    fun testSmartEnterBetweenOpeningAndClosingBrackets() {
        doTypeTest(
            '\n',
            """
                |fun method(<caret>) {}
                """,
            """
                |fun method(
                |        <caret>
                |) {}
                """
        ) {
            enableKotlinObsoleteCodeStyle(it)
            enableSmartEnter(it)
        }
    }

    private val enableSettingsWithInvertedAlignWhenMultiline: (CodeStyleSettings) -> Unit
        get() = {
            val settings = it.kotlinCommonSettings
            settings.ALIGN_MULTILINE_PARAMETERS = !settings.ALIGN_MULTILINE_PARAMETERS
            settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = !settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS
        }

    fun testSmartEnterWithTabsOnConstructorParametersWithInvertedAlignWhenMultiline() {
        doTypeTest(
            '\n',
            """
                |class A(
                |		a: Int,<caret>
                |)
                """,
            """
                |class A(
                |		a: Int,
                |		<caret>
                |)
                """
        ) {
            enableKotlinObsoleteCodeStyle(it)
            enableSettingsWithInvertedAlignWhenMultiline(it)
            enableSmartEnter(it)
            enableTabs(it)
        }
    }

    fun testSmartEnterWithTabsInMethodParametersWithInvertedAlignWhenMultiline() {
        doTypeTest(
            '\n',
            """
                |fun method(
                |		arg1: String,<caret>
                |) {}
                """,
            """
                |fun method(
                |		arg1: String,
                |		<caret>
                |) {}
                """
        ) {
            enableKotlinObsoleteCodeStyle(it)
            enableSettingsWithInvertedAlignWhenMultiline(it)
            enableSmartEnter(it)
            enableTabs(it)
        }
    }

    fun testSmartEnterBetweenOpeningAndClosingBracketsWithInvertedAlignWhenMultiline() {
        doTypeTest(
            '\n',
            """
                       |fun method(<caret>) {}
                       """,
            """
                       |fun method(
                       |        <caret>
                       |) {}
                       """
        ) {
            enableKotlinObsoleteCodeStyle(it)
            enableSettingsWithInvertedAlignWhenMultiline(it)
            enableSmartEnter(it)
        }
    }

    fun testValInserterOnClass() =
        testValInserter(',', """data class xxx(val x: Int<caret>)""", """data class xxx(val x: Int,<caret>)""")

    fun testValInserterOnSimpleDataClass() =
        testValInserter(',', """data class xxx(x: Int<caret>)""", """data class xxx(val x: Int,<caret>)""")

    fun testValInserterOnValWithComment() =
        testValInserter(',', """data class xxx(x: Int /*comment*/ <caret>)""", """data class xxx(val x: Int /*comment*/ ,<caret>)""")

    fun testValInserterOnValWithInitializer() =
        testValInserter(',', """data class xxx(x: Int = 2<caret>)""", """data class xxx(val x: Int = 2,<caret>)""")

    fun testValInserterOnValWithInitializerWithOutType() =
        testValInserter(',', """data class xxx(x = 2<caret>)""", """data class xxx(x = 2,<caret>)""")

    fun testValInserterOnValWithGenericType() =
        testValInserter(',', """data class xxx(x: A<B><caret>)""", """data class xxx(val x: A<B>,<caret>)""")

    fun testValInserterOnValWithNoType() =
        testValInserter(',', """data class xxx(x<caret>)""", """data class xxx(x,<caret>)""")

    fun testValInserterOnValWithIncompleteGenericType() =
        testValInserter(',', """data class xxx(x: A<B,C<caret>)""", """data class xxx(x: A<B,C,<caret>)""")

    fun testValInserterOnValWithInvalidComma() =
        testValInserter(',', """data class xxx(x:<caret> A<B>)""", """data class xxx(x:,<caret> A<B>)""")

    fun testValInserterOnValWithInvalidGenericType() =
        testValInserter(',', """data class xxx(x: A><caret>)""", """data class xxx(x: A>,<caret>)""")

    fun testValInserterOnInMultiline() =
        testValInserter(
            ',',
            """
                |data class xxx(
                |  val a: A,
                |  b: B<caret>
                |  val c: C
                |)
                """,
            """
                |data class xxx(
                |  val a: A,
                |  val b: B,<caret>
                |  val c: C
                |)
                """
        )

    fun testValInserterOnValInsertedInsideOtherParameters() =
        testValInserter(
            ',',
            """data class xxx(val a: A, b: A<caret>val c: A)""",
            """data class xxx(val a: A, val b: A,<caret>val c: A)"""
        )

    fun testValInserterOnSimpleInlineClass() =
        testValInserter(')', """inline class xxx(a: A<caret>)""", """inline class xxx(val a: A)<caret>""")

    fun testValInserterOnValInsertedWithSquare() =
        testValInserter(')', """data class xxx(val a: A, b: A<caret>)""", """data class xxx(val a: A, val b: A)<caret>""")

    fun testValInserterOnTypingMissedSquare() =
        testValInserter(')', """data class xxx(val a: A, b: A<caret>""", """data class xxx(val a: A, val b: A)<caret>""")

    fun testValInserterWithDisabledSetting() =
        testValInserter(',', """data class xxx(x: Int<caret>)""", """data class xxx(x: Int,<caret>)""", inserterEnabled = false)

    fun testMoveThroughGT() {
        myFixture.configureByText("a.kt", "val a: List<Set<Int<caret>>>")
        EditorTestUtil.performTypingAction(editor, '>')
        EditorTestUtil.performTypingAction(editor, '>')
        myFixture.checkResult("val a: List<Set<Int>><caret>")
    }

    fun testCharClosingQuote() {
        doTypeTest('\'', "val c = <caret>", "val c = ''")
    }

    fun testDontInsertExtraRBraceWithGTSymbolInTheMiddleOnTyping() {
        doTypeTest(
            ")",
            "fun foo() { if (1 > 2<caret>) {} }",
            "fun foo() { if (1 > 2)<caret> {} }",
        )
    }

    fun testDontInsertExtraRBraceWithLTSymbolInTheMiddleOnTyping() {
        doTypeTest(
            ")",
            "fun foo() { if (1 < 2<caret>) {} }",
            "fun foo() { if (1 < 2)<caret> {} }",
        )
    }


    private val enableSmartEnter: (CodeStyleSettings) -> Unit
        get() = {
            val indentOptions = it.getLanguageIndentOptions(KotlinLanguage.INSTANCE)
            indentOptions.SMART_TABS = true
        }

    private val enableTabs: (CodeStyleSettings) -> Unit
        get() = {
            val indentOptions = it.getLanguageIndentOptions(KotlinLanguage.INSTANCE)
            indentOptions.USE_TAB_CHARACTER = true
        }

    private fun doTypeTest(ch: Char, beforeText: String, afterText: String, settingsModifier: ((CodeStyleSettings) -> Unit) = { }) {
        doTypeTest(ch.toString(), beforeText, afterText, settingsModifier)
    }

    private fun doTypeTest(text: String, beforeText: String, afterText: String, settingsModifier: ((CodeStyleSettings) -> Unit) = { }) {
        configureCodeStyleAndRun(project, configurator = { settingsModifier(it) }) {
            myFixture.configureByText("a.kt", beforeText.trimMargin())
            for (ch in text) {
                myFixture.type(ch)
            }

            myFixture.checkResult(afterText.trimMargin())
        }
    }

    private fun testValInserter(ch: Char, beforeText: String, afterText: String, inserterEnabled: Boolean = true) {
        val editorOptions = KotlinEditorOptions.getInstance()
        val wasEnabled = editorOptions.isAutoAddValKeywordToDataClassParameters
        try {
            editorOptions.isAutoAddValKeywordToDataClassParameters = inserterEnabled
            doTypeTest(ch, beforeText, afterText)
        } finally {
            editorOptions.isAutoAddValKeywordToDataClassParameters = wasEnabled
        }
    }

    private fun doLtGtTestNoAutoClose(initText: String) {
        doLtGtTest(initText, false)
    }

    private fun doLtGtTest(initText: String, shouldCloseBeInsert: Boolean) {
        myFixture.configureByText("a.kt", initText)

        EditorTestUtil.performTypingAction(editor, '<')
        myFixture.checkResult(
            if (shouldCloseBeInsert) initText.replace("<caret>", "<<caret>>") else initText.replace(
                "<caret>",
                "<<caret>"
            )
        )

        EditorTestUtil.performTypingAction(editor, EditorTestUtil.BACKSPACE_FAKE_CHAR)
        myFixture.checkResult(initText)
    }

    private fun doLtGtTest(initText: String) {
        doLtGtTest(initText, true)
    }

    private val enableKotlinObsoleteCodeStyle: (CodeStyleSettings) -> Unit = {
        KotlinObsoleteCodeStyle.apply(it)
    }
}