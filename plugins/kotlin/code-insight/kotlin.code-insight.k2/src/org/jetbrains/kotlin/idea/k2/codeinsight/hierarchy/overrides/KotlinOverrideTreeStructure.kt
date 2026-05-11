// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.overrides

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.collectInheritors
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject


class KotlinOverrideTreeStructure(project: Project, declaration: KtCallableDeclaration) : HierarchyTreeStructure(project, null) {
    private val expectOrBaseElement: SmartPsiElementPointer<KtCallableDeclaration>
    private val baseElement = declaration.createSmartPointer()

    init {
        val expectDeclaration =
            ActionUtil.underModalProgress(declaration.project, KotlinBundle.message("kotlin.override.tree.structure.loading")) {
                declaration.expectDeclarationIfAny()
            } as? KtCallableDeclaration ?: declaration
        expectOrBaseElement = expectDeclaration.createSmartPointer()
        setBaseElement(KotlinOverrideHierarchyNodeDescriptor(null, expectDeclaration.containingClassOrObject!!, expectDeclaration))
    }

    override fun buildChildren(nodeDescriptor: HierarchyNodeDescriptor): Array<Any> {
        val expectOrBase = expectOrBaseElement.element ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val baseElement = baseElement.element ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val psiElement = nodeDescriptor.psiElement ?: return ArrayUtil.EMPTY_OBJECT_ARRAY

        val subclasses = collectInheritors(psiElement, baseElement.module, psiElement.useScope, baseElement.useScope)
        return subclasses.mapNotNull {
            it.unwrapped?.let { subclass -> KotlinOverrideHierarchyNodeDescriptor(nodeDescriptor, subclass, expectOrBase) }
        }.filter { it.calculateState() != AllIcons.Hierarchy.MethodNotDefined }.toTypedArray()
    }
}
