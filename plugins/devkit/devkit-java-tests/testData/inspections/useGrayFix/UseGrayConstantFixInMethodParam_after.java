import com.intellij.ui.Gray;

import java.awt.Color;

class UseGrayConstantFixInMethodParam {

  void any() {
    takeColor(Gray._125);
  }

  void takeColor(Color color) {
    // do nothing
  }

}
