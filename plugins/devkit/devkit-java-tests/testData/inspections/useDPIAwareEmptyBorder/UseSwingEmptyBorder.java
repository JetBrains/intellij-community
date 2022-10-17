import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Insets;

class UseJBColorConstructor {

  private static final EmptyBorder SWING_EMPTY_BORDER = <warning descr="Replace with JBUI.Borders.empty(...)">new EmptyBorder(1, 2, 3, 4)</warning>;
  private static final EmptyBorder SWING_EMPTY_BORDER_INSETS = new EmptyBorder(new Insets(1, 2, 3, 4)); // correct usage

  void any() {
    Border myEmptyBorder1 = <warning descr="Replace with JBUI.Borders.empty(...)">new EmptyBorder(1, 2, 3, 4)</warning>;
    Border myEmptyBorder2 = <warning descr="Replace with JBUI.Borders.empty(...)">new EmptyBorder(0, 0, 0, 0)</warning>;
    takeBorder(<warning descr="Replace with JBUI.Borders.empty(...)">new EmptyBorder(1, 2, 3, 4)</warning>);
    takeBorder(<warning descr="Replace with JBUI.Borders.empty(...)">new EmptyBorder(0, 0, 0, 0)</warning>);

    // correct cases:
    Border myEmptyBorder3 = new EmptyBorder(new Insets(1, 2, 3, 4));
    takeBorder(new EmptyBorder(new Insets(1, 2, 3, 4)));
  }

  void takeBorder(Border border) {
    // do nothing
  }
}
