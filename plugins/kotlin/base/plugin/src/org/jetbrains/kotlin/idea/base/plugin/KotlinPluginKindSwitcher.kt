// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.diagnostic.VMOptions

object KotlinPluginKindSwitcher {
    @JvmStatic
    fun canPluginBeSwitchedByVmOptions(): Boolean {
        return VMOptions.canWriteOptions()
    }

    @JvmStatic
    fun getPluginKindByVmOptions(): KotlinPluginKind {
        val isK2Enabled = VMOptions.readOption(USE_K2_VM_OPTION_PREFIX, /*effective=*/ false).toBoolean()
        return if (isK2Enabled) KotlinPluginKind.K2 else KotlinPluginKind.K1
    }

    @JvmStatic
    fun setPluginKindByVmOptions(newPluginKind: KotlinPluginKind) {
        val isK2Enabled = newPluginKind == KotlinPluginKind.K2
        VMOptions.setOption(USE_K2_VM_OPTION_PREFIX, isK2Enabled.toString())
    }

    private const val USE_K2_VM_OPTION_NAME: String = "idea.kotlin.plugin.use.k2"

    private const val USE_K2_VM_OPTION_PREFIX: String = "-D${USE_K2_VM_OPTION_NAME}="
}