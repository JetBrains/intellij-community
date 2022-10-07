import com.intellij.ui.JBColor;

import java.awt.Color;

class UseJBColorConstructorInMethodParam {
  void any() {
    takeColor(new JBColor(new Color(7, 8, 9), new Color()));
  }

  void takeColor(Color color) {
    // do nothing
  }
}
