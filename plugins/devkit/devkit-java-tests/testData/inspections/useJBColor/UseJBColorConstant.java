import java.awt.Color;
import com.intellij.ui.JBColor;

class UseJBColorConstructor {

  private static final Color COLOR_CONSTANT = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.BLUE</warning>;
  private static final Color JB_COLOR_CONSTANT = new JBColor(Color.DARK_GRAY, Color.LIGHT_GRAY); // correct usage

  void any() {
    Color myColor = <warning descr="'java.awt.Color' used instead of 'JBColor'">Color.BLUE</warning>;
    takeColor(<warning descr="'java.awt.Color' used instead of 'JBColor'">Color.green</warning>);
    // correct cases:
    Color myGreen = new JBColor(Color.white, Color.YELLOW);
    takeColor(new JBColor(Color.BLACK, Color.WHITE));
  }

  void takeColor(Color color) {
    // do nothing
  }
}
