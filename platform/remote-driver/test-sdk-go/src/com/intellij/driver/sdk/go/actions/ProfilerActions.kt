package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getRunContentManager
import com.intellij.driver.sdk.go.flameGraphViewSettingsPopup
import com.intellij.driver.sdk.go.goPerformanceToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.gutter
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.wait
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The text the pprof viewer shows in place of its tabs when the captured profile has no samples. */
private const val NO_PROFILER_DATA: String = "No profiler data"

/** The start of the label text on the pprof start page above the run configuration selector. */
private const val CHOOSE_CONFIGURATION: String = "Choose configuration"

class ProfilerActions(val driver: Driver, val ui: UiRobot) {

  /** The state of a button for an error message: "absent", "disabled" or "enabled". */
  private fun UiComponent.presenceState(): String = when {
    !present() -> "absent"
    !isEnabled() -> "disabled"
    else -> "enabled"
  }

  /**
   * Waits until the pprof viewer opens inside the "Go Optimization" tool window.
   * The viewer is ready when any of its sub-tabs becomes present; we use "Flame Graph"
   * because it is the default-selected viewer tab.
   *
   * A profile without samples gets no tabs at all. The viewer shows [NO_PROFILER_DATA] instead.
   * This method stops on that state at once, because the tab never appears afterwards.
   */
  fun waitForProfilerSnapshot(timeout: Duration = 5.minutes) {
    step("Wait for profiler snapshot viewer to open") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            val flameGraphTab = viewerTab("Flame Graph")
            var viewerOpened = false
            var lastContent = ""
            should(
              message = "The snapshot viewer opens",
              timeout = timeout,
              errorMessage = {
                "The snapshot viewer did not open. The Flame Graph tab is absent after $timeout. Tool window shows: $lastContent"
              },
            ) {
              lastContent = getAllTexts().joinToString()
              viewerOpened = flameGraphTab.present()
              viewerOpened || NO_PROFILER_DATA in lastContent
            }
            if (!viewerOpened) {
              error("The captured profile has no samples: the viewer shows '$NO_PROFILER_DATA'")
            }
          }
        }
      }
    }
  }

  fun openProfilerTab(tabName: String, timeout: Duration = 30.seconds) {
    step("Open profiler tab '$tabName'") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            val tab = viewerTab(tabName)
            tab.should(
              message = "$tabName tab is not present after $timeout",
              timeout = timeout,
            ) { present() }
            tab.click()
          }
        }
      }
    }
  }

  fun checkProfilerToolWindow(texts: List<String>, timeout: Duration = 1.minutes) {
    step("Check Go Optimization tool window contains ${texts.joinToString(prefix = "[", postfix = "]")}") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            var lastMissing: List<String> = texts
            var lastContent = ""
            should(
              message = "Go Optimization tool window does not contain $lastMissing: $lastContent",
              timeout = timeout,
            ) {
              lastContent = getAllTexts().joinToString()
              lastMissing = texts.filterNot { it in lastContent }
              lastMissing.isEmpty()
            }
          }
        }
      }
    }
  }

  /**
   * Asserts that the pprof sample-type selector ("Show:" label + combobox) is rendered
   * on the active viewer tab's side toolbar.
   */
  fun checkSampleTypeSelectorPresent(timeout: Duration = 30.seconds) {
    step("Check 'Show:' sample-type selector is present") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            sampleTypeSelectorLabel.should(
              message = "'Show:' sample-type selector label is not present after $timeout",
              timeout = timeout,
            ) { present() }
          }
        }
      }
    }
  }

  /**
   * Asserts that the "Graph" tab renders a call-graph diagram: the `UmlGraphZoomableViewport`
   * component must be present in the tool window. Call after switching to the Graph tab.
   */
  fun checkGraphRendered(timeout: Duration = 1.minutes) {
    step("Check Graph tab renders a call-graph diagram") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            graphViewport.should(
              message = "Graph tab did not render a UmlGraphZoomableViewport within $timeout",
              timeout = timeout,
            ) { present() }
          }
        }
      }
    }
  }

  /**
   * Clicks the Flame Graph tab's "View Settings" gear and asserts that [expected]
   * toggle labels are present in the popup. The popup is dismissed before returning.
   * Call while the Flame Graph tab is the active viewer tab.
   */
  fun checkFlameGraphViewToggles(expected: List<String>) {
    require(expected.isNotEmpty()) { "expected toggles must not be empty" }
    step("Check Flame Graph 'View Settings' popup contains $expected") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            viewSettingsButton.shouldBe("'View Settings' button is not present on Flame Graph tab") { present() }
            viewSettingsButton.click()
          }
          val anchor = expected.first()
          val popup = flameGraphViewSettingsPopup(anchor)
          popup.should(
            message = "Flame Graph 'View Settings' popup did not open (anchored on '$anchor')",
            timeout = 30.seconds,
          ) { present() }
          var lastMissing: List<String> = expected
          var lastContent = ""
          popup.should(
            message = "Flame Graph 'View Settings' popup contains ${expected.joinToString()}",
            timeout = 30.seconds,
            errorMessage = { "Flame Graph 'View Settings' popup does not contain $lastMissing. Popup shows: $lastContent" },
          ) {
            lastContent = getAllTexts().joinToString()
            lastMissing = expected.filterNot { it in lastContent }
            lastMissing.isEmpty()
          }
          keyboard { escape() }
        }
      }
    }
  }

  /**
   * Opens the "Go Optimization" tool window. It does nothing when the tool window is already open.
   *
   * The activate action toggles the tool window: on an active tool window it hides it.
   */
  fun openGoOptimizationToolWindow() {
    step("Open the 'Go Optimization' tool window") {
      driver.withContext {
        ui.ideFrame {
          if (goPerformanceToolWindow().present()) return@ideFrame
          driver.invokeAction("ActivateGoOptimizationToolWindow", now = false)
          goPerformanceToolWindow {
            should(message = "'Go Optimization' tool window did not open", timeout = 30.seconds) { present() }
          }
        }
      }
    }
  }

  /**
   * Checks the "Run with Profiler" button and the "Choose configuration" label of the pprof start page.
   *
   * The label is read from the painted text of the tool window.
   */
  fun checkProfilerStartPage() {
    step("Check the pprof start page") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            runWithProfilerButton.should(message = "'Run with Profiler' button is not present", timeout = 30.seconds) { present() }
            var lastContent = ""
            should(
              message = "The pprof start page shows '$CHOOSE_CONFIGURATION'",
              timeout = 10.seconds,
              errorMessage = { "'$CHOOSE_CONFIGURATION' label is not present. Tool window shows: $lastContent" },
            ) {
              lastContent = getAllTexts().joinToString()
              CHOOSE_CONFIGURATION in lastContent
            }
          }
        }
      }
    }
  }

  fun launchProfilerFromToolWindow(timeout: Duration = 2.minutes) {
    step("Launch profiler via the 'Run with Profiler' button") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            runWithProfilerButton.should(
              message = "'Run with Profiler' button is enabled",
              timeout = timeout,
              errorMessage = { "'Run with Profiler' button is ${runWithProfilerButton.presenceState()} after $timeout" },
            ) { present() && isEnabled() }
            runWithProfilerButton.click()
          }
        }
      }
    }
  }

  fun launchProfilerFromGutter() {
    step("Launch profiler from the editor gutter 'Profile' action") {
      driver.withContext {
        ui.ideFrame {
          // Scroll to the top so the `func main` run gutter icon is on screen and the click lands.
          codeEditor { goToPosition(1, 1) }
          val runIcon = gutter().getGutterIcons().firstOrNull {
            runCatching { it.getIconPath() }.getOrNull()?.contains("run", ignoreCase = true) == true
          } ?: error("Run gutter icon was not found in the editor")
          runIcon.click()
          clickProfileMenuItem()
        }
      }
    }
  }

  fun launchProfilerFromMainToolbar() {
    step("Launch profiler from the main toolbar") {
      driver.withContext {
        ui.ideFrame {
          mainToolbar.moreButton.click()
          // The main-toolbar "More" popup is a list popup; pick the profile row by the profiler name.
          popup().waitOneContainsText("GoLand Profiler").click()
        }
      }
    }
  }

  private fun IdeaFrameUI.clickProfileMenuItem() {
    val profileItem = x { and(byClass("ActionMenuItem"), contains(byVisibleText("Profile"))) }
    profileItem.should(message = "'Profile' run action is not present in the gutter popup", timeout = 30.seconds) { present() }
    profileItem.click()
  }

  fun waitForProfilerProcess(timeout: Duration = 3.minutes) {
    step("Wait for the profiler to attach to the running process") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            startCpuRecordingButton.should(
              message = "The profiler is attached: 'Start CPU Recording' is enabled",
              timeout = timeout,
              errorMessage = { "'Start CPU Recording' is ${startCpuRecordingButton.presenceState()} after $timeout" },
            ) { present() && isEnabled() }
          }
        }
      }
    }
  }

  fun recordCpuSnapshot(recordingWindow: Duration = 5.seconds) {
    step("Record a CPU snapshot") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            startCpuRecordingButton.should(
              message = "'Start CPU Recording' button is enabled",
              timeout = 1.minutes,
              errorMessage = { "'Start CPU Recording' button is ${startCpuRecordingButton.presenceState()}" },
            ) { present() && isEnabled() }
            startCpuRecordingButton.click()
            // Recording window: the Go CPU sampler needs wall-clock time to collect samples.
            stopCpuRecordingButton
              .should(message = "CPU recording did not start ('Stop CPU Recording' not shown)", timeout = 30.seconds) { present() }
              .wait(recordingWindow)
            stopCpuRecordingButton.click()
          }
        }
      }
    }
    waitForProfilerSnapshot()
  }

  fun takeSnapshot(buttonText: String) {
    step("Take '$buttonText' snapshot") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            profilerToolbarButton(buttonText).should(
              message = "'$buttonText' snapshot button is enabled",
              timeout = 1.minutes,
              errorMessage = { "'$buttonText' snapshot button is ${profilerToolbarButton(buttonText).presenceState()}" },
            ) { present() && isEnabled() }
            profilerToolbarButton(buttonText).click()
          }
        }
      }
    }
    waitForProfilerSnapshot()
  }

  fun takeMoreProfile(profileName: String) {
    step("Take '$profileName' snapshot from the 'More Profiles' menu") {
      driver.withContext {
        ui.ideFrame {
          goPerformanceToolWindow {
            moreProfilesButton.should(
              message = "'More Profiles' button is enabled",
              timeout = 1.minutes,
              errorMessage = { "'More Profiles' button is ${moreProfilesButton.presenceState()}" },
            ) { present() && isEnabled() }
            moreProfilesButton.click()
          }
          popup().waitOneContainsText(profileName).click()
        }
      }
    }
    waitForProfilerSnapshot()
  }

  fun checkLineHintsOutdatedBannerAfterEdit(fileName: String) {
    step("Edit '$fileName' and check the 'profiling results outdated' banner appears") {
      driver.openFile(fileName)
      driver.withContext {
        ui.ideFrame {
          codeEditor {
            goToPosition(1, 1)
            keyboard { enter() }
          }
          x { contains(byVisibleText("Profiling results might be outdated")) }.should(
            message = "'profiling results might be outdated' banner did not appear after editing $fileName",
            timeout = 1.minutes,
          ) { present() }
        }
      }
    }
  }

  fun stopProfilerProcess() {
    step("Stop the profiled program") {
      // The profiled program runs in the "Go Optimization" tool window, where invokeAction("Stop") does
      // not reliably target it and the terminate confirmation dialog only appears on IDE close (too late
      // for a headless run). Stop the process directly via its run content descriptor, otherwise it
      // survives IDE shutdown and is reported as a leftover process.
      val runContentManager = driver.getRunContentManager(driver.singleProject())
      runContentManager.getAllDescriptors()
        .mapNotNull { descriptor -> descriptor.getProcessHandler()?.takeIf { !it.isProcessTerminated() } }
        .forEach { process ->
          process.destroyProcess()
          process.waitFor(15.seconds.inWholeMilliseconds)
        }
    }
  }
}
