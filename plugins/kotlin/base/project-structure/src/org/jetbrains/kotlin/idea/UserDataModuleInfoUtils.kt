// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UserDataModuleInfoKt")
package org.jetbrains.kotlin.idea

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Key
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

// WARNING, this API is used by AS3.3+

@JvmField
@Deprecated("Use 'customSourceRootType' instead.")
val MODULE_ROOT_TYPE_KEY = getOrCreateKey<JpsModuleSourceRootType<*>>("Kt_SourceRootType")

@JvmField
@Deprecated("Use 'customSdk' instead.")
val SDK_KEY = getOrCreateKey<Sdk>("Kt_Sdk")

@JvmField
@Deprecated("Use 'customLibrary' instead.")
val LIBRARY_KEY = getOrCreateKey<Library>("Kt_Library")

private inline fun <reified T> getOrCreateKey(name: String): Key<T> {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    val existingKey = Key.findKeyByName(name) as Key<T>?
    return existingKey ?: Key.create(name)
}
