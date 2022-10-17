import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Color.GREEN

class UseJBColorConstant {

  companion object {
    private val COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.BLUE</warning>
    private val COLOR_CONSTANT_STATIC_IMPORT = <warning descr="'java.awt.Color' used instead of 'JBColor'">GREEN</warning>
    private val JB_COLOR_CONSTANT: Color = JBColor(Color.DARK_GRAY, Color.LIGHT_GRAY) // correct usage
    private val JB_COLOR_CONSTANT_STATIC_IMPORT: Color = JBColor(GREEN, GREEN) // correct usage
  }

  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.BLUE</warning>
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">Color.green</warning>)
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">GREEN</warning>)
    // correct cases:
    @Suppress("UNUSED_VARIABLE")
    val myGreen: Color = JBColor(Color.white, Color.YELLOW)
    takeColor(JBColor(Color.BLACK, Color.WHITE))
  }

  fun takeColor(@Suppress("UNUSED_PARAMETER") color: Color) {
    // do nothing
  }
}
