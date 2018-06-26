// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrImmutableUtils")

package org.jetbrains.plugins.groovy.transformations.immutable

import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightAnnotation
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

internal const val immutableCopyWithKind = "@Immutable#copyWith"
internal const val immutableOrigin = "by @Immutable"
internal const val immutableCopyWith = "copyWith"
const val GROOVY_TRANSFORM_IMMUTABLE_BASE = "groovy.transform.ImmutableBase"
const val GROOVY_TRANSFORM_IMMUTABLE_OPTIONS = "groovy.transform.ImmutableOptions"
const val GROOVY_TRANSFORM_KNOWN_IMMUTABLE = "groovy.transform.KnownImmutable"


fun hasImmutableAnnotation(owner: PsiModifierListOwner): Boolean {
  return owner.hasAnnotation(GroovyCommonClassNames.GROOVY_LANG_IMMUTABLE)
         || owner.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_IMMUTABLE)
         || owner.hasAnnotation(GROOVY_TRANSFORM_IMMUTABLE_BASE)
}

fun collectImmutableAnnotations(alias: GrAnnotation, list: MutableList<GrAnnotation>) {
  val owner = alias.owner ?: return
  list.add(GrLightAnnotation(owner, alias, GROOVY_TRANSFORM_IMMUTABLE_BASE, emptyMap()))
  list.add(GrLightAnnotation(owner, alias, GROOVY_TRANSFORM_IMMUTABLE_OPTIONS, emptyMap()))
  list.add(GrLightAnnotation(owner, alias, GROOVY_TRANSFORM_KNOWN_IMMUTABLE, emptyMap()))
  list.add(GrLightAnnotation(owner, alias, GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR, mapOf("defaults" to "false")))
}