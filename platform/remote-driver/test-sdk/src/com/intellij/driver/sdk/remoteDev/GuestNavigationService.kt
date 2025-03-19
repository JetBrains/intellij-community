package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget

@Remote("com.jetbrains.rd.platform.codeWithMe.editors.GuestNavigationService", rdTarget = RdTarget.FRONTEND)
interface GuestNavigationService {
  fun navigateViaBackend(pathRelativeToBasePath: String, offset: Int)
}
