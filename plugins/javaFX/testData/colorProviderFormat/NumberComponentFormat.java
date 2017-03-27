import javafx.scene.paint.Color;
import static javafx.scene.paint.Color.*;

public class Main {
  void foo() {
    Color color1 = <caret>new Color  (1, 1, 1, 1);
    Color color2 = <caret>Color.color(1, 1, 1, 1);
    Color color3 = <caret>Color.color(1, 1, 1, 1);
    Color color4 = <caret>color      (1, 1, 1, 1);
    Color color5 = <caret>color      (1, 1, 1);

    Color gray1 = <caret>Color.gray(1, 1);
    Color gray2 = <caret>Color.gray(1, 1);
    Color gray3 = <caret>gray      (1, 1);
    Color gray4 = <caret>gray      (1);

    Color rgb1 = <caret>Color.rgb(255, 255, 255, 1);
    Color rgb2 = <caret>rgb      (255, 255, 255, 1);
    Color rgb3 = <caret>Color.rgb(255, 255, 255, 1);
    Color rgb4 = <caret>rgb      (255, 255, 255);

    Color grayRgb1 = <caret>Color.grayRgb(255, 1);
    Color grayRgb2 = <caret>grayRgb      (255, 1);
    Color grayRgb3 = <caret>Color.grayRgb(255, 1);
    Color grayRgb4 = <caret>grayRgb      (255);

    Color hsb1 = Color.<caret>hsb(360, 1, 1, 1);
    Color hsb2 =       <caret>hsb(360, 1, 1, 1);
    Color hsb3 = Color.<caret>hsb(360, 1, 1, 1);
    Color hsb4 =       <caret>hsb(360, 1, 1, 1);
    Color hsb5 =       <caret>hsb(360, 1, 1);
  }
}