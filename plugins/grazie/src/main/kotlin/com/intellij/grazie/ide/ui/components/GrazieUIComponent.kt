package com.intellij.grazie.ide.ui.components

import com.intellij.grazie.GrazieConfig
import javax.swing.JComponent

internal interface GrazieUIComponent {
  val component: JComponent

  /** Should return true, if state of component does not match passed state of GrazieConfig (hence, it was modified) */
  fun isModified(state: GrazieConfig.State): Boolean

  /** Resets state (and view) of component to passed state of GrazieConfig */
  fun reset(state: GrazieConfig.State)

  /** Applies changes from component to passed state of GrazieConfig and returns new version */
  fun apply(state: GrazieConfig.State): GrazieConfig.State

  /** View-only components, that can not be modified somehow */
  interface ViewOnly : GrazieUIComponent {
    override fun isModified(state: GrazieConfig.State) = false
    override fun apply(state: GrazieConfig.State) = state
    override fun reset(state: GrazieConfig.State) {}
  }

  /** Components, that change representation, but delegate actual data handing to `impl` */
  interface Delegating : GrazieUIComponent {
    val impl: GrazieUIComponent

    override fun isModified(state: GrazieConfig.State) = impl.isModified(state)
    override fun apply(state: GrazieConfig.State) = impl.apply(state)
    override fun reset(state: GrazieConfig.State) = impl.reset(state)
  }
}