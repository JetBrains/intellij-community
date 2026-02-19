// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import org.jetbrains.idea.devkit.dom.productModules.ProductModulesElement

internal class ProductModulesXmlDomInspection : BasicDomElementsInspection<ProductModulesElement>(ProductModulesElement::class.java) 