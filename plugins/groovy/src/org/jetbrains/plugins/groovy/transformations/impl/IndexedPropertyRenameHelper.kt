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
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.psi.PsiMember
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.refactoring.rename.GrRenameHelper
import org.jetbrains.plugins.groovy.transformations.indexedProperty.indexedMethodKind

class IndexedPropertyRenameHelper : GrRenameHelper {

  override fun getNewMemberName(member: PsiMember, newName: String): String? {
    if (member !is GrLightMethodBuilder || member.methodKind != indexedMethodKind) return null
    arrayOf("get", "set").filter {
      member.name.startsWith(it)
    }.forEach {
      return it + newName.capitalize()
    }
    return null
  }
}