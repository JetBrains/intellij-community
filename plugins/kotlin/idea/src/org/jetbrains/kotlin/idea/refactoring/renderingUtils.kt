// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers

@K1Deprecation
fun wrapOrSkip(s: String, inCode: Boolean) = if (inCode) "<code>$s</code>" else s

@K1Deprecation
fun formatClassDescriptor(classDescriptor: DeclarationDescriptor) =
    IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(classDescriptor)

@K1Deprecation
fun formatPsiClass(
    psiClass: PsiClass,
    markAsJava: Boolean,
    inCode: Boolean
): String {
    val kind = if (psiClass.isInterface) "interface " else "class "
    var description: String = kind + PsiFormatUtil.formatClass(
        psiClass,
        PsiFormatUtilBase.SHOW_CONTAINING_CLASS or PsiFormatUtilBase.SHOW_NAME or PsiFormatUtilBase.SHOW_PARAMETERS or PsiFormatUtilBase.SHOW_TYPE
    )
    description = wrapOrSkip(description, inCode)

    return if (markAsJava) "[Java] $description" else description
}
