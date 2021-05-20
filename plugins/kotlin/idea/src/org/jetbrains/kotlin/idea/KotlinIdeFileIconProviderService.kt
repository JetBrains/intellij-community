// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons
import javax.swing.Icon

class KotlinIdeFileIconProviderService : KotlinIconProviderService() {
    private val icon by lazy {
        IconLoader.getIcon("/org/jetbrains/kotlin/idea/icons/kotlin_file.svg", KotlinIdeFileIconProviderService::class.java)
    }

    override fun getFileIcon(): Icon = icon

    override fun getLightVariableIcon(element: PsiModifierListOwner, flags: Int): Icon {
        val iconManager = IconManager.getInstance()
        val elementFlags = ElementPresentationUtil.getFlags(element, false)
        val baseIcon = iconManager.createLayeredIcon(element, PlatformIcons.VARIABLE_ICON, elementFlags)
        return ElementPresentationUtil.addVisibilityIcon(element, flags, baseIcon)
    }

}