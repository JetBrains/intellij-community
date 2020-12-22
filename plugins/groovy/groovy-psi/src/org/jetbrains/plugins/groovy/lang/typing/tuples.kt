// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType

private val tupleRegex = "groovy.lang.Tuple(\\d+)".toRegex()

/**
 * @return number or tuple component count of [groovy.lang.Tuple] inheritor type,
 * or `null` if the [type] doesn't represent an inheritor of [groovy.lang.Tuple] type
 */
fun getTupleComponentCountOrNull(type: PsiType): Int? {
  val classType = type as? PsiClassType
                  ?: return null
  val fqn = classType.resolve()?.qualifiedName
            ?: return null
  return tupleRegex.matchEntire(fqn)
    ?.groupValues
    ?.getOrNull(1)
    ?.toIntOrNull()
}
