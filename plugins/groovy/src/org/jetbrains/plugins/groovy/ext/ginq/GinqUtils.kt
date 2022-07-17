// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GinqUtils")

package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.psi.CommonClassNames.JAVA_LANG_LONG
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL

const val GROOVY_GINQ_TRANSFORM_GQ = "groovy.ginq.transform.GQ"

val GINQ_METHODS: Set<String> = setOf(
  "GQ",
  "GQL",
)

val JOINS : Set<String> = setOf(
  "join",
  "innerjoin",
  "innerhashjoin",
  "leftjoin",
  "lefthashjoin",
  "crossjoin",
  "rightjoin",
  "righthashjoin",
  "fulljoin",
  "fullhashjoin",
)

const val KW_CROSSJOIN: String = "crossjoin"

const val KW_FROM: String = "from"
const val KW_ON: String  = "on"
const val KW_WHERE: String  = "where"
const val KW_GROUPBY: String  = "groupby"
const val KW_HAVING: String  = "having"
const val KW_ORDERBY: String  = "orderby"
const val KW_LIMIT: String  = "limit"
const val KW_SELECT = "select"

const val KW_ASC: String = "asc"
const val KW_DESC: String = "desc"
const val KW_NULLSFIRST: String = "nullsfirst"
const val KW_NULLSLAST: String = "nullslast"

const val GINQ_EXISTS: String = "exists"

const val KW_SHUTDOWN = "shutdown"
const val KW_IMMEDIATE = "immediate"
const val KW_ABORT = "abort"

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE : String =
  "org.apache.groovy.ginq.provider.collection.runtime.Queryable"

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_WINDOW : String =
  "org.apache.groovy.ginq.provider.collection.runtime.Window"

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD: String =
  "org.apache.groovy.ginq.provider.collection.runtime.NamedRecord"


internal const val OVER_ORIGIN_INFO = "Ginq over"

internal interface GinqSupport {

  fun getQueryable(context: PsiElement): PsiClass? =
    JavaPsiFacade.getInstance(context.project).findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, context.resolveScope)

  fun findQueryableMethod(context: PsiElement, methodName: String): PsiMethod? =
    getQueryable(context)?.findMethodsByName(methodName, false)?.singleOrNull()

  fun getNamedRecord(context: PsiElement) : PsiClass? =
    JavaPsiFacade.getInstance(context.project).findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, context.resolveScope)

  fun getWindow(context: PsiElement): PsiClass? =
    JavaPsiFacade.getInstance(context.project).findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_WINDOW, context.resolveScope)
}

data class Signature(val returnType: String, val parameters: List<Pair<String, String>>)

val windowFunctions : Map<String, Signature> = mapOf(
  "rowNumber" to Signature(JAVA_LANG_LONG, emptyList()),
  "rank" to Signature(JAVA_LANG_LONG, emptyList()),
  "denseRank" to Signature(JAVA_LANG_LONG, emptyList()),
  "percentRank" to Signature(JAVA_MATH_BIG_DECIMAL, emptyList()),
  "cumeDist" to Signature(JAVA_MATH_BIG_DECIMAL, emptyList()),
  "ntile" to Signature(JAVA_LANG_LONG, listOf("expr" to JAVA_LANG_LONG)),
  "lead" to Signature("T", listOf("expr" to "T", "?offset" to JAVA_LANG_LONG, "?default" to "T")),
  "lag" to Signature("T", listOf("expr" to "T", "?offset" to JAVA_LANG_LONG, "?default" to "T")),
  "firstValue" to Signature("T", listOf("expr" to "T")),
  "lastValue" to Signature("T", listOf("expr" to "T")),
  "nthValue" to Signature("T", listOf("expr" to "T", "n" to JAVA_LANG_LONG)),
  )

sealed interface GinqRootPsiElement {
  val psi: GroovyPsiElement

  @JvmInline
  value class Call(override val psi: GrMethodCall) : GinqRootPsiElement

  @JvmInline
  value class Method(override val psi: GrMethod) : GinqRootPsiElement
}

