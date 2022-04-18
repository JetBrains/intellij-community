// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.domain

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderEntry
import org.jetbrains.kotlin.idea.project.isHMPPEnabled
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.platform.SimplePlatform
import kotlin.properties.Delegates

class ModuleEntity(val name: String) {

    lateinit var actualVersion: String
    lateinit var orderEntries: Array<OrderEntry>
    var componentPlatforms: Set<SimplePlatform>? = null
    var hMPPEnabled by Delegates.notNull<Boolean>()

    // TODO: add remaining entity properties


    companion object {
        // Adapter from Openapi
        fun fromOpenapiModule(module: Module): ModuleEntity {
            return ModuleEntity(module.name).apply {
                val rootModel = module.rootManager
                actualVersion = module.languageVersionSettings.languageVersion.versionString
                orderEntries = rootModel.orderEntries
                componentPlatforms = module.platform?.componentPlatforms
                hMPPEnabled = module.isHMPPEnabled

                // TODO: add remaining entity properties
            }
        }
    }
}


//fun module(name: String, isOptional: Boolean = false, assertionFunc: ModuleEntity.() -> Unit): ModuleEntity {
//    return ModuleEntity(name, isOptional).apply { this.assertionFunc() }
//}



