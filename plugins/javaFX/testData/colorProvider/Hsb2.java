import javafx.scene.paint.Color;
import static javafx.scene.paint.Color.*;

public class Main {
  void foo() {
    Color hsb1 = Color.<caret>hsb(null,   1, 0.8);
    Color hsb2 =       <caret>hsb( 0xF,  15,   1, .1/2);
    Color hsb3 = Color.<caret>hsb(-9.9, 0.6,-0.1,    1);
    Color hsb4 =       <caret>hsb(   1, .2f,  .6,    2);
    Color hsb5 =       <caret>hsb(50*5,  0d, 0.3, -.95);
  }
}