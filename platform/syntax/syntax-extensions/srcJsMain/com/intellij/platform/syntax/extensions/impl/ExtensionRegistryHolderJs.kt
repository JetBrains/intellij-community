// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import com.intellij.platform.syntax.extensions.ExtensionSupport
import fleet.util.multiplatform.Actual

@Actual("instantiateExtensionRegistry")
internal fun instantiateExtensionRegistryJs(): ExtensionSupport = ExtensionRegistryImpl()
