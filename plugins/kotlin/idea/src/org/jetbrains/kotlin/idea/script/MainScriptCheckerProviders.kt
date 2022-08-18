// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.script.configuration.SupportedScriptScriptingSupportCheckerProvider

class MainKtsScriptCheckerProvider: SupportedScriptScriptingSupportCheckerProvider(".main.kts")

class SpaceKtsScriptCheckerProvider: SupportedScriptScriptingSupportCheckerProvider(".space.kts")

class TeamCityKtsScriptCheckerProvider: SupportedScriptScriptingSupportCheckerProvider(".teamcity.kts", true) {
    override fun isSupportedScriptExtension(virtualFile: VirtualFile): Boolean {
        if (!virtualFile.name.endsWith(".kts")) return false

        var parent = virtualFile.parent
        while (parent != null) {
            if (parent.isDirectory && parent.name == ".teamcity") {
                return true
            }
            parent = parent.parent
        }

        return false
    }
}
