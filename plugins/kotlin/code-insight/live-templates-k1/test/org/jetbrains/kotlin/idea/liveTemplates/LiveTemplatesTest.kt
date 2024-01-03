// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.DataManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.util.*

@TestRoot("code-insight/live-templates-k1")
@TestMetadata("testData/simple")
@RunWith(JUnit38ClassRunner::class)
class LiveTemplatesTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.K1

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    @TestMetadata("sout.kt")
    fun testSout() {
        parameterless()
    }

    @TestMetadata("sout_BeforeCall.kt")
    fun testSout_BeforeCall() {
        parameterless()
    }

    @TestMetadata("sout_BeforeCallSpace.kt")
    fun testSout_BeforeCallSpace() {
        parameterless()
    }

    @TestMetadata("sout_BeforeBinary.kt")
    fun testSout_BeforeBinary() {
        parameterless()
    }

    @TestMetadata("sout_InCallArguments.kt")
    fun testSout_InCallArguments() {
        parameterless()
    }

    @TestMetadata("sout_BeforeQualifiedCall.kt")
    fun testSout_BeforeQualifiedCall() {
        parameterless()
    }

    @TestMetadata("sout_AfterSemicolon.kt")
    fun testSout_AfterSemicolon() {
        parameterless()
    }

    @TestMetadata("soutf.kt")
    fun testSoutf() {
        parameterless()
    }

    @TestMetadata("soutf_InCompanion.kt")
    fun testSoutf_InCompanion() {
        parameterless()
    }

    @TestMetadata("serr.kt")
    fun testSerr() {
        parameterless()
    }

    @TestMetadata("main.kt")
    fun testMain() {
        parameterless()
    }

    @TestMetadata("maina.kt")
    fun testMaina() {
        parameterless()
    }

    @TestMetadata("soutv.kt")
    fun testSoutv() {
        start()

        assertStringItems("DEFAULT_BUFFER_SIZE", "args", "x", "y")
        typeAndNextTab("y.plus(\"test\")")

        checkAfter()
    }

    @TestMetadata("soutp.kt")
    fun testSoutp() {
        parameterless()
    }

    @TestMetadata("fun0.kt")
    fun testFun0() {
        start()

        type("foo")
        nextTab(2)

        checkAfter()
    }

    @TestMetadata("fun1.kt")
    fun testFun1() {
        start()

        type("foo")
        nextTab(4)

        checkAfter()
    }

    @TestMetadata("fun2.kt")
    fun testFun2() {
        start()

        type("foo")
        nextTab(6)

        checkAfter()
    }

    @TestMetadata("exfun.kt")
    fun testExfun() {
        start()

        typeAndNextTab("Int")
        typeAndNextTab("foo")
        typeAndNextTab("arg : Int")
        nextTab()

        checkAfter()
    }

    @TestMetadata("exval.kt")
    fun testExval() {
        start()

        typeAndNextTab("Int")
        nextTab()
        typeAndNextTab("Int")

        checkAfter()
    }

    @TestMetadata("exvar.kt")
    fun testExvar() {
        start()

        typeAndNextTab("Int")
        nextTab()
        typeAndNextTab("Int")

        checkAfter()
    }

    @TestMetadata("closure.kt")
    fun testClosure() {
        start()

        typeAndNextTab("param")
        nextTab()

        checkAfter()
    }

    @TestMetadata("interface.kt")
    fun testInterface() {
        start()

        typeAndNextTab("SomeTrait")

        checkAfter()
    }

    @TestMetadata("singleton.kt")
    fun testSingleton() {
        start()

        typeAndNextTab("MySingleton")

        checkAfter()
    }

    @TestMetadata("void.kt")
    fun testVoid() {
        start()

        typeAndNextTab("foo")
        typeAndNextTab("x : Int")

        checkAfter()
    }

    @TestMetadata("iter.kt")
    fun testIter() {
        start()

        assertStringItems("args", "myList", "o", "str")
        type("args")
        nextTab(2)

        checkAfter()
    }

    @TestMetadata("iter_ForKeywordVariable.kt")
    fun testIter_ForKeywordVariable() {
        start()
        nextTab(2)
        checkAfter()
    }

    @TestMetadata("anonymous_1.kt")
    fun testAnonymous_1() {
        start()

        typeAndNextTab("Runnable")

        checkAfter()
    }

    @TestMetadata("anonymous_2.kt")
    fun testAnonymous_2() {
        start()

        typeAndNextTab("Thread")

        checkAfter()
    }

    @TestMetadata("object_ForClass.kt")
    fun testObject_ForClass() {
        start()

        typeAndNextTab("A")

        checkAfter()
    }

    @TestMetadata("ifn.kt")
    fun testIfn() {
        doTestIfnInn()
    }

    @TestMetadata("inn.kt")
    fun testInn() {
        doTestIfnInn()
    }

    private fun doTestIfnInn() {
        start()

        assertStringItems("b", "t", "y")
        typeAndNextTab("b")

        checkAfter()
    }

    private fun parameterless() {
        start()

        checkAfter()
    }

    private fun start() {
        myFixture.configureByDefaultFile()
        myFixture.type(templateName)

        doAction("ExpandLiveTemplateByTab")
    }

    private val templateName: String
        get() {
            val testName = getTestName(true)
            if (testName.contains("_")) {
                return testName.substring(0, testName.indexOf("_"))
            }
            return testName
        }

    private fun checkAfter() {
        TestCase.assertNull(templateState)
        myFixture.checkContentByExpectedPath(".exp")
    }

    private fun typeAndNextTab(s: String) {
        type(s)
        nextTab()
    }

    private fun type(s: String) {
        myFixture.type(s)
    }

    private fun nextTab() {
        val project = project
        UIUtil.invokeAndWaitIfNeeded(Runnable {
            CommandProcessor.getInstance().executeCommand(
                project,
                {
                    templateState!!.nextTab()
                },
                "nextTab",
                null
            )
        })
    }

    private fun nextTab(times: Int) {
        for (i in 0 until times) {
            nextTab()
        }
    }

    private val templateState: TemplateState?
        get() = TemplateManagerImpl.getTemplateState(myFixture.editor)

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinJvmLightProjectDescriptor.DEFAULT
    }

    private fun doAction(@Suppress("SameParameterValue") actionId: String) {
        val actionManager = EditorActionManager.getInstance()
        val actionHandler = actionManager.getActionHandler(actionId)
        actionHandler.execute(
            myFixture.editor, myFixture.editor.caretModel.currentCaret,
            DataManager.getInstance().getDataContext(myFixture.editor.component)
        )
    }

    private fun assertStringItems(@NonNls vararg items: String) {
        TestCase.assertEquals(listOf(*items), listOf(*itemStringsSorted))
    }

    private val itemStrings: Array<String>
        get() {
            val lookup = LookupManager.getActiveLookup(myFixture.editor)!!
            val result = ArrayList<String>()
            for (element in lookup.items) {
                result.add(element.lookupString)
            }
            return ArrayUtil.toStringArray(result)
        }

    private val itemStringsSorted: Array<String>
        get() {
            val items = itemStrings
            Arrays.sort(items)
            return items
        }
}
