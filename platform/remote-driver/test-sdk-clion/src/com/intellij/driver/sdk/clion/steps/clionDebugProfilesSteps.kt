package com.intellij.driver.sdk.clion.steps

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitForOne
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val IdeaFrameUI.clionDebugProfilesSteps get() = ClionDebugProfilesSteps(this)

class ClionDebugProfilesSteps(private val ideFrame: IdeaFrameUI) {
  private val settingsPath = arrayOf("Build, Execution, Deployment", "Debugger", "Debug Profiles")

  val debugProfileSwitcher: UiComponent
    get() = ideFrame.mainToolbar.x { byClass("ComboBoxButton") }

  private val SettingsDialogUiComponent.addProfileButton
    get() = actionButton { and(byClass("ActionButton"), byTooltip("Add Profile")) }

  private val SettingsDialogUiComponent.removeProfileButton
    get() = actionButton { and(byClass("ActionButton"), byTooltip("Delete Profile")) }

  private val SettingsDialogUiComponent.copyProfileButton
    get() = actionButton { and(byClass("ActionButton"), byTooltip("Copy Profile")) }

  private val SettingsDialogUiComponent.profileNameField
    get() = textField { and(byAccessibleName("Name:"), byClass("JBTextField")) }

  private val SettingsDialogUiComponent.profilesTree
    get() = content().tree(xQuery { byClass("Tree") })

  private val SettingsDialogUiComponent.configureProfileInJsonButton
    get() = button("Configure Profile in .json")

  private fun SettingsDialogUiComponent.setProfileName(name: String) {
    profileNameField.text = name
    profilesTree.waitContainsText(name, timeout = 10.seconds)
  }

   private fun SettingsDialogUiComponent.createProfileAndSelect(create: () -> Unit) {
    val existingProfiles = profilesTree.collectExpandedPaths().map { it.path.last() }
    create()
    val createdProfile = waitForOne(
      message = "A new debug profile appears in the tree",
      timeout = 10.seconds,
      errorMessage = { found -> "Expected one new debug profile in the tree.\n\tBefore: $existingProfiles\n\tNow: $found" },
      getter = { profilesTree.collectExpandedPaths().map { it.path.last() } },
      checker = { it !in existingProfiles },
    )
    profilesTree.clickRow { it == createdProfile }
  }


  fun getDebugProfilesFromSwitcher(): List<String> = ideFrame.run {
    debugProfileSwitcher.waitFound(timeout = 30.seconds).click()
    popup().waitFound(timeout = 10.seconds).getAllTexts().map { it.text }.also { keyboard { escape() } }
  }

  fun getSelectedDebugProfile(): String = ideFrame.run {
    debugProfileSwitcher.waitFound(timeout = 30.seconds).click()
    popup().waitFound(timeout = 10.seconds).list().run {
      val selectedIndex = rawItems.indices.last { index -> collectIconsAtIndex(index).any { it.contains("checked.svg") } }
      rawItems[selectedIndex].removeSurrounding("<html>", "</html>")
    }.also { keyboard { escape() } }
  }

  fun selectDebugProfile(profile: String) {
    ideFrame.run {
      step("Select debug profile '$profile' in the switcher") {
        debugProfileSwitcher.waitFound(timeout = 10.seconds).click()
        popup().waitFound(timeout = 10.seconds).waitOneText(profile, timeout = 10.seconds).click()
        waitForIndicators(1.minutes)
      }
    }
  }

  fun openDebugProfilesSettings() {
    ideFrame.run {
      openSettingsDialog()
      settingsDialog { openTreeSettingsSection(*settingsPath) }
    }
  }

  fun saveAndCloseSettings() {
    ideFrame.settingsDialog { okButton.click() }
  }

  // Requires the Debug Profiles settings page to be open (see openDebugProfilesSettings).
  fun addDebugProfile(name: String, type: String) {
    ideFrame.run {
      step("Add debug profile '$name' of type '$type'") {
        settingsDialog {
          createProfileAndSelect {
            addProfileButton.click()
            popup().waitFound(timeout = 10.seconds).waitOneText(type, timeout = 10.seconds).click()
          }
          setProfileName(name)
        }
      }
    }
  }

  // Requires the Debug Profiles settings page to be open (see openDebugProfilesSettings).
  fun removeDebugProfile(name: String) {
    ideFrame.run {
      step("Remove debug profile '$name'") {
        settingsDialog {
          profilesTree.clickPath(name, fullMatch = false)
          removeProfileButton.click()
        }
      }
    }
  }

  // Requires the Debug Profiles settings page to be open with [sourceProfile] present (see openDebugProfilesSettings).
  fun copyDebugProfile(name: String, sourceProfile: String) {
    ideFrame.run {
      step("Copy the '$sourceProfile' debug profile as '$name'") {
        settingsDialog {
          createProfileAndSelect {
            profilesTree.clickPath(sourceProfile, fullMatch = false)
            copyProfileButton.click()
          }
          setProfileName(name)
        }
      }
    }
  }

  fun configureProfileInJson(name: String) {
    ideFrame.run {
      step("Open debug profile '$name' and click 'Configure Profile in .json'") {
        settingsDialog {
          profilesTree.clickPath(name, fullMatch = false)
          configureProfileInJsonButton.click()
        }
      }
    }
  }

  // Requires the Debug Profiles settings page to be open (see openDebugProfilesSettings).
  fun getSuggestedDebuggers(): List<String> = ideFrame.run {
    settingsDialog { addProfileButton.click() }
    popup().waitFound(timeout = 10.seconds).getAllTexts().map { it.text }.also { keyboard { escape() } }
  }
}
