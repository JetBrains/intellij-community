import com.intellij.ui.JBColor
import java.awt.Color.BLACK

class UseJBColorConstantStaticImportInVariable {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myColor = JBColor.BLACK
  }
}
