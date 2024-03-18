// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

private class FirStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ID

    override fun getDisplayName(): String = DISPLAY_NAME

    override fun isAvailable(project: Project): Boolean {
        return Registry.`is`(REGISTRY_KEY, /* defaultValue = */ false)
    }

    override fun createWidget(project: Project): StatusBarWidget = Widget()

    companion object {
        const val ID = "kotlin.fir.ide"

        @NlsSafe
        private const val DISPLAY_NAME = "K2 Kotlin Mode"
    }
}

private class FirStatusBarWidgetListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
        if (value.key != REGISTRY_KEY) return
        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed || project.isDefault) continue
                val manager = project.service<StatusBarWidgetsManager>()
                manager.updateWidget(FirStatusBarWidgetFactory::class.java)
            }
        }
    }
}

private const val REGISTRY_KEY = "kotlin.k2.show.fir.statusbar.icon"

private class Widget : StatusBarWidget, StatusBarWidget.IconPresentation {
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun ID(): String = FirStatusBarWidgetFactory.ID
    override fun getTooltipText(): String = "K2 Kotlin Mode"
    override fun getIcon(): Icon = KotlinIcons.FIR
}