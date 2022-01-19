// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import kotlin.system.exitProcess

internal class DumpActionsAppStarter : ApplicationStarter {
  override fun getCommandName(): String {
    return "dumpActions"
  }

  override fun main(args: List<String>) {
    val outputFile = args.getOrNull(1)!!
    dumpActionsNames(outputFile)
    exitProcess(0)
  }

  private fun dumpActionsNames(outputFile: String) {
    val actionManager = ActionManager.getInstance()
    val actionIdList = actionManager.getActionIdList("")
    val visitedActions = HashSet<String>()
    val actionsDescriptions = HashSet<ActionDescription>()
    for (actionId in actionIdList) {
      val action = actionManager.getAction(actionId)
      processAction(action, actionId, visitedActions, actionsDescriptions)
    }
    val mainMenuAction = HashMap<String, String>()
    collectMainMenuActions(mainMenuAction, actionManager.getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup, null)
    val builder = StringBuilder()
    builder.appendLine("Id;Name;Description;Path")
    for (description in actionsDescriptions) {
      val path = mainMenuAction[description.id] ?: ""
      builder.appendLine("${description.id};${description.name};${description.description};$path")
    }

    FileUtil.writeToFile(File(outputFile), builder.toString())
    File(outputFile).writeText(builder.toString())
  }

  private fun collectMainMenuActions(groups: HashMap<String, String>, action: AnAction, parentName: String?) {
    if (action is SeparatorAction) return
    try {
      val path = appendName(action, parentName)
      if (action is ActionGroup) {
        for (child in action.getChildren(null)) {
          collectMainMenuActions(groups, child, path)
        }
      }
      else {
        val id = ActionManager.getInstance().getId(action) ?: action.javaClass.name
        groups[id] = path
      }
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun processAction(action: AnAction?,
                            actionId: String?,
                            visitedActions: HashSet<String>,
                            actionsDescriptions: HashSet<ActionDescription>) {
    if (action == null) return
    if (action is SeparatorAction) return
    try {
      val id = actionId ?: ActionManager.getInstance().getId(action) ?: action.javaClass.name
      if (visitedActions.contains(id)) {
        return
      }
      visitedActions.add(id)
      val templatePresentation = action.templatePresentation
      val name = templatePresentation.text ?: ""

      if (action is ActionGroup) {
        val children = action.getChildren(null)
        for (child in children) {
          processAction(child, null, visitedActions, actionsDescriptions)
        }
      }
      else {
        if (!id.startsWith("<anonymous-group")) {
          val description = templatePresentation.description ?: ""

          actionsDescriptions.add(ActionDescription(id, name, description))
        }
      }
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }

  data class ActionDescription(val id: String, val name: String, val description: String)

  private fun appendName(action: AnAction,
                         parentMenuPath: String?): String {
    val templatePresentation = action.templatePresentation
    if (action is ActionGroup && !action.isPopup) return parentMenuPath ?: ""
    val text = if (templatePresentation.text.isNullOrBlank()) "?" else templatePresentation.text
    return if (parentMenuPath.isNullOrEmpty()) text else "$parentMenuPath | $text"
  }

}
