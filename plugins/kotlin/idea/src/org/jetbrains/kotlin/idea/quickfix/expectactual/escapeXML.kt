// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.expectactual

import com.intellij.openapi.util.text.StringUtil

fun escapeXml(text: String): String = StringUtil.escapeXmlEntities(text)