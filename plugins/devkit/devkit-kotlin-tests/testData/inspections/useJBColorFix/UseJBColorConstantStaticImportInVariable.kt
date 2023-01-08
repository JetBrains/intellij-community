import java.awt.Color.BLACK

class UseJBColorConstantStaticImportInVariable {
  fun any() {
    @Suppress("UNUSED_VARIABLE")
    val myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">BL<caret>ACK</warning>
  }
}
