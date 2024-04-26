// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("DEPRECATION")

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.reference.EntryPoint
import com.intellij.codeInspection.reference.RefElement
import com.intellij.openapi.util.DefaultJDOMExternalizer
import com.intellij.psi.PsiElement
import org.jdom.Element
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class KotlinJUnitStaticEntryPoint(@JvmField var wasSelected: Boolean = true) : EntryPoint() {
    override fun getDisplayName() = KotlinBundle.message("junit.static.methods")

    override fun isSelected() = wasSelected

    override fun isEntryPoint(refElement: RefElement, psiElement: PsiElement) = isEntryPoint(psiElement)

    private val staticJUnitAnnotations = listOf(
        "org.junit.BeforeClass",
        "org.junit.jupiter.api.BeforeAll",
        "org.junit.AfterClass",
        "org.junit.jupiter.api.AfterAll",
        "org.junit.runners.Parameterized.Parameters"
    )

    override fun isEntryPoint(psiElement: PsiElement) = psiElement is KtLightMethod &&
            AnnotationUtil.isAnnotated(psiElement, staticJUnitAnnotations, AnnotationUtil.CHECK_TYPE) &&
            AnnotationUtil.isAnnotated(psiElement, "kotlin.jvm.JvmStatic", AnnotationUtil.CHECK_TYPE)

    override fun readExternal(element: Element) {
        DefaultJDOMExternalizer.readExternal(this, element)
    }

    override fun setSelected(selected: Boolean) {
        this.wasSelected = selected
    }

    override fun writeExternal(element: Element) {
        if (!wasSelected) {
            DefaultJDOMExternalizer.writeExternal(this, element)
        }
    }
}