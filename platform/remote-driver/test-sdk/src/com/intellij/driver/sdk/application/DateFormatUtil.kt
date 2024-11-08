package com.intellij.driver.sdk.application

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Remote("com.intellij.util.text.DateFormatUtil")
interface DateFormatUtil {
  fun formatDate(date: Date): String
}

fun Driver.formatLicenseDate(date: LocalDate) = utility(DateFormatUtil::class)
  .formatDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))