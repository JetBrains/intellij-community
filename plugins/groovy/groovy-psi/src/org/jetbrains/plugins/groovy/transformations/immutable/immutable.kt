// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrImmutableUtils")

package org.jetbrains.plugins.groovy.transformations.immutable

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightAnnotation
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

//set from org.apache.groovy.ast.tools.ImmutablePropertyUtils
internal val builtinImmutables = setOf(
  "java.lang.Class",
  "java.lang.Boolean",
  "java.lang.Byte",
  "java.lang.Character",
  "java.lang.Double",
  "java.lang.Float",
  "java.lang.Integer",
  "java.lang.Long",
  "java.lang.Short",
  "java.lang.String",
  "java.math.BigInteger",
  "java.math.BigDecimal",
  "java.awt.Color",
  "java.net.URI",
  "java.util.UUID",
  "java.time.DayOfWeek",
  "java.time.Duration",
  "java.time.Instant",
  "java.time.LocalDate",
  "java.time.LocalDateTime",
  "java.time.LocalTime",
  "java.time.Month",
  "java.time.MonthDay",
  "java.time.OffsetDateTime",
  "java.time.OffsetTime",
  "java.time.Period",
  "java.time.Year",
  "java.time.YearMonth",
  "java.time.ZonedDateTime",
  "java.time.ZoneOffset",
  "java.time.ZoneRegion",
  "java.time.chrono.ChronoLocalDate",
  "java.time.chrono.ChronoLocalDateTime",
  "java.time.chrono.Chronology",
  "java.time.chrono.ChronoPeriod",
  "java.time.chrono.ChronoZonedDateTime",
  "java.time.chrono.Era",
  "java.time.format.DecimalStyle",
  "java.time.format.FormatStyle",
  "java.time.format.ResolverStyle",
  "java.time.format.SignStyle",
  "java.time.format.TextStyle",
  "java.time.temporal.IsoFields",
  "java.time.temporal.JulianFields",
  "java.time.temporal.ValueRange",
  "java.time.temporal.WeekFields"
)

internal const val immutableCopyWithKind = "@Immutable#copyWith"
internal const val immutableOrigin = "by @Immutable"
internal const val immutableCopyWith = "copyWith"
internal const val KNOWN_IMMUTABLES_OPTION = "knownImmutables"
internal const val KNOWN_IMMUTABLE_CLASSES_OPTION = "knownImmutableClasses"

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

fun isImmutable(field: GrField): Boolean {
  val containingClass = field.containingClass ?: return false
  val type = field.type
  if (type is PsiPrimitiveType) return true
  if (type is PsiArrayType) return true
  if (type !is PsiClassType) return false
  val psiClass = type.resolve() ?: return false
  if (psiClass.isEnum) return true

  if (builtinImmutables.contains(psiClass.qualifiedName)) return true
  if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_COLLECTION)
      || InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_MAP)
      || InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_CLONEABLE)) return true

  containingClass.getAnnotation(GROOVY_TRANSFORM_IMMUTABLE_OPTIONS)?.let {
    val immutableFields = GrAnnotationUtil.getStringArrayValue(it, KNOWN_IMMUTABLES_OPTION, true)
    if (immutableFields.contains(field.name)) return true
    val immutableClasses = GrAnnotationUtil.getClassArrayValue(it, KNOWN_IMMUTABLE_CLASSES_OPTION, true)
    if (immutableClasses.contains(psiClass)) return true
  }

  if (hasImmutableAnnotation(psiClass)) return true
  psiClass.getAnnotation(GROOVY_TRANSFORM_KNOWN_IMMUTABLE)?.let { return true }

  return false
}