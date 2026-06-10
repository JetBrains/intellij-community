// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.ui.components.Badge
import javax.swing.Icon

/**
 * Kotlin exposes this immutable badge as a [JvmField] named [Badge.new], which means that Java
 * sources can't access it, due to `new` being a keyword.
 * We need to expose separately to allow Java sources to access it.
 */
@JvmField
internal val newBadgeIcon: Icon = Badge.new