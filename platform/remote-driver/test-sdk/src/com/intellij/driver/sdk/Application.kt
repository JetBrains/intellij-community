package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.openapi.components.ComponentManager")
interface ComponentManager

@Remote("com.intellij.openapi.application.Application")
interface Application : ComponentManager

@Remote("com.intellij.openapi.application.ApplicationManager")
interface ApplicationManager {
  fun getApplication(): Application
}

fun Driver.application(): Application {
  return application(RdTarget.DEFAULT)
}

fun Driver.application(rdTarget: RdTarget): Application {
  return utility<ApplicationManager>(rdTarget).getApplication()
}
