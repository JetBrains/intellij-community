import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color

class UseGrayConstantFixInJBColorParam {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myGray: Color = JBColor(Gray._25, Gray._125)
  }
}
