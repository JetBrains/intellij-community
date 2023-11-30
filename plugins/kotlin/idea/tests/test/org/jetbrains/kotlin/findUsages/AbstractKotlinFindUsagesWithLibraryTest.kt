// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.findUsages

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractKotlinFindUsagesWithLibraryTest : AbstractFindUsagesTest() {

    open val isWithSourcesTestData: Boolean = true

    private val mockLibraryFacility = createMockLibraryFacility()

    private fun createMockLibraryFacility(): MockLibraryFacility {
        val root: String? = if (isWithSourcesTestData) KotlinTestUtils.getTestsRoot(this::class.java) else null

        return when {
            root == null -> MockLibraryFacility(source = IDEA_TEST_DATA_DIR.resolve("findUsages/libraryUsages/_library"))
            root.matches(".*(java|kotlin)Library".toRegex())  -> MockLibraryFacility(source = IDEA_TEST_DATA_DIR.resolve("findUsages/libraryUsages/_library"))
            else -> MockLibraryFacility(source = File("$root/_library"))
        }
    }

    override fun setUp() {
        super.setUp()
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override val ignoreLog: Boolean
        get() = true

}
