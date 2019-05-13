// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.GrReferenceResolveRunner
import org.jetbrains.plugins.groovy.lang.resolve.processors.AllVariantsProcessor

fun GrReferenceExpression.resolveIncomplete(): Collection<GroovyResolveResult> {
  val name = referenceName ?: return emptyList()
  val processor = AllVariantsProcessor(name, this)
  GrReferenceResolveRunner(this, processor).resolveReferenceExpression()
  return processor.results
}
