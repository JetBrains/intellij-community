// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.injection

import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InjectionInfo(private val languageId: String?, val prefix: String?, val suffix: String?) {
    fun toBaseInjection(injectionSupport: LanguageInjectionSupport): BaseInjection? {
        if (languageId == null) return null

        val baseInjection = BaseInjection(injectionSupport.id)
        baseInjection.injectedLanguageId = languageId

        if (prefix != null) {
            baseInjection.prefix = prefix
        }

        if (suffix != null) {
            baseInjection.suffix = suffix
        }

        return baseInjection
    }

    companion object {
        fun fromBaseInjection(baseInjection: BaseInjection?): InjectionInfo? {
            if (baseInjection == null) {
                return null
            }

            return InjectionInfo(
              baseInjection.injectedLanguageId,
              baseInjection.prefix,
              baseInjection.suffix
            )
        }
    }
}