// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.psi.UserDataProperty

var LookupElement.contributorClass: Class<*>? by UserDataProperty(Key.create("LookupElement.LOOKUP_ELEMENT_CONTRIBUTOR"))
