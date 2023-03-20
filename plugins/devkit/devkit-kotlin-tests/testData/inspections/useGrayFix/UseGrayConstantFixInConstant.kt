import java.awt.Color

object UseGrayConstantFixInConstant {
  private val GRAY_CONSTANT = <warning descr="'java.awt.Color' used for gray">Co<caret>lor(125, 125, 125)</warning>
}
