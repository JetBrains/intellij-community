package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service

@Remote("com.intellij.openapi.module.ModuleManager")
interface ModuleManager {
  fun getModules(): Array<Module>
}

@Remote("com.intellij.openapi.module.Module")
interface Module {
  fun getName(): String
}

fun Driver.getModules(project: Project? = null): List<Module> {
  return service<ModuleManager>(project ?: singleProject()).getModules().toList()
}
