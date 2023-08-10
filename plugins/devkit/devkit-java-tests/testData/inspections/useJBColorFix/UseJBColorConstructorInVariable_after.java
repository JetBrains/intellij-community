import com.intellij.ui.JBColor;

import java.awt.Color;

class UseJBColorConstructorInVariable {
  void any() {
    Color myColor = new JBColor(new Color(4, 5, 6), new Color());
  }
}
