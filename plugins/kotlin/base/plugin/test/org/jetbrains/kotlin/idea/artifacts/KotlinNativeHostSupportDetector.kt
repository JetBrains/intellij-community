// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.artifacts

import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

object KotlinNativeHostSupportDetector {
    /**
     * @return `true` if the current host can support K/N, i.e., the K/N prebuilt libraries exist for this platform and architecture
     */
    fun isNativeHostSupported(): Boolean {
        val currentHost = HostManager.host
        if (currentHost !in supportedNativeHosts) return false
        // Because of the workaround in HostManager,
        // returned host architecture can be incorrect and should be checked separately.
        // E.g., on Linux ARM64 hosts HostManager.host currently falls back to Linux X64 because of some KGP issues.
        if (currentHost.architecture != expectedArchitectureByHostArchString[HostManager.hostArchOrNull()]) return false
        return true
    }

    // Set of K/N targets for which K/N prebuilt can be downloaded
    private val supportedNativeHosts: Set<KonanTarget> = setOf(
        KonanTarget.MACOS_X64,
        KonanTarget.MACOS_ARM64,
        KonanTarget.LINUX_X64,
        KonanTarget.MINGW_X64,
    )

    private val expectedArchitectureByHostArchString: Map<String, Architecture> = mapOf(
        "x86_64" to Architecture.X64,
        "aarch64" to Architecture.ARM64,
    )
}
