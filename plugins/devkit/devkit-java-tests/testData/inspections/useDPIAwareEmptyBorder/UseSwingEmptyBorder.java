import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Insets;

class UseSwingEmptyBorder {

  private static final EmptyBorder SWING_EMPTY_BORDER = <warning descr="'EmptyBorder' is not DPI-aware">new EmptyBorder(1, 2, 3, 4)</warning>;
  private static final EmptyBorder SWING_EMPTY_BORDER_INSETS = new EmptyBorder(new Insets(1, 2, 3, 4)); // correct usage

  void any() {
    Border myEmptyBorder1 = <warning descr="'EmptyBorder' is not DPI-aware">new EmptyBorder(1, 2, 3, 4)</warning>;
    Border myEmptyBorder2 = <warning descr="'EmptyBorder' is not DPI-aware">new EmptyBorder(0, 0, 0, 0)</warning>;
    takeBorder(<warning descr="'EmptyBorder' is not DPI-aware">new EmptyBorder(1, 2, 3, 4)</warning>);
    takeBorder(<warning descr="'EmptyBorder' is not DPI-aware">new EmptyBorder(0, 0, 0, 0)</warning>);

    // correct cases:
    Border myEmptyBorder3 = new EmptyBorder(new Insets(1, 2, 3, 4));
    takeBorder(new EmptyBorder(new Insets(1, 2, 3, 4)));
  }

  void takeBorder(Border border) {
    // do nothing
  }
}
