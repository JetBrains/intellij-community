// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.testIntegration

import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspectionFilter
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class MainMethodMayBeStaticFilter : GrMethodMayBeStaticInspectionFilter() {
  override fun isIgnored(method: GrMethod): Boolean {
    return GroovyConfigUtils.getInstance().isVersionAtLeast(method, GroovyConfigUtils.GROOVY5_0) && method.name == "main"
  }
}