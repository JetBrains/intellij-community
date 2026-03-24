// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared

import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.k2.inspections.tests.AbstractK2LocalInspectionTest
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("code-insight/inspections-shared/tests/k2")
@TestMetadata("../testData/inspectionsLocal/replaceAssociateFunction/associateWith/multipackage")
internal class AssociateConvertionsMultipackageTest : AbstractK2LocalInspectionTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override val inspectionFileName: String = ".inspection"

    @TestMetadata("explicitImport.kt")
    fun testExplicitImport() {
        doTest(fileName())
    }

    @TestMetadata("noExplicitImport.kt")
    fun testNoExplicitImport() {
        doTest(fileName())
    }

}
