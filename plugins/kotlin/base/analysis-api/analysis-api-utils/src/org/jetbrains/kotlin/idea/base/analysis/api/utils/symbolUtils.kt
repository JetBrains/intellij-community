// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin

fun KaSymbolOrigin.isJavaSourceOrLibrary(): Boolean = this == KaSymbolOrigin.JAVA_SOURCE || this == KaSymbolOrigin.JAVA_LIBRARY
