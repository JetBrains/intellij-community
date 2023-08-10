import java.awt.Color

class UseJBColorConstantInConstant {

  companion object {
    private val COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">Col<caret>or.BLUE</warning>
  }
}
