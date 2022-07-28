// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.Messages
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePluginVersion

object KotlinPluginCompatibilityVerifier {
    @JvmStatic
    fun checkCompatibility() {
        val platformVersion = ApplicationInfo.getInstance().shortVersion ?: return
        val isAndroidStudio = KotlinPlatformUtils.isAndroidStudio

        val rawVersion = KotlinIdePlugin.version
        val kotlinPluginVersion = KotlinIdePluginVersion.parse(rawVersion).getOrNull() ?: return

        if (kotlinPluginVersion.platformVersion != platformVersion || kotlinPluginVersion.isAndroidStudio != isAndroidStudio) {
            val ideName = ApplicationInfo.getInstance().versionName

            runInEdt {
                Messages.showWarningDialog(
                    KotlinBundle.message("plugin.verifier.compatibility.issue.message", rawVersion, ideName, platformVersion),
                    KotlinBundle.message("plugin.verifier.compatibility.issue.title")
                )
            }
        }
    }
}
