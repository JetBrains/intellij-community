package com.intellij.remoteDev.downloader

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal object RemoteDevStatisticsCollector : CounterUsagesCollector() {
  private const val GROUP_ID = "cwm.gateway"
  private val EVENT_GROUP = EventLogGroup(GROUP_ID, 2)

  // Fields
  private val IS_SUCCEEDED_FIELD = EventFields.Boolean("isSucceeded")

  // Events
  private val GUEST_DOWNLOAD_EVENT = EVENT_GROUP.registerIdeActivity(
    "guestDownload",
    finishEventAdditionalFields = arrayOf(IS_SUCCEEDED_FIELD)
  )

  fun onGuestDownloadStarted(): StructuredIdeActivity = GUEST_DOWNLOAD_EVENT.started(null)

  fun onGuestDownloadFinished(activity: StructuredIdeActivity?, isSucceeded: Boolean) {
    activity?.finished { listOf(EventPair(IS_SUCCEEDED_FIELD, isSucceeded)) }
  }

  override fun getGroup(): EventLogGroup = EVENT_GROUP
}
