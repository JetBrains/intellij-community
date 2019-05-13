/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

object BodyCodeMembersProvider : GrCodeMembersProvider<GrTypeDefinition> {

  override fun getCodeMethods(definition: GrTypeDefinition): Array<GrMethod> {
    return definition.body?.methods ?: GrMethod.EMPTY_ARRAY
  }

  override fun getCodeFields(definition: GrTypeDefinition): Array<GrField> {
    return definition.body?.fields ?: GrField.EMPTY_ARRAY
  }

  override fun getCodeInnerClasses(definition: GrTypeDefinition): Array<GrTypeDefinition> {
    return definition.body?.innerClasses ?: GrTypeDefinition.EMPTY_ARRAY
  }
}
