// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.ide.util.EditSourceUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import java.awt.event.MouseEvent

internal class SimpleNavigationHandler(private val target: SmartPsiElementPointer<PsiElement>) : GutterIconNavigationHandler<PsiElement> {
        override fun navigate(event: MouseEvent, element: PsiElement) {
            val targetElement = target.element ?: return

            EditSourceUtil.getDescriptor(targetElement)
                ?.takeIf { it.canNavigate() }
                ?.navigate(true)
        }
    }