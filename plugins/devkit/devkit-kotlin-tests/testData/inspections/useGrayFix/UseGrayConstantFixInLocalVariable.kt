import java.awt.Color

class UseGrayConstantFixInLocalVariable {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myGray = <warning descr="'java.awt.Color' used for gray">Co<caret>lor(25, 25, 25)</warning>
  }
}
