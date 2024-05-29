// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinCodeFragmentContextModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinGlobalModuleStateModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinGlobalSourceModuleStateModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinGlobalSourceOutOfBlockModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModificationEventKind
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleOutOfBlockModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleStateModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.analysis.providers.topics.isModuleLevel
import org.junit.Assert

/**
 * Tracks modification events published during its lifetime. [assertNotModified], [assertModified], and [assertModifiedOnce] refer to the
 * [expectedEventKind] -- that is, the modification tracker primarily checks if a modification event with that exact kind was or was not
 * received.
 *
 * [allowedEventKinds] can be additionally specified as "side effect" events which are expected to be , such as a global out-of-block
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
        val ktModule: KtModule? = null,
        val isRemoval: Boolean = false,
    ) {
        override fun toString(): String = buildString {
            append(kind)
            if (ktModule != null) {
                append(" in $ktModule")
            }
            if (isRemoval) {
                append(" (removal)")
            }
        }
    }

    private val expectedEvents = mutableListOf<ReceivedEvent>()

    private val forbiddenEvents = mutableListOf<ReceivedEvent>()

    init {
        Disposer.register(testRootDisposable, this)

        val busConnection = project.analysisMessageBus.connect(this)
        busConnection.subscribe(
            KotlinTopics.MODULE_STATE_MODIFICATION,
            KotlinModuleStateModificationListener { module, modificationKind ->
                handleReceivedEvent(
                    ReceivedEvent(
                        KotlinModificationEventKind.MODULE_STATE_MODIFICATION,
                        module,
                        modificationKind == KotlinModuleStateModificationKind.REMOVAL,
                    )
                )
            },
        )
        busConnection.subscribe(
            KotlinTopics.MODULE_OUT_OF_BLOCK_MODIFICATION,
            KotlinModuleOutOfBlockModificationListener { module ->
                handleReceivedEvent(KotlinModificationEventKind.MODULE_OUT_OF_BLOCK_MODIFICATION, module)
            },
        )
        busConnection.subscribe(
            KotlinTopics.GLOBAL_MODULE_STATE_MODIFICATION,
            KotlinGlobalModuleStateModificationListener {
                handleReceivedEvent(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION)
            },
        )
        busConnection.subscribe(
            KotlinTopics.GLOBAL_SOURCE_MODULE_STATE_MODIFICATION,
            KotlinGlobalSourceModuleStateModificationListener {
                handleReceivedEvent(KotlinModificationEventKind.GLOBAL_SOURCE_MODULE_STATE_MODIFICATION)
            },
        )
        busConnection.subscribe(
            KotlinTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION,
            KotlinGlobalSourceOutOfBlockModificationListener {
                handleReceivedEvent(KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION)
            },
        )
        busConnection.subscribe(
            KotlinTopics.CODE_FRAGMENT_CONTEXT_MODIFICATION,
            KotlinCodeFragmentContextModificationListener { module ->
                handleReceivedEvent(KotlinModificationEventKind.CODE_FRAGMENT_CONTEXT_MODIFICATION, module)
            },
        )
    }

    private fun handleReceivedEvent(kind: KotlinModificationEventKind) {
        handleReceivedEvent(ReceivedEvent(kind))
    }

    private fun handleReceivedEvent(kind: KotlinModificationEventKind, ktModule: KtModule?) {
        handleReceivedEvent(ReceivedEvent(kind, ktModule))
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
        Assert.assertTrue(
            "`$expectedEventName` events for $label should not have been published, but ${expectedEvents.size} events were received.",
            expectedEvents.isEmpty(),
        )
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
        Assert.assertTrue(
            "A single `$expectedEventName` event for $label should have been published, but ${expectedEvents.size} events were received.",
            expectedEvents.size == 1,
        )
        checkShouldBeRemoval(shouldBeRemoval)
        checkForbiddenEvents()
    }

    private fun checkShouldBeRemoval(shouldBeRemoval: Boolean) {
        val shouldOrShouldNot = if (shouldBeRemoval) "should" else "should not"
        expectedEvents.forEachIndexed { index, receivedEvent ->
            Assert.assertTrue(
                "The `$expectedEventName` event #$index for $label $shouldOrShouldNot be a removal event.",
                receivedEvent.isRemoval == shouldBeRemoval,
            )
        }
    }

    private fun checkForbiddenEvents() {
        if (forbiddenEvents.isEmpty()) return

        Assert.fail(
            "The following forbidden events for $label should not have been published:\n- ${forbiddenEvents.joinToString("\n -")}"
        )
    }

    override fun dispose() { }
}

class ModuleModificationEventTracker(
    private val ktModule: KtModule,
    label: String,
    expectedEventKind: KotlinModificationEventKind,
    allowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    testRootDisposable: Disposable,
) : ModificationEventTracker(ktModule.project, label, expectedEventKind, allowedEventKinds, testRootDisposable) {
    init {
        require(expectedEventKind.isModuleLevel)
    }

    override fun handleReceivedEvent(receivedEvent: ReceivedEvent) {
        // Ignore all module-level events published for other modules. The modification event trackers for these other modules should track
        // their respective modification events.
        if (receivedEvent.kind.isModuleLevel && receivedEvent.ktModule != ktModule) {
            return
        }

        super.handleReceivedEvent(receivedEvent)
    }
}
