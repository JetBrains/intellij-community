import javafx.scene.paint.Color;
import static javafx.scene.paint.Color.*;

public class Main {
  void foo() {
    Color rgb1 = Color.<caret>rgb(  0, 255,    (150)      );
    Color rgb2 = <caret>rgb      (150,   0,      255, 0.12);
    Color rgb3 = Color.<caret>rgb(0xF, 100,        0,   1d);
    Color rgb4 = <caret>rgb      (255, 100, (int)'a',   0f);

    Color grayRgb1 = Color.<caret>grayRgb(147         );
    Color grayRgb2 = <caret>grayRgb      (  0, 0.3+0.5);
    Color grayRgb3 = Color.<caret>grayRgb(255,       0);
    Color grayRgb4 = <caret>grayRgb      (0xF,       1);
  }
}