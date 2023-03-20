import com.intellij.ui.JBColor;
import com.intellij.ui.Gray;

import java.awt.Color;

class UseGrayConstantFixInJBColorParam {

  void any() {
    Color myGray = new JBColor(Gray._25, Gray._125);
  }

}
