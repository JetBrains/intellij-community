// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils

interface IRegistryKey {
    fun withSuffix(suffix: String): IRegistryKey
    fun inChild(child: String): IRegistryKey
    operator fun div(child: String): IRegistryKey = inChild(child)
    fun getStringValue(value: String): String?
    fun getKeys(): List<String>?
    fun getValues(): Map<String, Any>?
}