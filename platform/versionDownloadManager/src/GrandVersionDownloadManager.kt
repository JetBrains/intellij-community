// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.versionDownloadManager

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class GrandVersionDownloadManager {
  val managers: List<VersionDownloadManager>
    get() = EP_NAME.extensionsIfPointIsRegistered

  fun findById(id: String): VersionDownloadManager? = managers.firstOrNull { it.id == id }

  companion object {
    val EP_NAME: ExtensionPointName<VersionDownloadManager> =
      ExtensionPointName.create("com.intellij.versionDownloadManager")

    @JvmStatic
    fun getInstance(): GrandVersionDownloadManager = service()

    inline fun <reified T : VersionDownloadManager> get(): T =
      requireNotNull(getOrNull<T>()) { "No ${T::class.java.name} is registered in '${EP_NAME.name}'" }

    inline fun <reified T : VersionDownloadManager> getOrNull(): T? =
      EP_NAME.extensionsIfPointIsRegistered.filterIsInstance<T>().firstOrNull()
  }
}
