@file:Suppress(<warning descr="[ERROR_SUPPRESSION] Suppression of error 'MISSING_DEPENDENCY_SUPERCLASS' might compile and work, but the compiler behavior is UNSPECIFIED and WILL NOT BE PRESERVED. Please report your use case to the Kotlin issue tracker instead: https://kotl.in/issue">"MISSING_DEPENDENCY_SUPERCLASS"</warning>) // mockSDK misses some superclasses

import javax.swing.JComponent

class SetToolTipTextPropertyStyleFix {
  fun test(comp: JComponent) {
    <warning descr="'JComponent.setToolTipText(String)' used instead of safe 'setToolTipText(HtmlChunk)'"><caret>comp.toolTipText = "hello"</warning>
  }
}
