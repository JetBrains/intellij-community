// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons
import javax.swing.Icon

class KotlinIdeFileIconProviderService : KotlinIconProviderService() {
    override fun getFileIcon(): Icon = KotlinIcons.FILE

    override fun getBuiltInFileIcon(): Icon = KotlinIcons.FILE

    override fun getLightVariableIcon(element: PsiModifierListOwner, flags: Int): Icon {
        val iconManager = IconManager.getInstance()
        val elementFlags = ElementPresentationUtil.getFlags(element, false)
        val baseIcon = iconManager.createLayeredIcon(element, PlatformIcons.VARIABLE_ICON, elementFlags)
        return ElementPresentationUtil.addVisibilityIcon(element, flags, baseIcon)
    }

}