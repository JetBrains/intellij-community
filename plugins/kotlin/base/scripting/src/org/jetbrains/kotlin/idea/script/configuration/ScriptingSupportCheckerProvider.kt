// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

abstract class ScriptingSupportCheckerProvider {
    @get:Nls
    abstract val title: String

    open fun isSupportedScriptExtension(virtualFile: VirtualFile): Boolean = false

    abstract fun isSupportedUnderSourceRoot(virtualFile: VirtualFile): Boolean

    companion object {
        @JvmField
        val CHECKER_PROVIDERS: ExtensionPointName<ScriptingSupportCheckerProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.scripting.support.checker.provider")
    }
}

abstract class SupportedScriptScriptingSupportCheckerProvider(private val suffix: String, private val supportedUnderSourceRoot: Boolean = false) : ScriptingSupportCheckerProvider() {
    override val title: String = "$suffix script"

    override fun isSupportedScriptExtension(virtualFile: VirtualFile): Boolean =
        virtualFile.name.endsWith(suffix)

    override fun isSupportedUnderSourceRoot(virtualFile: VirtualFile): Boolean =
        if (supportedUnderSourceRoot) isSupportedScriptExtension(virtualFile) else false
}