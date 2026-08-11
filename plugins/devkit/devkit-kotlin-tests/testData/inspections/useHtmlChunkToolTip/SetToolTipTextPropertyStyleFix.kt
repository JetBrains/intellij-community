@file:Suppress(<warning descr="[ERROR_SUPPRESSION]">"MISSING_DEPENDENCY_SUPERCLASS"</warning>) // mockSDK misses some superclasses

import javax.swing.JComponent

class SetToolTipTextPropertyStyleFix {
  fun test(comp: JComponent) {
    <warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'"><caret>comp.toolTipText = "hello"</warning>
  }
}
