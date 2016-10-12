/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

object BodyCodeMembersProvider : GrCodeMembersProvider<GrTypeDefinition> {

  override fun getCodeMethods(definition: GrTypeDefinition): Array<GrMethod> {
    val body = definition.body
    return if (body == null) GrMethod.EMPTY_ARRAY else body.methods
  }

  override fun getCodeFields(definition: GrTypeDefinition): Array<GrField> {
    val body = definition.body
    return if (body == null) GrField.EMPTY_ARRAY else body.fields
  }

  override fun getCodeInnerClasses(definition: GrTypeDefinition): Array<GrTypeDefinition> {
    val body = definition.body
    return if (body == null) GrTypeDefinition.EMPTY_ARRAY else body.innerClasses
  }
}
