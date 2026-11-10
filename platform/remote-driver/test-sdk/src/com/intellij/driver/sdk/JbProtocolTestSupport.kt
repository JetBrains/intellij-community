package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import org.jetbrains.annotations.ApiStatus

@Remote("org.jetbrains.ide.JbProtocolTestSupport", plugin = "com.intellij/intellij.platform.builtInServer.impl")
@ApiStatus.Internal
interface JbProtocolTestSupportRef {
  fun fire(url: String)
}

fun Driver.fireJbProtocolUrl(url: String) {
  utility(JbProtocolTestSupportRef::class).fire(url)
}
