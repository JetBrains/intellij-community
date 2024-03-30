package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.model.TextDataList
import org.intellij.lang.annotations.Language

@Remote("com.jetbrains.performancePlugin.remotedriver.SearchService",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface SearchService {
  fun findAll(@Language("xpath") xpath: String): List<Component>
  fun findAll(@Language("xpath") xpath: String, component: Component): List<Component>
  fun findAllText(component: Component): TextDataList
}