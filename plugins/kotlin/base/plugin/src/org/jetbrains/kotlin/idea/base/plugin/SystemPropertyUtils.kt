// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SystemPropertyUtils")

package org.jetbrains.kotlin.idea.base.plugin

import org.jetbrains.annotations.NonNls

const val USE_K2_PLUGIN_PROPERTY_NAME: @NonNls String = "idea.kotlin.plugin.use.k2"

var useK2Plugin: Boolean?
    get() = System.getProperty(USE_K2_PLUGIN_PROPERTY_NAME)?.toBoolean()
    set(value) {
        if (value != null) {
            System.setProperty(USE_K2_PLUGIN_PROPERTY_NAME, value.toString())
        } else {
            System.clearProperty(USE_K2_PLUGIN_PROPERTY_NAME)
        }
    }
