import javafx.scene.paint.Color;
import static javafx.scene.paint.Color.*;

public class Main {
  void foo() {
    Color color1 = new Color  (0,0.251,0.502,1);
    Color color2 = Color.color(0.251,0.502,1,0);
    Color color3 = Color.color(0.502,1,0,0.251);
    Color color4 = color      (1,0,0.251,0.502);
    Color color5 = color      (0,0.251,0.502);

    Color gray1 = Color.color(0.251,0.502,1,0);
    Color gray2 = Color.color(0.502,1,0,0.251);
    Color gray3 = color      (1,0,0.251,0.502);
    Color gray4 = color      (0,0.251,0.502);

    Color rgb1 = Color.rgb(64,128,255,0);
    Color rgb2 = rgb      (128,255,0,0.251);
    Color rgb3 = Color.rgb(255,0,64,0.502);
    Color rgb4 = rgb      (0,64,128);

    Color grayRgb1 = Color.rgb(64,128,255,0);
    Color grayRgb2 = rgb      (128,255,0,0.251);
    Color grayRgb3 = Color.rgb(255,0,64,0.502);
    Color grayRgb4 = rgb      (0,64,128);

    Color hsb1 = Color.hsb(219.8953,0.749,1,0);
    Color hsb2 =       hsb(89.8824,1,1,0.251);
    Color hsb3 = Color.hsb(344.9412,1,1,0.502);
    Color hsb4 =       hsb(210,1,0.502);
    Color hsb5 =       hsb(219.8953,0.749,1,0);
  }
}