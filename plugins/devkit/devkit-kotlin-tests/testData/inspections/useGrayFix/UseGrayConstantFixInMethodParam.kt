import java.awt.Color

class UseGrayConstantFixInMethodParam {
  fun any() {
    takeColor(<warning descr="'java.awt.Color' used for gray">Col<caret>or(125, 125, 125)</warning>)
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeColor(color: Color?) {
    // do nothing
  }
}
