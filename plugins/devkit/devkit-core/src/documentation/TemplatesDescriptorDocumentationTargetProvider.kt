// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class TemplatesDescriptorDocumentationTargetProvider : AbstractXmlDescriptorDocumentationTargetProvider() {

  override val docYamlCoordinates = DocumentationDataCoordinates(
    "https://jb.gg/sdk-docs/templates-descriptor.yaml", // -> https://raw.githubusercontent.com/JetBrains/intellij-community/refs/heads/master/plugins/devkit/devkit-core/resources/documentation/templates-descriptor.yaml
    "/documentation/templates-descriptor.yaml"
  )

  override fun isApplicable(element: PsiElement, originalElement: PsiElement?): Boolean {
    return originalElement != null && DescriptorUtil.isTemplatesXml(originalElement.containingFile)
  }

}
