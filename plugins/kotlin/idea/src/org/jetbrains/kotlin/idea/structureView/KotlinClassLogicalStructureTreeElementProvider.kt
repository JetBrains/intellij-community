// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.structureView.logical.LogicalStructureTreeElementProvider
import com.intellij.ide.structureView.StructureViewTreeElement
import org.jetbrains.kotlin.asJava.classes.KtLightClass

class KotlinClassLogicalStructureTreeElementProvider: LogicalStructureTreeElementProvider<KtLightClass> {

    override fun getModelClass(): Class<KtLightClass> = KtLightClass::class.java

    override fun getTreeElement(model: KtLightClass): StructureViewTreeElement {
        return KotlinStructureViewElement(model.kotlinOrigin ?: model, false)
    }

}