package com.intellij.driver.sdk.application

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

@Remote("com.intellij.openapi.application.ApplicationNamesInfo")
interface ApplicationNamesInfo {
  fun getInstance(): ApplicationNamesInfo
  fun getFullProductName(): String
}

val Driver.fullProductName get() = utility(ApplicationNamesInfo::class).getInstance().getFullProductName()