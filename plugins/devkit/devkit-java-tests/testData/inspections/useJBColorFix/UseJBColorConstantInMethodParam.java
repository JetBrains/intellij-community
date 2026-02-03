import java.awt.Color;

class UseJBColorConstructor {
  void any() {
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">Color.g<caret>reen</warning>);
  }

  void takeColor(Color color) {
    // do nothing
  }
}
