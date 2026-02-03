// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.sanity

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.PatternContext
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.visitor.KotlinMatchingVisitor
import org.jetbrains.kotlin.psi.KtElement

class KotlinLightSanityProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        addLibrary(AccessDeniedException::class.java, OrderRootType.CLASSES, model)
        addLibrary(PsiElement::class.java, OrderRootType.CLASSES, model)
        addLibrary(KtElement::class.java, OrderRootType.CLASSES, model)
        addLibrary(PatternContext::class.java, OrderRootType.CLASSES, model)
        addLibrary(NodeFilter::class.java, OrderRootType.CLASSES, model)
        addLibrary(KotlinMatchingVisitor::class.java, OrderRootType.CLASSES, model)
    }
}