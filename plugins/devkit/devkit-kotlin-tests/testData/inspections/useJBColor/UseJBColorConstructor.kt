import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Color as ColorAlias

class UseJBColorConstructor {

  companion object {
    private val COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color(1, 2, 3)</warning>
    private val JB_COLOR_CONSTANT: Color = JBColor(Color(1, 2, 3), Color(4, 5, 6)) // correct usage
  }

  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myColor1 = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color(4, 5, 6)</warning>
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">Color(7, 8, 9)</warning>)

    // type alias:
    @Suppress("UNUSED_VARIABLE")
    val myColor2 = <warning descr="'java.awt.Color' used instead of 'JBColor'">ColorAlias(4, 5, 6)</warning>
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">ColorAlias(7, 8, 9)</warning>)

    // correct cases:
    @Suppress("UNUSED_VARIABLE")
    val myGreen: Color = JBColor(Color(1, 2, 3), Color(4, 5, 6))
    takeColor(JBColor(Color(1, 2, 3), Color(4, 5, 6)))
  }

  fun takeColor(@Suppress("UNUSED_PARAMETER") color: Color?) {
    // do nothing
  }
}
