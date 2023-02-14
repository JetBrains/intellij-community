import java.awt.Color

class UseJBColorConstantInMethodParam {
  fun any() {
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">Color.g<caret>reen</warning>)
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeColor(color: Color) {
    // do nothing
  }
}
