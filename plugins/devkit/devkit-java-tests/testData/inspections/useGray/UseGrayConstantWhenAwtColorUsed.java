import java.awt.Color;
import com.intellij.ui.JBColor;
import com.intellij.ui.Gray;

class UseGrayConstantWhenAwtColorUsed {

  private static final Color GRAY_CONSTANT = <warning descr="'java.awt.Color' used for gray">new Color(125, 125, 125)</warning>;
  private static final Color NOT_GRAY_CONSTANT = new Color(12, 13, 14);

  private static final int GRAY_VALUE = 125;

  void any() {
    Color myGray1 = <warning descr="'java.awt.Color' used for gray">new Color(25, 25, 25)</warning>;
    Color myGray2 = <warning descr="'java.awt.Color' used for gray">new Color(GRAY_VALUE, 125, GRAY_VALUE)</warning>;
    takeColor(<warning descr="'java.awt.Color' used for gray">new Color(125, 125, 125)</warning>);
    Color myGray3 = new JBColor(<warning descr="'java.awt.Color' used for gray">new Color(25, 25, 25)</warning>, <warning descr="'java.awt.Color' used for gray">new Color(125, 125, 125)</warning>);
    takeColor(new JBColor(<warning descr="'java.awt.Color' used for gray">new Color(25, 25, 25)</warning>, <warning descr="'java.awt.Color' used for gray">new Color(125, 125, 125)</warning>));

    // correct cases:
    Color notGray1 = new Color(15, 15, 1);
    Color notGray2 = new Color(15, 1, 15);
    Color notGray3 = new Color(1, 15, 15);
    Color grayWithAlpha1 = new Color(15, 16, 17, 100);
    Color unsupportedGray = new Color(0x000000);
    Color invalidGrayValue1 = new Color(-15, -15, -15);
    Color invalidGrayValue2 = new Color(2500, 2500, 2500);
    Color constantGray1 = Color.LIGHT_GRAY;
    Color constantGray2 = Color.GRAY;
  }

  void takeColor(Color color) {
    // do nothing
  }
}
