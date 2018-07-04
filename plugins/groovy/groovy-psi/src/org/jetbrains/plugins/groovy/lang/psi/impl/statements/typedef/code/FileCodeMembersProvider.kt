/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass

object FileCodeMembersProvider : GrCodeMembersProvider<GroovyScriptClass> {

  override fun getCodeMethods(definition: GroovyScriptClass): Array<GrMethod> = definition.containingFile.methods

  override fun getCodeFields(definition: GroovyScriptClass): Array<GrField> = GrField.EMPTY_ARRAY

  override fun getCodeInnerClasses(definition: GroovyScriptClass): Array<GrTypeDefinition> = GrTypeDefinition.EMPTY_ARRAY
}
