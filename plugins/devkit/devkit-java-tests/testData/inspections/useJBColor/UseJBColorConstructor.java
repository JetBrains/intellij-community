import java.awt.Color;
import com.intellij.ui.JBColor;

class UseJBColorConstructor {

  private static final Color COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">new Color(1, 2, 3)</warning>;
  private static final Color JB_COLOR_CONSTANT = new JBColor(new Color(1, 2, 3), new Color(4, 5, 6)); // correct usage

  void any() {
    Color myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">new Color(4, 5, 6)</warning>;
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">new Color(7, 8, 9)</warning>);
    // correct cases:
    Color myGreen = new JBColor(new Color(1, 2, 3), new Color(4, 5, 6));
    takeColor(new JBColor(new Color(1, 2, 3), new Color(4, 5, 6)));
  }

  void takeColor(Color color) {
    // do nothing
  }
}
