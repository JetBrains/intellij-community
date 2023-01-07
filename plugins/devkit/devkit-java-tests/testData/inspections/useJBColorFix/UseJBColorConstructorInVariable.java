import java.awt.Color;

class UseJBColorConstructorInVariable {
  void any() {
    Color myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">new Co<caret>lor(4, 5, 6)</warning>;
  }
}
