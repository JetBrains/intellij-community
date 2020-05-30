package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.runInEdtAndWait
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.FeatureSuggestersManagerListener
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class FeatureSuggesterTest : LightJavaCodeInsightFixtureTestCase() {

    class TestSuggestersExecutionPolicy : IdeaTestExecutionPolicy() {
        override fun getName(): String = "TestSuggestersExecutionPolicy"

        override fun runInDispatchThread(): Boolean = false
    }

    init {
        System.setProperty("idea.test.execution.policy", TestSuggestersExecutionPolicy::class.java.name)
    }

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    fun testSuggestionFound(doTestActions: FeatureSuggesterTest.() -> Unit, assert: (PopupSuggestion) -> Boolean) {
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        val testPassed = AtomicBoolean(false)
        project.messageBus.connect()
            .subscribe(FeatureSuggestersManagerListener.TOPIC, object : FeatureSuggestersManagerListener {
                override fun featureFound(suggestion: PopupSuggestion) {
                    if (!testPassed.get()) {
                        lock.withLock {
                            val result = assert(suggestion)
                            testPassed.set(result)
                            condition.signal()
                        }
                    }
                }
            })

        doTestActions()
        lock.withLock {
            condition.await(10, TimeUnit.SECONDS)
        }
        println(myFixture.file.text)
        if (!testPassed.get()) {
            TestCase.fail()
        }
    }

    fun testSuggestionNotFound(doTestActions: FeatureSuggesterTest.() -> Unit) {
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        val testPassed = AtomicBoolean(false)
        project.messageBus.connect()
            .subscribe(FeatureSuggestersManagerListener.TOPIC, object : FeatureSuggestersManagerListener {
                override fun featureFound(suggestion: PopupSuggestion) {
                    if (!testPassed.get()) {
                        // we are doing something only if current test is running
                        // because this listeners will be alive in all tests
                        lock.withLock {
                            println(myFixture.file.text)
                            TestCase.fail()
                        }
                    }
                }
            })

        doTestActions()
        lock.withLock {
            condition.await(10, TimeUnit.SECONDS)
        }
        testPassed.set(true)

        println(myFixture.file.text)
    }

    fun moveCaretRelatively(columnShift: Int, lineShift: Int, withSelection: Boolean) {
        runInEdtAndWait {
            editor.caretModel.moveCaretRelatively(columnShift, lineShift, withSelection, false, true)
        }
    }

    /**
     * Removes text from current caret position according to column and line shift
     * Implemented by selecting text and typing space symbol
     */
    fun removeSymbols(columnShift: Int, lineShift: Int) {
        moveCaretRelatively(columnShift, lineShift, true)
        myFixture.type(' ')
    }

}