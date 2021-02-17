package com.intellij.xdebugger.impl.frame

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.OnePixelSplitter
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
    XWatchesViewImpl(session, watchesInVariables, isVertical), DnDNativeTarget, XWatchesView {

    companion object {
      const val proportionKey = "debugger.immediate.window.in.watches.proportion.key"
    }

    init {
        val bottomLocalsComponentProvider = (session.debugProcess as? XDebugSessionTabCustomizer)?.bottomLocalsComponentProvider
        if (bottomLocalsComponentProvider != null)
        {
            // it's hacky, we change default watches component to splitter, this way allows not to change base components
            DataManager.removeDataProvider(myComponent)
          val splitter = OnePixelSplitter(true, PropertiesComponent.getInstance().getFloat(proportionKey, 0.5f), 0.01f, 0.99f)
                .apply {
                    splitterProportionKey = proportionKey
                    dividerWidth = 1
                    divider.background = UIUtil.CONTRAST_BORDER_COLOR
                }
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
}