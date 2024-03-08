// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.util.Clock
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.xmlb.jdomToJson
import com.intellij.util.xmlb.jsonDomToXml
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@Serializable
data class UpdateInfoState(
  @NonNls val date: String? = null,
  @NonNls val actionInfo: String? = null,
  @NonNls val fileInfo: String? = null,
)

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

  fun writeExternal(): UpdateInfoState? {
    val fileInformation = fileInformation?.takeIf { !it.isEmpty } ?: return null

    val fileElement = Element(FILE_INFO_ELEMENTS)
    fileInformation.writeExternal(fileElement)

    return UpdateInfoState(
      date = date,
      actionInfo = actionInfo?.actionName,
      fileInfo = Json.encodeToString(jdomToJson(fileElement)),
    )
  }

  fun readExternal(state: UpdateInfoState) {
    date = state.date
    val fileInfoElement = state.fileInfo?.let { s -> (Json.parseToJsonElement(s) as? JsonObject)?.let { jsonDomToXml(it) } } ?: return
    actionInfo = getActionInfoByName(state.actionInfo) ?: return

    val updatedFiles = UpdatedFiles.create()
    updatedFiles.readExternal(fileInfoElement)
    fileInformation = updatedFiles
  }

  val caption: String
    get() = VcsBundle.message("toolwindow.title.update.project", date)

  val isEmpty: Boolean
    get() = fileInformation == null || fileInformation!!.isEmpty
}

private const val FILE_INFO_ELEMENTS: @NonNls String = "UpdatedFiles"

private fun getActionInfoByName(actionInfoName: String?): ActionInfo? {
  return when {
    ActionInfo.UPDATE.actionName == actionInfoName -> ActionInfo.UPDATE
    ActionInfo.STATUS.actionName == actionInfoName -> ActionInfo.STATUS
    else -> null
  }
}