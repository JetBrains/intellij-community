package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.go.ui.navigationGutterIcon
import com.intellij.driver.sdk.go.ui.navigationTargetsPopup
import com.intellij.driver.sdk.invokeActionByShortcut
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.shouldHaveFocus
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

/**
 * The navigation actions of the Go plugin: `Go to Implementations` and `Go to Interfaces`.
 *
 * An action collects its targets in the background, so the pop-up of the targets grows while it is shown.
 */
class NavigationActions(val driver: Driver, val ui: UiRobot) {

  /** Clicks the `Go to Implementations` gutter icon of the line [declaration] starts on. */
  fun goToImplementationsFromGutter(declaration: String) {
    clickNavigationGutterIcon(GO_TO_IMPLEMENTATIONS, declaration)
  }

  /** Clicks the `Go to Interfaces` gutter icon of the line [declaration] starts on. */
  fun goToInterfacesFromGutter(declaration: String) {
    clickNavigationGutterIcon(GO_TO_INTERFACES, declaration)
  }

  /** Puts the caret on [target] of [declaration], then invokes [actionId] by its shortcut. */
  fun invokeOnDeclaration(actionId: String, declaration: String, target: String) {
    step("Invoke '$actionId' by its shortcut on '$target' of '$declaration'") {
      driver.withContext {
        ui.ideFrame {
          codeEditor {
            expandAllFoldings()
            moveCaretToOffset(declarationOffset(text, declaration, target))
            // A click at the caret can hit the intention bulb in the indent and open the intention pop-up instead.
            scrollToCaret()
            // On Windows, the robot keys reach the IDE only after a mouse click activates its window.
            // The tab of the file is a spot that cannot open a pop-up.
            this@ideFrame.editorTabs().clickTab(editor.getVirtualFile().getName())
            setFocus()
            shouldHaveFocus()
            invokeActionByShortcut(actionId)
          }
        }
      }
    }
  }

  /**
   * Selects the first target of the pop-up, which must be [name] out of [expectedTargets] targets.
   *
   * The pop-up sorts a target of the project before a target of the module cache, so the first target is the one of
   * the project.
   */
  fun selectFirstTarget(name: String, expectedTargets: Int) {
    step("Select '$name', the first of the $expectedTargets targets of the pop-up") {
      driver.withContext {
        ui.ideFrame {
          navigationTargetsPopup().run {
            waitFound(POPUP_TIMEOUT)
            var found = emptyList<String>()
            waitFor(
              message = "The pop-up shows $expectedTargets targets",
              timeout = POPUP_TIMEOUT,
              errorMessage = { "The pop-up shows ${found.size} targets: $found" },
            ) {
              found = targets()
              found.size == expectedTargets
            }
            check(name in found.first()) { "The first target of the pop-up is '${found.first()}', and not '$name'" }
            clickTarget(0)
          }
        }
      }
    }
  }

  /**
   * Waits until the editor shows [target] of [declaration] in [file] of the project, with the caret on it.
   *
   * The module cache holds a released copy of the project, so the path of the opened file tells the two copies apart.
   */
  fun assertNavigatedTo(file: String, declaration: String, target: String) {
    step("Verify the editor shows '$target' of '$file'") {
      driver.withContext {
        ui.ideFrame {
          var path = ""
          var caret = -1
          var expected = -1
          waitFor(
            message = "The caret is on '$target' of '$file'",
            timeout = NAVIGATION_TIMEOUT,
            errorMessage = { "The editor shows '$path' with the caret at offset $caret, and the target is at $expected" },
          ) {
            codeEditor {
              path = editor.getVirtualFile().getPath().replace('\\', '/')
              expected = if (declaration in text) declarationOffset(text, declaration, target) else -1
              caret = interact { getCaretModel().getOffset() }
            }
            expected >= 0 && caret == expected && path.endsWith(file) && MODULE_CACHE_DIRECTORY !in path
          }
        }
      }
    }
  }

  private fun clickNavigationGutterIcon(title: String, declaration: String) {
    step("Click the '$title' gutter icon of '$declaration'") {
      driver.withContext {
        ui.ideFrame {
          val editor = codeEditor()
          editor.expandAllFoldings()
          require(declaration in editor.text) { "'$declaration' is not found in the file the editor shows" }
          navigationGutterIcon(editor.getLineNumber(declaration), title).click()
        }
      }
    }
  }

  /** The offset of [target] in the [declaration] the [text] of the editor holds. */
  private fun declarationOffset(text: String, declaration: String, target: String): Int {
    val offset = text.indexOf(declaration)
    require(offset >= 0) { "'$declaration' is not found in the file the editor shows" }
    val targetOffset = declaration.indexOf(target)
    require(targetOffset >= 0) { "'$target' is not a part of '$declaration'" }
    require(targetOffset == declaration.lastIndexOf(target)) { "'$target' is not unique in '$declaration'" }
    return offset + targetOffset
  }

  private companion object {
    const val GO_TO_IMPLEMENTATIONS = "Go to Implementations"
    const val GO_TO_INTERFACES = "Go to Interfaces"

    /** The GOPATH of the IDE under test, where the dependencies of the project are downloaded to. */
    const val MODULE_CACHE_DIRECTORY = "go-modules-cache"

    val POPUP_TIMEOUT = 30.seconds
    val NAVIGATION_TIMEOUT = 30.seconds
  }
}
