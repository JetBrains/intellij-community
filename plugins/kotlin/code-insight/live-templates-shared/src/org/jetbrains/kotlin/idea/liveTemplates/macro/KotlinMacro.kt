// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Macro
import com.intellij.codeInsight.template.TemplateContextType
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType

abstract class KotlinMacro : Macro() {
    override fun isAcceptableInContext(context: TemplateContextType?): Boolean {
        return context is KotlinTemplateContextType
    }
}
