// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.psi.UserDataProperty

internal var LookupElement.shortenCommand: ShortenCommand? by UserDataProperty(Key.create("SHORTEN_COMMAND"))

