@file:Suppress("MISSING_DEPENDENCY_SUPERCLASS") // mockSDK misses some superclasses

import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.text.HtmlChunk
import javax.swing.JComponent

class SetToolTipTextWithStringFix {
  fun test(comp: JComponent) {
      comp.setToolTipText(HtmlChunk.text("hello"))
  }
}
