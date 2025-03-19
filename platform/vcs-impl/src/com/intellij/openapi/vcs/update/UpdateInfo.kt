// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.util.Clock
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.xmlb.jdomToJson
import com.intellij.util.xmlb.jsonDomToXml
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@Serializable
data class UpdateInfoState(
  @NonNls val date: String? = null,
  @NonNls val actionInfo: String? = null,
  @NonNls val fileInfo: JsonObject? = null,
)

internal class UpdateInfo(
  internal val fileInformation: UpdatedFiles?,
  internal val date: String?,
  internal val actionInfo: ActionInfo?,
) {
  constructor(updatedFiles: UpdatedFiles?, actionInfo: ActionInfo?) : this(
    actionInfo = actionInfo,
    fileInformation = updatedFiles,
    date = DateFormatUtil.formatPrettyDateTime(Clock.getTime()),
  )

  fun writeExternal(): UpdateInfoState? {
    val fileInformation = fileInformation?.takeIf { !it.isEmpty } ?: return null

    val fileElement = Element("e")
    FileGroup.writeGroupsToElement(fileInformation.topLevelGroups, fileElement)

    return UpdateInfoState(
      date = date,
      actionInfo = actionInfo?.actionName,
      fileInfo = jdomToJson(fileElement),
    )
  }

  val caption: String
    get() = VcsBundle.message("toolwindow.title.update.project", date)

  val isEmpty: Boolean
    get() = fileInformation == null || fileInformation.isEmpty
}

internal fun readUpdateInfoState(state: UpdateInfoState): UpdateInfo {
  return UpdateInfo(
    date = state.date,
    actionInfo = getActionInfoByName(state.actionInfo),
    fileInformation = state.fileInfo?.let { jsonDomToXml(it) }?.let { UpdatedFiles.readExternal(it) }
  )
}

private fun getActionInfoByName(actionInfoName: String?): ActionInfo? {
  return when {
    ActionInfo.UPDATE.actionName == actionInfoName -> ActionInfo.UPDATE
    ActionInfo.STATUS.actionName == actionInfoName -> ActionInfo.STATUS
    else -> null
  }
}