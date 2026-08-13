// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.imports

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ref.GCUtil
import org.jetbrains.kotlin.idea.k2.codeinsight.imports.KtReferencesInCopyMap
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/**
 * Tests reference lookup after GC clears the Kotlin PSI reference cache.
 *
 * A later `getReferences()` call can create new [KtReference] instances.
 * The map must match a stored reference to its equivalent copy reference.
 *
 * [KtReferencesInCopyMap] is currently being used in Kotlin's Import Optimizer.
 *
 * Regression test for [KTIJ-39334](https://youtrack.jetbrains.com/issue/KTIJ-39334).
 */
@TestApplication
class KtReferencesInCopyMapUnderGCPressureTest {
    companion object {
        private val projectFixture: TestFixture<Project> = projectFixture()
    }

    @Test
    fun `finds reference after the reference cache is collected`() {
        lateinit var originalFile: KtFile
        lateinit var copyFile: KtFile
        lateinit var storedReference: KtReference

        runReadActionBlocking {
            originalFile = KtPsiFactory(projectFixture.get()).createFile(
                """
                    fun target() = Unit

                    fun use() {
                        target()
                    }
                """.trimIndent(),
            )
            copyFile = originalFile.copy() as KtFile
            storedReference = targetReference(originalFile)
        }

        forceReferenceRecreationOrFail(storedReference, originalFile)

        runReadActionBlocking {
            val referencesInCopy = KtReferencesInCopyMap.createFor(originalFile, copyFile)
            val referenceInCopy = referencesInCopy.findReferenceInCopy(storedReference)

            assertNotNull(referenceInCopy)
            assertSame(copyFile, referenceInCopy!!.element.containingFile)
        }
    }

    private fun forceReferenceRecreationOrFail(storedReference: KtReference, originalFile: KtFile) {
        val retries = 5

        repeat(retries) {
            GCUtil.tryGcSoftlyReachableObjects()

            if (storedReference !== runReadActionBlocking { targetReference(originalFile) }) {
                // GC succeeded, there is a new reference created
                return
            }
        }

        fail("The GC could not have cleared references after $retries retries")
    }

    private fun targetReference(file: KtFile): KtReference {
        val expression = file.collectDescendantsOfType<KtSimpleNameExpression>().single { it.text == "target" }
        return expression.references.filterIsInstance<KtReference>().single()
    }
}
