import java.awt.Color

class UseGrayConstantFixWhenNumberConstantsReferenced {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val gray = <warning descr="'java.awt.Color' used for gray">C<caret>olor(GRAY_VALUE, 125, GRAY_VALUE)</warning>
  }

  companion object {
    private const val GRAY_VALUE = 125
  }
}
