// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import org.jetbrains.plugins.github.util.GHEnterpriseServerMetadataLoader


private const val REQUIRED_VERSION_MAJOR = 2
private const val REQUIRED_VERSION_MINOR = 21

@Service
class GHEServerVersionChecker {

  fun checkVersionSupported(endpointUrl: String) {
    val majorVersion: Int
    val minorVersion: Int
    try {
      val metadataRequest = service<GHEnterpriseServerMetadataLoader>().findRequestByEndpointUrl(endpointUrl) ?: return
      val serverMeta = ProgressIndicatorUtils.awaitWithCheckCanceled(metadataRequest) ?: return
      val versionSplit = serverMeta.installedVersion.split('.')
      majorVersion = versionSplit[0].toInt()
      minorVersion = versionSplit[1].toInt()
    }
    catch (e: Throwable) {
      throw IllegalStateException("Could not determine GitHub Enterprise server version", e)
    }

    when {
      majorVersion > REQUIRED_VERSION_MAJOR ->
        return

      majorVersion < REQUIRED_VERSION_MAJOR ->
        throwUnsupportedVersion(majorVersion, minorVersion)

      majorVersion == REQUIRED_VERSION_MAJOR ->
        if (minorVersion < REQUIRED_VERSION_MINOR) throwUnsupportedVersion(majorVersion, minorVersion)
    }
  }

  private fun throwUnsupportedVersion(currentMajor: Int, currentMinor: Int) {
    error(
      "Unsupported GitHub Enterprise server version $currentMajor.$currentMinor. Earliest supported version is $REQUIRED_VERSION_MAJOR.$REQUIRED_VERSION_MINOR"
    )
  }
}