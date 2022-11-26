import com.intellij.ui.Gray
import java.awt.Color

class UseGrayConstantFixInLocalVariable {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myGray = Gray._25
  }
}
