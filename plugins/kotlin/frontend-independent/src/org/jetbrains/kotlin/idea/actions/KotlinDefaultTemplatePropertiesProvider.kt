// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.fileTemplates.DefaultTemplatePropertiesProvider
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import java.util.*

class KotlinDefaultTemplatePropertiesProvider : DefaultTemplatePropertiesProvider {
    override fun fillProperties(directory: PsiDirectory, props: Properties) {
        props.setProperty(
          FileTemplate.ATTRIBUTE_PACKAGE_NAME,
          directory.getFqNameWithImplicitPrefixOrRoot().asString()
        )
    }
}