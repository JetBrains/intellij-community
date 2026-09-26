// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.Disposable
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.PairProcessor
import com.intellij.util.ref.IgnoredTraverseEntry
import com.intellij.util.ref.IgnoredTraverseReference
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Field
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

@TestApplication
class LeakHunterIgnoreReferencesTest {
    @Test
    fun `leaked disposable ignored`(): Unit = timeoutRunBlocking(60.seconds) {
        val ref = ReferenceToDisposable(TestDisposable())
        assertThrows<AssertionError> { checkReferenced(ref, listOf()) }
        assertDoesNotThrow {
            checkReferenced(
                ref,
                listOf(IgnoredTraverseReference("com.intellij.openapi.util.ReferenceToDisposable.ref", -1))
            )
        }
    }

    @Test
    fun `ignored field does not hide non ignored path to same leak`() {
        val leaked = TestDisposable()
        val ignoredField = IgnoredLeakRoot::class.java.name + ".ignored"
        val rootsSupplier: Supplier<Map<Any, String>> = Supplier {
            linkedMapOf(
                IgnoredLeakRoot(leaked) to "ignored root",
                VisibleLeakRoot(leaked) to "visible root",
            )
        }

        assertThrows<AssertionError> {
            LeakHunter.checkLeak(rootsSupplier, TestDisposable::class.java, shouldExamineField(ignoredField)) { true }
        }
    }

    @Test
    fun `ignored field suppresses leak when all paths are ignored`() {
        val leaked = TestDisposable()
        val ignoredField = IgnoredLeakRoot::class.java.name + ".ignored"

        assertDoesNotThrow {
            checkReferenced(IgnoredLeakRoot(leaked), shouldExamineField = shouldExamineField(ignoredField))
        }
    }

    private fun checkReferenced(
        root: Any,
        ignoredTraverseEntries: List<IgnoredTraverseEntry> = emptyList(),
        shouldExamineField: Predicate<Field> = Predicate { true },
    ) {
        val rootSupplier: Supplier<Map<Any, String>> = Supplier {
            mapOf(root to "root")
        }
        LeakHunter.checkLeak(rootSupplier, TestDisposable::class.java, ignoredTraverseEntries, shouldExamineField) { true }
    }

    private fun shouldExamineField(ignoredField: String): Predicate<Field> =
        Predicate { field -> field.declaringClass.name + "." + field.name != ignoredField }

    @Test
    fun testLeakHunterDoesntReportFalsePositivesFromWeakHashMap() {
        val javaMap: WeakHashMap<Any?, MyLeakData?> = WeakHashMap<Any?, MyLeakData?>()
        val disposable = AtomicReference(Any())
        javaMap[disposable.get()] = MyLeakData()

        disposable.set(null)

        LeakHunter.processLeaks(
            { mapOf(javaMap to "Standard WeakHashMap") },
            MyLeakData::class.java,
            Predicate { true },
            null,
            PairProcessor { _, _ ->
                Assertions.fail("Found a leak!")
            })
    }

    @Test
    fun testLeakHunterDoesntReportDisposedDisposablesFromDisposer() {
        val disposable: AtomicReference<Disposable.Default?> = AtomicReference(object : Disposable.Default {})
        Disposer.dispose(disposable.get()!!)
        Disposer.getDisposalTrace(disposable.get()!!).addSuppressed(MyLeakThrowable(MyLeakData()))
        //Ensure that the reference is GC'ed, even in interpreter mode
        disposable.set(null)
        LeakHunter.processLeaks(
            { mapOf(Disposer.getTree() to "Disposer.getTree()") },
            MyLeakData::class.java,
            Predicate { true },
            null,
            PairProcessor { _, _ ->
                Assertions.fail("Found a leak!")
            })
    }
}

private class MyLeakData
private class MyLeakThrowable(@Suppress("unused") private val myData: MyLeakData) : Throwable()

class TestDisposable : Disposable {
    override fun dispose() {}
}

@Suppress("unused")
private class IgnoredLeakRoot(val ignored: TestDisposable)

@Suppress("unused")
private class VisibleLeakRoot(val visible: TestDisposable)

class ReferenceToDisposable(val ref: Disposable)
