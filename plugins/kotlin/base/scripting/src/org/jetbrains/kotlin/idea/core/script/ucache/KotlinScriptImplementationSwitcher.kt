// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.util.registry.Registry

val scriptsAsEntities: Boolean = Registry.`is`("kotlin.scripts.as.entities", false)