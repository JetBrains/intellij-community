package com.intellij.xdebugger.impl.frame

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ui.Splitter
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer
import java.awt.BorderLayout
import javax.swing.JPanel

class XWatchesViewImpl2(
    val session: XDebugSessionImpl,
    watchesInVariables: Boolean,
    isVertical: Boolean,
    val layoutDisposable: Disposable
) :
    XWatchesViewImpl(session, watchesInVariables, isVertical, false), DnDNativeTarget, XWatchesView {

    init {
        val bottomLocalsComponentProvider = (session.debugProcess as? XDebugSessionTabCustomizer)?.bottomLocalsComponentProvider
        if (bottomLocalsComponentProvider == null)
            super.createToolbar(AnAction.EMPTY_ARRAY)
        else {
            // it's hacky, we change default watches component to splitter, this way allows not to change base components
            DataManager.removeDataProvider(myComponent)
            val splitter = Splitter(true).apply {
                dividerWidth = 1
                divider.background = UIUtil.CONTRAST_BORDER_COLOR
            }
            super.createToolbar(arrayOf(createExtraAction(splitter, bottomLocalsComponentProvider)))
            val toolbar = myComponent.getComponent(1) as ActionToolbarImpl
            val locals = myComponent.getComponent(0) as JPanel
            splitter.firstComponent = locals

            if (PropertiesComponent.getInstance().getBoolean("debugger.immediate.window.in.watches", true))
                splitter.secondComponent = bottomLocalsComponentProvider.createBottomLocalsComponent(layoutDisposable)

            myComponent = BorderLayoutPanel()
            myComponent.add(splitter)
            myComponent.add(toolbar, if (isVertical) BorderLayout.WEST else BorderLayout.NORTH)
            DataManager.registerDataProvider(myComponent, this)
        }
    }

    fun createExtraAction(
        splitter: Splitter,
        sessionTabComponentProvider: XDebugSessionTabCustomizer.SessionTabComponentProvider
    ): AnAction {
        return object : ToggleAction(sessionTabComponentProvider.componentIconPopupText, null, sessionTabComponentProvider.componentIcon) {
            private var bottomComponentIsVisible: Boolean =
                PropertiesComponent.getInstance().getBoolean("debugger.immediate.window.in.watches", true)
            override fun isSelected(e: AnActionEvent): Boolean = bottomComponentIsVisible
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                bottomComponentIsVisible = !bottomComponentIsVisible
                splitter.apply {
                    if (bottomComponentIsVisible)
                        secondComponent = sessionTabComponentProvider.createBottomLocalsComponent(layoutDisposable)
                    else
                        secondComponent = null
                    revalidate()
                    repaint()
                }
                sessionTabComponentProvider.visibilityChanged(bottomComponentIsVisible)
                PropertiesComponent.getInstance().setValue("debugger.immediate.window.in.watches", bottomComponentIsVisible, true)
            }
        }
    }
}