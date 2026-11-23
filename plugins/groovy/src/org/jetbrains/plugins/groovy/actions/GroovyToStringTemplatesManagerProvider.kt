// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions

import org.jetbrains.java.generate.template.TemplatesManager
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManagerProvider

internal class GroovyToStringTemplatesManagerProvider : ToStringTemplatesManagerProvider {
  override val templatesManager: TemplatesManager
    get() = GroovyToStringTemplatesManager.getInstance()
}
