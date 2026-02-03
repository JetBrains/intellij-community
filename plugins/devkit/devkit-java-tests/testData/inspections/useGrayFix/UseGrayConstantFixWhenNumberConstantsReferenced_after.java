import com.intellij.ui.Gray;

import java.awt.Color;

class UseGrayConstantFixWhenNumberConstantsReferenced {

  private static final int GRAY_VALUE = 125;

  void any() {
    Color gray = Gray._125;
  }

}
