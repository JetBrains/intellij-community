// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.usages

import com.intellij.devkit.apiDump.lang.ApiDumpLangBundle
import com.intellij.devkit.apiDump.lang.psi.*
import com.intellij.psi.PsiElement
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider

internal class ADUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement): UsageType? {
    when (element) {
      is ADMethodReference, is ADFieldReference, is ADConstructorReference -> return apiDeclarationApiType
      is ADTypeReference -> {
        when (element.parent) {
          is ADMethod -> return methodReturnValueApiType
          is ADParameter -> return parameterApiType
          is ADField -> return fieldTypeApiType
          is ADSuperType -> return superTypeApiType
          is ADCompanion -> return companionTypeApiType
          is ADClassHeader -> return apiDeclarationApiType
        }
      }
    }
    return null
  }
}

private val apiDeclarationApiType = UsageType(ApiDumpLangBundle.messagePointer("api.declaration"))
private val methodReturnValueApiType = UsageType(ApiDumpLangBundle.messagePointer("method.return.value.api"))
private val parameterApiType = UsageType(ApiDumpLangBundle.messagePointer("parameter.api"))
private val fieldTypeApiType = UsageType(ApiDumpLangBundle.messagePointer("field.type.api"))
private val superTypeApiType = UsageType(ApiDumpLangBundle.messagePointer("super.type.api"))
private val companionTypeApiType = UsageType(ApiDumpLangBundle.messagePointer("companion.type.api"))
