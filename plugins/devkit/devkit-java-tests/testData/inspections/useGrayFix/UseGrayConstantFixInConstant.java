import java.awt.Color;

class UseGrayConstantFixInConstant {

  private static final Color GRAY_CONSTANT = <warning descr="'java.awt.Color' used for gray">new Co<caret>lor(125, 125, 125)</warning>;

}
