// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import org.jetbrains.annotations.NonNls

const val USE_K1_PLUGIN_PROPERTY_NAME: @NonNls String = "idea.kotlin.plugin.use.k1"

@Deprecated("Use USE_K1_PLUGIN_PROPERTY_NAME instead")
const val USE_K2_PLUGIN_PROPERTY_NAME: @NonNls String = "idea.kotlin.plugin.use.k2"

var useK2Plugin: Boolean?
    get() = System.getProperty(USE_K1_PLUGIN_PROPERTY_NAME)?.toBoolean()?.not()
    set(value) {
        if (value != null) {
            System.setProperty(USE_K1_PLUGIN_PROPERTY_NAME, value.not().toString())
        } else {
            System.clearProperty(USE_K1_PLUGIN_PROPERTY_NAME)
        }
    }
