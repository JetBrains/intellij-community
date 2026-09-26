package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.client.RequiresLockSemantics
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.TextDataList

@Remote("com.jetbrains.performancePlugin.remotedriver.SwingHierarchyService",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface SwingHierarchyService {
  @RequiresLockSemantics(LockSemantics.NO_LOCK)
  fun getSwingHierarchyAsDOM(component: Component?, onlyFrontend: Boolean): String
  fun findAllText(component: Component): TextDataList
}