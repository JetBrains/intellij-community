// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions.generate.tostring

import org.jetbrains.java.generate.GenerateToStringActionHandlerBase
import org.jetbrains.plugins.groovy.actions.GroovyToStringTemplatesManager

internal class GroovyGenerateToStringActionHandlerImpl : GenerateToStringActionHandlerBase(GroovyToStringTemplatesManager.getInstance())