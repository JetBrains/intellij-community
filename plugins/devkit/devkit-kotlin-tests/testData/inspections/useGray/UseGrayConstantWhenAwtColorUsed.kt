import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Color as ColorAlias

class UseGrayConstantWhenAwtColorUsed {

  companion object {
    private val GRAY_CONSTANT = <warning descr="'java.awt.Color' used for gray">Color(125, 125, 125)</warning>
    private val NOT_GRAY_CONSTANT = Color(12, 13, 14)
    private const val GRAY_VALUE = 125
  }

  @Suppress("UNUSED_VARIABLE")
  fun any() {
    val myGray1 = <warning descr="'java.awt.Color' used for gray">Color(25, 25, 25)</warning>
    val myGray2 = <warning descr="'java.awt.Color' used for gray">Color(GRAY_VALUE, 125, GRAY_VALUE)</warning>
    takeColor(<warning descr="'java.awt.Color' used for gray">Color(125, 125, 125)</warning>)
    val myGray3: Color = JBColor(<warning descr="'java.awt.Color' used for gray">Color(25, 25, 25)</warning>, <warning descr="'java.awt.Color' used for gray">Color(125, 125, 125)</warning>)
    takeColor(JBColor(<warning descr="'java.awt.Color' used for gray">Color(25, 25, 25)</warning>, <warning descr="'java.awt.Color' used for gray">Color(125, 125, 125)</warning>))

    // type alias:
    val myGray21 = <warning descr="'java.awt.Color' used for gray">ColorAlias(25, 25, 25)</warning>
    val myGray22 = <warning descr="'java.awt.Color' used for gray">ColorAlias(GRAY_VALUE, 125, GRAY_VALUE)</warning>
    takeColor(<warning descr="'java.awt.Color' used for gray">ColorAlias(125, 125, 125)</warning>)
    val myGray23: Color = JBColor(<warning descr="'java.awt.Color' used for gray">ColorAlias(25, 25, 25)</warning>, <warning descr="'java.awt.Color' used for gray">ColorAlias(125, 125, 125)</warning>)
    takeColor(JBColor(<warning descr="'java.awt.Color' used for gray">ColorAlias(25, 25, 25)</warning>, <warning descr="'java.awt.Color' used for gray">ColorAlias(125, 125, 125)</warning>))

    // correct cases:
    val notGray1 = Color(15, 15, 1)
    val notGray2 = Color(15, 1, 15)
    val notGray3 = Color(1, 15, 15)
    val grayWithAlpha1 = Color(15, 16, 17, 100)
    val unsupportedGray = Color(0x000000)
    val invalidGrayValue1 = Color(-15, -15, -15)
    val invalidGrayValue2 = Color(2500, 2500, 2500)
    val constantGray1 = Color.LIGHT_GRAY
    val constantGray2 = Color.GRAY

    // type alias:
    val notGray21 = ColorAlias(15, 15, 1)
    val notGray22 = ColorAlias(15, 1, 15)
    val notGray23 = ColorAlias(1, 15, 15)
    val grayWithAlpha21 = ColorAlias(15, 16, 17, 100)
    val unsupportedGray2 = ColorAlias(0x000000)
    val invalidGrayValue21 = ColorAlias(-15, -15, -15)
    val invalidGrayValue22 = ColorAlias(2500, 2500, 2500)
    val constantGray21 = ColorAlias.LIGHT_GRAY
    val constantGray22 = ColorAlias.GRAY
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeColor(color: Color?) {
    // do nothing
  }
}