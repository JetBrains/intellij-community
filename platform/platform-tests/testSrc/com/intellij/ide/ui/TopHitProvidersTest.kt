// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ApplicationLevelProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ProjectLevelProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.NotABooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TopHitProvidersTest {
  @Rule
  @JvmField
  val projectRule = ProjectRule()

  @Rule
  @JvmField
  val edtRule = EdtRule()

  @Test
  fun uiSettings() {
    val errors = ArrayList<String>()
    checkProviders(errors, SearchTopHitProvider.EP_NAME.extensionList.asSequence().flatMap {
      if (it is ProjectLevelProvider) {
        errors.add("$it must be registered using `projectOptionsTopHitProvider` extension point")
        return@flatMap emptySequence()
      }
      (it as? ApplicationLevelProvider)?.options?.asSequence() ?: emptySequence()
    })
    checkProviders(errors, OptionsTopHitProvider.PROJECT_LEVEL_EP.extensionList.asSequence().flatMap { it.getOptions(projectRule.project) })
    assertThat(errors).isEmpty()
  }

  private fun checkProviders(errors: MutableList<String>, options: Sequence<OptionDescription>) {
    for (option in options) {
      if (option !is BooleanOptionDescription) {
        continue
      }

      try {
        val enabled = option.isOptionEnabled

        // we can't reliably restore original state for non-boolean options
        if (option is NotABooleanOptionDescription ||
            // makes sense only on Windows
            option.option == "UI: Show main menu in separate toolbar") {
          continue
        }

        option.setOptionState(!enabled)
        if (enabled == option.isOptionEnabled) {
          errors.add("Can't set " + toString(option))
        }

        // restore
        option.setOptionState(enabled)
        if (enabled != option.isOptionEnabled) {
          errors.add("Can't restore " + toString(option))
        }
      }
      catch (e: Throwable) {
        e.printStackTrace()
        errors.add("Error while testing " + toString(option) + ": " + e.message)
      }
    }
  }
}

private fun toString(booleanOption: BooleanOptionDescription): String {
  return String.format("'%s'; id: %s; %s", booleanOption.option, booleanOption.configurableId, booleanOption.javaClass)
}