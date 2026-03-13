package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget

@Remote("com.jetbrains.thinclient.editors.FrontendGuestNavigationService", rdTarget = RdTarget.FRONTEND)
interface FrontendGuestNavigationService {
  fun navigateViaBackend(pathRelativeToBasePath: String, offset: Int)
}
