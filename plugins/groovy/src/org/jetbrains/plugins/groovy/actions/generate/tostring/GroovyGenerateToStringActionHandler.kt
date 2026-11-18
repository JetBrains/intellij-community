// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions.generate.tostring

import org.jetbrains.java.generate.JavaGenerateToStringActionHandler
import org.jetbrains.plugins.groovy.actions.GroovyToStringTemplatesManager

internal class GroovyGenerateToStringActionHandler : JavaGenerateToStringActionHandler(GroovyToStringTemplatesManager.getInstance())