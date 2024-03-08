// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.util.Clock
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.text.DateFormatUtil
import org.jdom.Element
import org.jetbrains.annotations.NonNls

internal class UpdateInfo {
  var fileInformation: UpdatedFiles? = null
    private set

  private var date: String? = null

  var actionInfo: ActionInfo? = null
    private set

  constructor(updatedFiles: UpdatedFiles?, actionInfo: ActionInfo?) {
    this.actionInfo = actionInfo
    fileInformation = updatedFiles
    date = DateFormatUtil.formatPrettyDateTime(Clock.getTime())
  }

  constructor()

  fun writeExternal(element: Element) {
    if (fileInformation == null) {
      return
    }

    element.setAttribute(DATE_ATTR, date)
    element.setAttribute(ACTION_INFO_ATTRIBUTE_NAME, actionInfo!!.actionName)
    val filesElement = Element(FILE_INFO_ELEMENTS)
    fileInformation!!.writeExternal(filesElement)
    element.addContent(filesElement)
  }

  fun readExternal(element: Element) {
    date = element.getAttributeValue(DATE_ATTR)
    val fileInfoElement = element.getChild(FILE_INFO_ELEMENTS) ?: return

    val actionInfoName = element.getAttributeValue(ACTION_INFO_ATTRIBUTE_NAME)

    actionInfo = getActionInfoByName(actionInfoName) ?: return

    val updatedFiles = UpdatedFiles.create()
    updatedFiles.readExternal(fileInfoElement)
    fileInformation = updatedFiles
  }

  val caption: String
    get() = VcsBundle.message("toolwindow.title.update.project", date)

  val isEmpty: Boolean
    get() = fileInformation == null || fileInformation!!.isEmpty
}

private const val DATE_ATTR: @NonNls String = "date"
private const val FILE_INFO_ELEMENTS: @NonNls String = "UpdatedFiles"
private const val ACTION_INFO_ATTRIBUTE_NAME: @NonNls String = "ActionInfo"

private fun getActionInfoByName(actionInfoName: String): ActionInfo? {
  return when {
    ActionInfo.UPDATE.actionName == actionInfoName -> ActionInfo.UPDATE
    ActionInfo.STATUS.actionName == actionInfoName -> ActionInfo.STATUS
    else -> null
  }
}