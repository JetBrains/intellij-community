import com.intellij.ui.JBColor;
import com.intellij.ui.Gray;

import java.awt.Color;

class UseGrayConstantFixInJBColorParam {

  void any() {
    Color myGray = new JBColor(<warning descr="'java.awt.Color' used for gray">new Co<caret>lor(25, 25, 25)</warning>, Gray._125);
  }

}
