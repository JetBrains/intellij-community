import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import java.awt.*;

import static com.intellij.util.ui.JBInsets.create;
import static com.intellij.util.ui.JBUI.insets;

class UseAwtInsets {

  private static final Insets AWT_INSETS = <warning descr="'Insets' is not DPI-aware">new Insets(1, 2, 3, 4)</warning>;
  private static final Insets AWT_INSETS_IN_JB_UI_INSETS = JBUI.insets(new Insets(1, 2, 3, 4)); // correct usage
  private static final Insets AWT_INSETS_IN_JB_INSETS = JBInsets.create(new Insets(1, 2, 3, 4)); // correct usage

  void any() {
    Insets myInsets1 = <warning descr="'Insets' is not DPI-aware">new Insets(1, 2, 3, 4)</warning>;
    Insets myInsets2 = <warning descr="'Insets' is not DPI-aware">new Insets(0, 0, 0, 0)</warning>;
    takeInsets(<warning descr="'Insets' is not DPI-aware">new Insets(1, 2, 3, 4)</warning>);
    takeInsets(<warning descr="'Insets' is not DPI-aware">new Insets(0, 0, 0, 0)</warning>);

    // correct cases:
    Insets myEmptyInsets3 = JBUI.insets(new Insets(1, 2, 3, 4));
    takeInsets(JBUI.insets(new Insets(1, 2, 3, 4)));
    Insets myEmptyInsets4 = insets(new Insets(1, 2, 3, 4));

    Insets myEmptyInsets5 = JBInsets.create(new Insets(1, 2, 3, 4));
    takeInsets(JBInsets.create(new Insets(1, 2, 3, 4)));
    Insets myEmptyInsets6 = create(new Insets(1, 2, 3, 4));
  }

  void takeInsets(Insets insets) {
    // do nothing
  }
}
