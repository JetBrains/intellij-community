import java.awt.Color;

class UseGrayConstantFixInMethodParam {

  void any() {
    takeColor(<warning descr="'java.awt.Color' used for gray">new Col<caret>or(125, 125, 125)</warning>);
  }

  void takeColor(Color color) {
    // do nothing
  }

}
