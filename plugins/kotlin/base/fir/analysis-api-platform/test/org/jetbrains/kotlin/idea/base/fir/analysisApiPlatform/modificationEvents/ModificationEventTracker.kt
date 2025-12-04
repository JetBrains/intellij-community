// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.*
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.junit.Assert

/**
 * Tracks modification events published during its lifetime. [assertNotModified], [assertModified], and [assertModifiedOnce] refer to the
 * [expectedEventKind] -- that is, the modification tracker primarily checks if a modification event with that exact kind was or was not
 * received.
 *
 * [allowedEventKinds] can be additionally specified as "side effect" events which might be published, such as a global out-of-block
 * modification event after a module roots change. The modification event tracker understands **all** other event kinds as *forbidden* and
 * will fail the test if such an event is encountered.
 */
open class ModificationEventTracker(
    protected val project: Project,
    protected val label: String,
    protected val expectedEventKind: KotlinModificationEventKind,
    protected val allowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    testRootDisposable: Disposable,
) : Disposable {
    protected class ReceivedEvent(
        val kind: KotlinModificationEventKind,
        val module: KaModule? = null,
        val isRemoval: Boolean = false,
    ) {
        private val stackTrace: String = Thread.currentThread().stackTrace
            .drop(1) // Drop `getStackTrace` itself.
            .takeWhile { !it.className.startsWith("junit.framework.") } // Stop at JUnit frames.
            .joinToString("\n") { "  at $it" }

        override fun toString(): String = buildString {
            append(kind)
            if (module != null) {
                append(" in $module")
            }
            if (isRemoval) {
                append(" (removal)")
            }
        }

        fun toStringWithStackTrace(): String = buildString {
            appendLine(this@ReceivedEvent.toString())
            appendLine("--------")
            appendLine("Event stack trace:")
            appendLine(stackTrace)
            appendLine("--------")
        }
    }

    private val expectedEvents = mutableListOf<ReceivedEvent>()

    private val forbiddenEvents = mutableListOf<ReceivedEvent>()

    init {
        Disposer.register(testRootDisposable, this)

        project.analysisMessageBus.connect(this).subscribe(
            KotlinModificationEvent.TOPIC,
            KotlinModificationEventListener { event ->
                when (event) {
                    is KotlinModuleStateModificationEvent ->
                        handleReceivedEvent(
                            ReceivedEvent(
                                KotlinModificationEventKind.MODULE_STATE_MODIFICATION,
                                event.module,
                                event.modificationKind == KotlinModuleStateModificationKind.REMOVAL,
                            )
                        )

                    is KotlinModuleOutOfBlockModificationEvent ->
                        handleReceivedEvent(KotlinModificationEventKind.MODULE_OUT_OF_BLOCK_MODIFICATION, event.module)

                    is KotlinGlobalModuleStateModificationEvent ->
                        handleReceivedEvent(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION)

                    is KotlinGlobalSourceModuleStateModificationEvent ->
                        handleReceivedEvent(KotlinModificationEventKind.GLOBAL_SOURCE_MODULE_STATE_MODIFICATION)

                    is KotlinGlobalScriptModuleStateModificationEvent ->
                        handleReceivedEvent(KotlinModificationEventKind.GLOBAL_SCRIPT_MODULE_STATE_MODIFICATION)

                    is KotlinGlobalSourceOutOfBlockModificationEvent ->
                        handleReceivedEvent(KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION)

                    is KotlinCodeFragmentContextModificationEvent ->
                        handleReceivedEvent(KotlinModificationEventKind.CODE_FRAGMENT_CONTEXT_MODIFICATION, event.module)
                }
            }
        )
    }

    private fun handleReceivedEvent(kind: KotlinModificationEventKind) {
        handleReceivedEvent(ReceivedEvent(kind))
    }

    private fun handleReceivedEvent(kind: KotlinModificationEventKind, module: KaModule?) {
        handleReceivedEvent(ReceivedEvent(kind, module))
    }

    protected open fun handleReceivedEvent(receivedEvent: ReceivedEvent) {
        if (receivedEvent.kind == expectedEventKind) {
            expectedEvents.add(receivedEvent)
        } else if (receivedEvent.kind !in allowedEventKinds) {
            forbiddenEvents.add(receivedEvent)
        }
    }

    private val expectedEventName: String get() = expectedEventKind.name

    fun assertNotModified() {
        if (expectedEvents.isNotEmpty()) {
            val eventsWithStackTraces = expectedEvents.joinToString("\n\n") { it.toStringWithStackTrace() }
            Assert.fail(
                "`$expectedEventName` events for $label should not have been published, but ${expectedEvents.size} events were received:\n\n$eventsWithStackTraces"
            )
        }
        checkForbiddenEvents()
    }

    fun assertModified(shouldBeRemoval: Boolean = false) {
        Assert.assertTrue(
            "At least one `$expectedEventName` event for $label should have been published, but no events were received.",
            expectedEvents.isNotEmpty(),
        )
        checkShouldBeRemoval(shouldBeRemoval)
        checkForbiddenEvents()
    }

    fun assertModifiedOnce(shouldBeRemoval: Boolean = false) {
        assertModifiedExactly(times = 1, shouldBeRemoval = shouldBeRemoval)
    }

    fun assertModifiedExactly(times: Int, shouldBeRemoval: Boolean = false) {
        if (expectedEvents.size != times) {
            val numberText = if (times == 1) "A single" else "Exactly $times"
            val eventsText = if (times == 1) "event" else "events"

            Assert.fail(
                "$numberText `$expectedEventName` $eventsText for $label should have been published," +
                        " but ${expectedEvents.size} events were received.",
            )
        }

        checkShouldBeRemoval(shouldBeRemoval)
        checkForbiddenEvents()
    }

    private fun checkShouldBeRemoval(shouldBeRemoval: Boolean) {
        val shouldOrShouldNot = if (shouldBeRemoval) "should" else "should not"
        expectedEvents.forEachIndexed { index, receivedEvent ->
            if (receivedEvent.isRemoval != shouldBeRemoval) {
                Assert.fail(
                    "The `$expectedEventName` event #$index for $label $shouldOrShouldNot be a removal event:\n\n${receivedEvent.toStringWithStackTrace()}"
                )
            }
        }
    }

    private fun checkForbiddenEvents() {
        if (forbiddenEvents.isEmpty()) return

        val eventsWithStackTraces = forbiddenEvents.joinToString("\n\n") { it.toStringWithStackTrace() }
        Assert.fail(
            "The following forbidden events for '$label' should not have been published:\n\n$eventsWithStackTraces"
        )
    }

    override fun dispose() { }
}

class ModuleModificationEventTracker(
    private val module: KaModule,
    label: String,
    expectedEventKind: KotlinModificationEventKind,
    allowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    testRootDisposable: Disposable,
) : ModificationEventTracker(module.project, label, expectedEventKind, allowedEventKinds, testRootDisposable) {
    init {
        require(expectedEventKind.isModuleLevel)
    }

    override fun handleReceivedEvent(receivedEvent: ReceivedEvent) {
        // Ignore all module-level events published for other modules. The modification event trackers for these other modules should track
        // their respective modification events.
        if (receivedEvent.kind.isModuleLevel && receivedEvent.module != module) {
            return
        }

        super.handleReceivedEvent(receivedEvent)
    }
}
