import javafx.scene.paint.Color;
import static javafx.scene.paint.Color.*;

public class Main {
  void foo() {
    Color color1 = <caret>new Color  (  0,   1,  0.5, 0.65);
    Color color2 = <caret>Color.color( 1L,   0,    1      );
    Color color3 = <caret>color      (0.8, .3d,    0,    1);
    Color color4 = <caret>color      (  1, 0.6, 0.5f,    0);

    Color gray1 = <caret>Color.gray( 0.5     );
    Color gray2 = <caret>Color.gray(  0L, 0.8);
    Color gray3 = <caret>gray      (  1d,   0);
    Color gray4 = <caret>gray      (0.7f,   1);
  }
}