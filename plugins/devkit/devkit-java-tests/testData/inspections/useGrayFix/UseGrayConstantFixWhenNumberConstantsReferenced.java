import java.awt.Color;

class UseGrayConstantFixWhenNumberConstantsReferenced {

  private static final int GRAY_VALUE = 125;

  void any() {
    Color gray = <warning descr="'java.awt.Color' used for gray">new Col<caret>or(GRAY_VALUE, 125, GRAY_VALUE)</warning>;
  }

}
