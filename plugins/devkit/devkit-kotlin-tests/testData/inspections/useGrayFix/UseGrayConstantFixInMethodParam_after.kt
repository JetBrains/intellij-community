import com.intellij.ui.Gray
import java.awt.Color

class UseGrayConstantFixInMethodParam {
  fun any() {
    takeColor(Gray._125)
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeColor(color: Color?) {
    // do nothing
  }
}
