import java.awt.Color;

class UseGrayConstantFixInLocalVariable {

  void any() {
    Color myGray = <warning descr="'java.awt.Color' used for gray">new Co<caret>lor(25, 25, 25)</warning>;
  }

}
