// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k1.completion

import com.intellij.compose.ide.plugin.shared.completion.ComposeModifierCompletionContributorTest
import com.intellij.compose.ide.plugin.shared.util.configureByText
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

internal class K1ComposeModifierCompletionContributorTest : ComposeModifierCompletionContributorTest() {
  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K1

  // outputs slightly differ on K1 and K2, so we have some separate test implementations.

  fun testModifierImportAlias() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier as Modifier1
  
        @Composable
        fun myWidget() {
            myWidgetWithModifier(Modifier1.<caret>
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "extensionFunction"
    lookupStrings shouldContain "extensionFunctionReturnsNonModifier"
    lookupStrings.indexOf("extensionFunction") shouldBe 0

    // Do
    completeWith("extensionFunction")

    // Check
    myFixture.checkResult(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
        import androidx.compose.ui.Modifier as Modifier1
  
        @Composable
        fun myWidget() {
            myWidgetWithModifier(Modifier1.extensionFunction()
        }
      """.trimIndent()
    )
  }

  fun testModifierImportAliasForProperty() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier as Modifier1
  
        @Composable
        fun myWidget() {
            val myModifier:Modifier1 = <caret>
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "Modifier.extensionFunction"
    // If the user didn't type 'Modifier' don't suggest extensions that don't return Modifier.
    lookupStrings shouldNotContain "Modifier.extensionFunctionReturnsNonModifier"

    // Do
    myFixture.type("extensionFunction\t")

    // Check
    myFixture.checkResult(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
        import androidx.compose.ui.Modifier as Modifier1
        
        @Composable
        fun myWidget() {
            val myModifier:Modifier1 = Modifier.extensionFunction()
        }
      """.trimIndent()
    )
  }
}
