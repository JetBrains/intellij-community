package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.rd.platform.codeWithMe.editors.GuestNavigationService")
interface GuestNavigationService {
  fun navigateViaBackend(pathRelativeToBasePath: String, offset: Int)
}
