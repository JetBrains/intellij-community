/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("StudioUpdateAnalyticsUtil")

package com.intellij.util.analytics

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioUpdateFlowEvent

fun logClickUpdate(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder().setEventKind(StudioUpdateFlowEvent.Kind.DIALOG_CLICK_UPDATE), newBuild)
}

fun logClickIgnore(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder().setEventKind(StudioUpdateFlowEvent.Kind.DIALOG_CLICK_IGNORE), newBuild)
}

fun logClickLater(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder().setEventKind(StudioUpdateFlowEvent.Kind.DIALOG_CLICK_LATER), newBuild)
}

fun logClickAction(actionName: String, newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder()
        .setEventKind(StudioUpdateFlowEvent.Kind.DIALOG_CLICK_ACTION)
        .setActionName(actionName), newBuild)
}

fun logDownloadSuccess(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder().setEventKind(StudioUpdateFlowEvent.Kind.PATCH_DOWNLOAD_SUCCESS), newBuild)
}

fun logDownloadFailure(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder().setEventKind(StudioUpdateFlowEvent.Kind.PATCH_DOWNLOAD_FAILURE), newBuild)
}

fun logUpdateDialogOpenManually(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder()
        .setEventKind(StudioUpdateFlowEvent.Kind.DIALOG_OPEN)
        .setDialogTrigger(StudioUpdateFlowEvent.DialogTrigger.MANUAL), newBuild)
}

fun logUpdateDialogOpenFromNotification(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder()
        .setEventKind(StudioUpdateFlowEvent.Kind.DIALOG_OPEN)
        .setDialogTrigger(StudioUpdateFlowEvent.DialogTrigger.NOTIFICATION), newBuild)
}

fun logClickNotification(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder().setEventKind(StudioUpdateFlowEvent.Kind.NOTIFICATION_UPDATE_LINK_CLICKED), newBuild)

}

fun logNotificationShown(newBuild: String) {
  log(StudioUpdateFlowEvent.newBuilder().setEventKind(StudioUpdateFlowEvent.Kind.NOTIFICATION_SHOWN), newBuild)
}

fun log(event: StudioUpdateFlowEvent.Builder, newBuild: String) {
  event.setStudioNewVersion(newBuild)
  UsageTracker.log(AndroidStudioEvent.newBuilder()
                     .setKind(AndroidStudioEvent.EventKind.STUDIO_UPDATE_FLOW)
                     .setStudioUpdateFlowEvent(event.build())
  )
}
