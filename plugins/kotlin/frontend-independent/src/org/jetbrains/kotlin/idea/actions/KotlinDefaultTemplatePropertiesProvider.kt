// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.fileTemplates.DefaultTemplatePropertiesProvider
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import java.util.Properties

class KotlinDefaultTemplatePropertiesProvider : DefaultTemplatePropertiesProvider {
    override fun fillProperties(directory: PsiDirectory, props: Properties) {
        if (RootKindFilter.projectSources.matches(directory)) {
            props.setProperty(
                FileTemplate.ATTRIBUTE_PACKAGE_NAME,
                directory.getFqNameWithImplicitPrefixOrRoot().asString()
            )
        }
    }
}