import javafx.scene.paint.Color;
import static javafx.scene.paint.Color.*;

public class Main {
  void foo() {
    Color rgb1 = <caret>Color.rgb(500, 255,    (150)      );
    Color rgb2 = <caret>rgb      (150,  -1,      255, 0.12);
    Color rgb3 = <caret>Color.rgb(0xF, 100,      256,   1d);
    Color rgb4 = <caret>rgb      (255, 100, (int)'a', -.1f);

    Color grayRgb1 = <caret>Color.grayRgb(-147        );
    Color grayRgb2 = <caret>grayRgb      (  0, 0.3-0.5);
    Color grayRgb3 = <caret>Color.grayRgb(256,       0);
    Color grayRgb4 = <caret>grayRgb      (0xF,     1.1);
  }
}