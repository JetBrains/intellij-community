import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color

class UseGrayConstantFixInJBColorParam {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myGray: Color = JBColor(<warning descr="'java.awt.Color' used for gray">Col<caret>or(25, 25, 25)</warning>, Gray._125)
  }
}
