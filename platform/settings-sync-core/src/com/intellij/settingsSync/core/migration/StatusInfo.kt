package com.intellij.settingsSync.core.migration

/**
 * @author Alexander Lobas
 */
enum class StatusInfo {
  OFFLINE, JBA_NOT_FOUND, DISABLED, JBA_IO_ERROR, JBA_NOT_CONNECTED, JBA_THIS_NOT_CONNECTED, JBA_CONNECTED, JBA_IO_OPERATION, JBA_NOT_ACCEPTED;

  fun notDisabled(): Boolean {
    return ordinal > ordinal
  }

  fun `in`(begin: StatusInfo, end: StatusInfo): Boolean {
    val value = ordinal
    return begin.ordinal <= value && value <= end.ordinal
  }
}