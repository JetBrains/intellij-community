import java.awt.Color;
import com.intellij.ui.JBColor;

class UseJBColorConstructor {

  private static final Color COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.BLUE</warning>;

  void any() {
    Color myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.BLUE</warning>;
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">Color.green</warning>);
  }

  void takeColor(Color color) {
    // do nothing
  }
}
