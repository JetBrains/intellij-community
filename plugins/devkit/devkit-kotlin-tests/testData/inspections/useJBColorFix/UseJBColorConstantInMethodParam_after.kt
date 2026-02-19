import com.intellij.ui.JBColor
import java.awt.Color

class UseJBColorConstantInMethodParam {
  fun any() {
    takeColor(JBColor.GREEN)
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeColor(color: Color) {
    // do nothing
  }
}
