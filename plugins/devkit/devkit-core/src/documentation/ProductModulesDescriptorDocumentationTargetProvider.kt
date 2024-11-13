// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal class ProductModulesDescriptorDocumentationTargetProvider : AbstractXmlDescriptorDocumentationTargetProvider() {

  override val docYamlCoordinates = DocumentationDataCoordinates(
    // https://raw.githubusercontent.com/JetBrains/intellij-community/refs/heads/master/plugins/devkit/devkit-core/resources/documentation/product-modules-descriptor.yaml
    "https://jb.gg/sdk-docs/product-modules-descriptor.yaml",
    "/documentation/product-modules-descriptor.yaml"
  )

  override fun isApplicable(element: PsiElement, originalElement: PsiElement?): Boolean {
    return originalElement != null && DescriptorUtil.isProductModulesXml(originalElement.containingFile)
  }

}
