// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.io.File

abstract class KtCompanionObjectInExtensionInspectionTestBase : LightDevKitInspectionFixTestBase(),
                                                                ExpectedPluginModeProvider {

  override fun getFileExtension(): String = "kt"

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    myFixture.addClass(
      """
        package com.intellij.openapi.extensions; 
        
        public class ExtensionPointName<T> { 
          public ExtensionPointName(String name) { }
        }
      """.trimIndent()
    )

    myFixture.addClass(
      """
        import com.intellij.openapi.extensions.ExtensionPointName;
        
        public interface MyExtension {
          ExtensionPointName<MyExtension> EP_NAME = new ExtensionPointName<>("com.intellij.example.myExtension");
        }
      """.trimIndent()
    )

    myFixture.addClass(
      """
        package com.intellij.openapi.components;
        
        public @interface Service { }
      """.trimIndent()
    )

    myFixture.addClass(
      """
        package com.intellij.openapi.diagnostic;

        public class Logger { }
      """.trimIndent()
    )

    myFixture.addClass(
      """      
      public @interface MyAnnotation { }
      """.trimIndent()
    )

    myFixture.addClass(
      """
      package kotlin.jvm;

      public @interface JvmStatic { }
      """.trimIndent()
    )

    myFixture.configureByFile("plugin.xml")
    myFixture.enableInspections(CompanionObjectInExtensionInspection())
  }

  protected fun doTestFixWithReferences(fixName: String, refFileExtension: String = fileExtension) {
    val (fileNameBefore, fileNameAfter) = getBeforeAfterFileNames()
    val (referencesFileNameBefore, referencesFileNameAfter) = getBeforeAfterFileNames(suffix = "references", extension = refFileExtension)
    myFixture.testHighlighting(fileNameBefore, referencesFileNameBefore)
    val intention: IntentionAction = myFixture.findSingleIntention(fixName)
    myFixture.launchAction(intention)
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
    myFixture.checkResultByFile(referencesFileNameBefore, referencesFileNameAfter, true)
  }

  protected fun doTestFixWithConflicts(fixName: String, expectedConflicts: List<String>) {
    val fileName = "${getTestName(false)}.$fileExtension"
    myFixture.testHighlighting(fileName)
    val intention: IntentionAction = myFixture.findSingleIntention(fixName)
    try {
      myFixture.launchAction(intention)
      fail("Expected ConflictsInTestsException exception te be thrown.")
    }
    catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
      UsefulTestCase.assertSameElements(e.messages, expectedConflicts)
    }
  }

  private fun getBeforeAfterFileNames(
    testName: String = getTestName(false),
    suffix: String? = null,
    extension: String = fileExtension,
  ): Pair<String, String> {
    val resultName = testName + suffix?.let { "_$it" }.orEmpty()
    val beforeName = "${resultName}.$extension"

    val k2FileName = "${resultName}_after.k2.$extension"
    val k2FilePath = getTestDataPath() + "/" + k2FileName
    if (KotlinPluginModeProvider.isK2Mode() && File(k2FilePath).exists()) {
      return beforeName to k2FileName
    }
    return beforeName to "${resultName}_after.$extension"
  }
}
