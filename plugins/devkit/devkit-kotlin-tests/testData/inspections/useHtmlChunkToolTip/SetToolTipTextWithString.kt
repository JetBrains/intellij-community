@file:Suppress(<warning descr="[ERROR_SUPPRESSION]">"MISSING_DEPENDENCY_SUPERCLASS"</warning>) // mockSDK misses some superclasses

import javax.swing.JComponent

class SetToolTipTextWithString {
  fun test(comp: JComponent) {
    comp.<warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'">setToolTipText("hello")</warning>
    comp.setToolTipText(null)
    <warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'">comp.toolTipText = "hello"</warning>
    <warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'">comp.toolTipText = getString()</warning>
    comp.toolTipText = null
  }

  fun getString(): String = ""
}
