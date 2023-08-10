import java.awt.Color;

class UseJBColorConstructor {
  void any() {
    Color myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.li<caret>ghtGray</warning>;
  }
}
