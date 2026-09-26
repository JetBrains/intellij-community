import java.awt.Color

class UseJBColorConstantInVariable {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.li<caret>ghtGray</warning>
  }
}
