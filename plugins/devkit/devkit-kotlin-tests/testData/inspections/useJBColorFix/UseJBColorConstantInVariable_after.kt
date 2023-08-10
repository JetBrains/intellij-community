import com.intellij.ui.JBColor
import java.awt.Color

class UseJBColorConstantInVariable {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myColor = JBColor.LIGHT_GRAY
  }
}
