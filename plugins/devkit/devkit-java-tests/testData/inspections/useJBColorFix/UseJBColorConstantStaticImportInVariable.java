import java.awt.Color;
import static java.awt.Color.BLACK;

class UseJBColorConstantStaticImportInVariable {
  void any() {
    Color myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">BL<caret>ACK</warning>;
  }
}
