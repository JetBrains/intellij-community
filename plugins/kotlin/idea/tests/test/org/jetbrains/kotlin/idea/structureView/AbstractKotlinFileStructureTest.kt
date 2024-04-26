// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.util.FileStructurePopup
import com.intellij.ide.util.InheritedMembersNodeProvider
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.completion.test.configureByFilesWithSuffixes
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractKotlinFileStructureTest : KotlinFileStructureTestBase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override val fileExtension: String
        get() = fileName().substringAfter(".")

    override val treeFileName: String get() = getFileName("after")

    fun doTest(path: String) {
        myFixture.configureByFilesWithSuffixes(dataFile(), testDataDirectory, ".Data")

        popupFixture.popup.setup()

        checkTree()
    }

    protected fun FileStructurePopup.setup() {
        val fileText = FileUtil.loadFile(File(testDataDirectory, fileName()), true)

        val withInherited = InTextDirectivesUtils.isDirectiveDefined(fileText, "WITH_INHERITED")
        setTreeActionState(nodeProviderClass(), withInherited)
    }

    protected open fun nodeProviderClass(): Class<out InheritedMembersNodeProvider<*>> = KotlinInheritedMembersNodeProvider::class.java
}
