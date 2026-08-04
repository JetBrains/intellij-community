// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.actionSystem

import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.PathManager
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.TestApplicationManager
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val REGENERATE_PROPERTY = "actions.golden.regenerate"

/**
 * Golden snapshot of the action-group structure assembled from XML registration:
 * the recursive [IdeActions.GROUP_MAIN_MENU] tree plus the direct-children id sequence
 * of every order-sensitive group listed in `riskyGroupIds.txt` that this run registers.
 *
 * Only observed id sequences are asserted, captured via
 * [DefaultActionGroup.getChildActionsOrStubs] in a fresh test application:
 * no action classes are loaded and nothing is assumed about when references resolve,
 * so the same snapshot must hold across registration-mechanism changes.
 *
 * Each subclass pins the golden to one product via [goldenFile] and [regenerateCommand].
 * The plugin set that assembles the tree is deliberately not asserted: what this snapshot
 * protects is the resulting structure, so a plugin that comes or goes matters here only
 * insofar as it changes an id sequence.
 *
 * If a sequence change is intended, regenerate the snapshot with
 * `-Dpass.actions.golden.regenerate=true` (full command in [regenerateCommand]).
 */
abstract class ActionGroupStructureTestCase {
  companion object {
    @JvmStatic
    @BeforeAll
    fun initializeApplication() {
      // This module doesn't depend on intellij.platform.testFramework.junit5,
      // so @TestApplication isn't available on this classpath.
      TestApplicationManager.getInstance()
    }
  }

  protected abstract val goldenFile: Path
  protected abstract val regenerateCommand: String

  /** The checkout root; in dev-mode runs [PathManager.getHomeDir] is the dev-run dist, not the checkout. */
  protected val monorepoRoot: Path
    get() {
      val devProjectRoot = if (AppMode.isRunningFromDevBuild()) AppMode.getDevIdeaProjectDir() else null
      return devProjectRoot?.let(Path::of) ?: PathManager.getHomeDir()
    }

  protected val communityRoot: Path
    get() = monorepoRoot.resolve("community").takeIf { Files.isDirectory(it.resolve(".idea")) } ?: monorepoRoot

  @Test
  fun mainMenuAndRiskyGroupSequencesMatchGoldenSnapshot() {
    val riskyGroupIds = (communityRoot / "platform" / "platform-tests" / "testData" /
                         "actionSystem" / "groupStructure" / "riskyGroupIds.txt")
      .readLines()
      .filterNot { it.isBlank() || it.startsWith("#") }
      .distinct()
      .sorted()
    val actual = renderSnapshot(ActionManager.getInstance(), riskyGroupIds)

    if (System.getProperty(REGENERATE_PROPERTY).toBoolean()) {
      goldenFile.writeText(actual)
      return
    }
    if (!Files.exists(goldenFile)) {
      fail { "Golden snapshot $goldenFile is missing; generate it with:\n$regenerateCommand" }
    }
    val expected = goldenFile.readText()
    if (expected != actual) {
      throw FileComparisonFailedError("Action-group id sequences diverged", expected, actual, goldenFile.absolutePathString())
    }
  }

  private fun renderSnapshot(actionManager: ActionManager, riskyGroupIds: List<String>): String {
    val absentIds = riskyGroupIds.filter { actionManager.getActionOrStub(it) == null }
    val result = StringBuilder()
    result.appendLine("# Golden action-group structure: child id sequences observed via getChildActionsOrStubs.")
    result.appendLine("# Regenerate: $regenerateCommand")
    val absentLine = "# Risky group ids not registered in this run (${absentIds.size} of ${riskyGroupIds.size}): " + absentIds.joinToString(" ")
    result.appendLine(absentLine.trimEnd())
    result.appendLine("[tree ${IdeActions.GROUP_MAIN_MENU}]")
    val mainMenu = actionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU)
    if (mainMenu == null) {
      result.appendLine("  <unregistered>")
    }
    else {
      val path = mutableListOf(mainMenu)
      staticChildrenOf(mainMenu).forEach { child ->
        appendTree(result, child, actionManager, indent = 1, path = path)
      }
    }
    riskyGroupIds.forEach { id ->
      val group = actionManager.getActionOrStub(id) ?: return@forEach
      result.appendLine("[group $id]")
      staticChildrenOf(group).forEach { child ->
        result.appendLine("  ${labelOf(child, actionManager)}")
      }
    }
    return result.toString()
  }

  private fun appendTree(result: StringBuilder, action: AnAction, actionManager: ActionManager, indent: Int, path: MutableList<AnAction>) {
    val padding = "  ".repeat(indent)
    val label = labelOf(action, actionManager)
    if (path.any { it === action }) {
      result.appendLine("$padding$label <cycle>")
      return
    }
    result.appendLine("$padding$label")
    path.add(action)
    staticChildrenOf(action).forEach { child ->
      appendTree(result, child, actionManager, indent + 1, path)
    }
    path.removeLast()
  }

  private fun staticChildrenOf(action: AnAction): Array<AnAction> =
    if (action is DefaultActionGroup) action.childActionsOrStubs else AnAction.EMPTY_ARRAY

  private fun labelOf(action: AnAction, actionManager: ActionManager): String = when (action) {
    is ActionStubBase -> action.id
    is Separator -> "<separator>"
    else -> actionManager.getId(action)
            ?: if (action is ActionGroup) "<anonymous-group>" else "<unregistered ${action.javaClass.name}>"
  }
}
