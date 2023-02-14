import java.awt.Color;

class UseJBColorConstructorInMethodParam {
  void any() {
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">new Col<caret>or(7, 8, 9)</warning>);
  }

  void takeColor(Color color) {
    // do nothing
  }
}
