@file:Suppress(<warning descr="[ERROR_SUPPRESSION]">"MISSING_DEPENDENCY_SUPERCLASS"</warning>) // mockSDK misses some superclasses

import javax.swing.JComponent

class SetToolTipTextWithStringFixRaw {
  fun test(comp: JComponent) {
    comp.<warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'">setToolTipText(<caret>"hello")</warning>
  }
}
