import com.intellij.ui.Gray
import java.awt.Color

class UseGrayConstantFixWhenNumberConstantsReferenced {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val gray = Gray._125
  }

  companion object {
    private const val GRAY_VALUE = 125
  }
}
