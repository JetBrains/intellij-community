package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.dialogs.newProjectDialog
import com.intellij.driver.sdk.ui.components.common.dialogs.restartToUpdateConfirmationDialogUi
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.ui.notPresent
import com.intellij.driver.sdk.ui.shouldBe
import kotlin.time.Duration.Companion.seconds

val Driver.pluginSteps get() = PluginSteps(this)

class PluginSteps(private val driver: Driver) {

  fun disablePlugin(pluginToDisableName: String) {
    driver.run {
      welcomeScreen {
        step("Disable ${pluginToDisableName} plugin") {
          openPluginsSettings()
          pluginsDialog {
            pluginsSettingsPage {
              openInstalledTab()
              waitLoaded()
              getPluginFromList(pluginToDisableName).enabledControl.run {
                waitSelected(true)
                uncheck()
              }
              okButton.click()
            }
          }
        }
      }
    }
    postponeRestartIfNeeded()
  }

  fun enablePlugin(pluginToEnableName: String) {
    driver.run {
      welcomeScreen {
        step("Enable ${pluginToEnableName} plugin") {
          openPluginsSettings()
          pluginsDialog {
            pluginsSettingsPage {
              openInstalledTab()
              waitLoaded()
              getPluginFromList(pluginToEnableName).enabledControl.run {
                waitSelected(false)
                check()
              }
              okButton.click()
            }
          }
        }
      }
    }
    postponeRestartIfNeeded()
  }

  fun checkPluginsState(plugins: List<String>, expectedStateIsEnabled: Boolean = false) {
    driver.run {
      welcomeScreen {
        step("Verify plugins ${plugins.joinToString(", ")} state after disabling/enabling the dependent plugin") {
          openPluginsSettings()
          pluginsDialog {
            pluginsSettingsPage {
              openInstalledTab()
              waitLoaded()
              plugins.forEach { plugin ->
                getPluginFromList(plugin).enabledControl.waitSelected(expectedStateIsEnabled, timeout = 10.seconds)
              }
              okButton.click()
            }
          }
        }
      }
    }
    postponeRestartIfNeeded()
  }

  fun checkPluginProjectTemplate(pluginTemplateProjectName: String, isPresent: Boolean = true) {
    driver.run {
      step("Verify ${pluginTemplateProjectName} project template is ${if (isPresent) "present" else "not present"} on the welcome screen") {
        welcomeScreen {
          waitFound(30.seconds)
          createNewProjectButton.click()
        }

        newProjectDialog {
          x("//div[@class='JBList']").waitFound()

          if (isPresent) x("//div[@class='JBList']").waitOneText(pluginTemplateProjectName).click()
          else x("//div[@class='JBList']//div[@text='${pluginTemplateProjectName}']").shouldBe(notPresent)

          cancelButton.click()
        }
      }
    }
  }

  fun checkPluginRequiresInstallationOrLoading(pluginToCheckName: String, pluginThatRequiredName: String = "") {
    driver.run {
      welcomeScreen {
        step("Verify ${pluginToCheckName} plugin requires other plugin to be installed") {
          openPluginsSettings()
          pluginsDialog {
            pluginsSettingsPage {
              openInstalledTab()
              waitLoaded()
              getPluginFromList(pluginToCheckName).waitFound().click()
              getPluginFromList(pluginToCheckName).shouldBe {
                errorNotice.hasSubtext("Requires") || errorNotice.hasSubtext("Not loaded")
              }
              getPluginFromList(pluginToCheckName).run {
                if (pluginThatRequiredName.isNotEmpty()) errorNotice.waitContainsText(text = pluginThatRequiredName,
                                                                                         message = "Looking for plugin name substring in error message",
                                                                                         ignoreCase = true)
              }
              okButton.click()
            }
          }
        }
      }
    }
  }

  /**
   * Postpones the IDE restart if the "Restart to apply changes in plugins?" dialog appears after applying a plugin change.
   * Toggling non-dynamic plugins requires a restart; the driver framework does not support a real restart, so we press
   * "Not Now" and continue. The dialog is flaky and may not appear, hence the [runCatching] guard.
   */
  private fun postponeRestartIfNeeded() {
    driver.run {
      welcomeScreen {
        step("Postpone IDE restart if the 'apply changes in plugins' dialog appears") {
          runCatching {
            restartToUpdateConfirmationDialogUi {
              waitFound(10.seconds)
              cancelButton.click() // "Not Now"
            }
          }
        }
      }
    }
  }

}