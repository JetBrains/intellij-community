/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

interface GrCodeMembersProvider<in T : GrTypeDefinition> {

  fun getCodeMethods(definition: T): Array<GrMethod>

  fun getCodeFields(definition: T): Array<GrField>

  fun getCodeInnerClasses(definition: T): Array<GrTypeDefinition>
}
