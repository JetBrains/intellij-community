// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.dsl

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class MemberBuilder(val context: TransformationContext) {

  fun method(@NlsSafe name: String, builder: GrLightMethodBuilder.() -> Unit): GrLightMethodBuilder {
    return GrLightMethodBuilder(context.manager, name).apply(builder)
  }

  fun constructor(builder: GrLightMethodBuilder.() -> Unit): GrLightMethodBuilder {
    return GrLightMethodBuilder(context.codeClass).apply(builder)
  }
}