package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service


@Remote("com.intellij.openapi.roots.ModuleRootManager")
interface ModuleRootManager {
  fun getInstance(module: Module): ModuleRootManager
  fun getContentEntries(): Array<ContentEntry>
  fun getOrderEntries(): Array<OrderEntry>
}

@Remote("com.intellij.openapi.roots.OrderEntry")
interface OrderEntry {
  fun getPresentableName(): String
}

@Remote("com.intellij.openapi.roots.ContentEntry")
interface ContentEntry {
  fun getFile(): VirtualFile
}

fun Driver.getContentEntries(module: Module) = utility(ModuleRootManager::class).getInstance(module).getContentEntries().toList()
fun Driver.getOrderEntries(module: Module) = utility(ModuleRootManager::class).getInstance(module).getOrderEntries().toList()
