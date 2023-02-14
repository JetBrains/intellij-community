// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinProjectStructureCustomizationUtils")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.LIBRARY_KEY
import org.jetbrains.kotlin.idea.MODULE_ROOT_TYPE_KEY
import org.jetbrains.kotlin.idea.SDK_KEY
import org.jetbrains.kotlin.psi.UserDataProperty

@Suppress("DEPRECATION")
var UserDataHolder.customSourceRootType: JpsModuleSourceRootType<*>? by UserDataProperty(MODULE_ROOT_TYPE_KEY)

@Suppress("DEPRECATION")
var UserDataHolder.customSdk: Sdk? by UserDataProperty(SDK_KEY)

@Suppress("DEPRECATION")
var UserDataHolder.customLibrary: Library? by UserDataProperty(LIBRARY_KEY)