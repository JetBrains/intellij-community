import com.intellij.util.ui.JBUI;

import java.awt.*;

import static com.intellij.util.ui.JBUI.insets;

class UseJBUIInsetsThatCanBeSimplified {
  private static final Insets INSETS_CONSTANT_CAN_BE_SIMPLIFIED = <warning descr="Insets creation can be simplified">JBUI.insets(0)</warning>;
  private static final Insets INSETS_CONSTANT_CORRECT1 = JBUI.insets(1, 2, 3, 4); // correct
  private static final Insets INSETS_CONSTANT_CORRECT2 = JBUI.insets(1); // correct

  private static final int ZERO = 0;
  private static final int ONE = 1;

  void any() {
    // cases that can be simplified:
    <warning descr="Insets creation can be simplified">JBUI.insets(0)</warning>;
    Insets insets1 = <warning descr="Insets creation can be simplified">JBUI.insets(0)</warning>;
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(0)</warning>);

    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(0, 0)</warning>);
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(1, 1)</warning>);

    // all the same
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(0, 0, 0, 0)</warning>);
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(1, 1, 1, 1)</warning>);

    // 1st == 3rd and 2nd == 4th
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(1, 2, 1, 2)</warning>);

    // 3 zeros
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(1, 0, 0, 0)</warning>);
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(0, 1, 0, 0)</warning>);
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(0, 0, 1, 0)</warning>);
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(0, 0, 0, 1)</warning>);

    // static import:
    <warning descr="Insets creation can be simplified">insets(0)</warning>;

    // constant used to check expressions evaluation:
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(ONE, ZERO, 0, ZERO)</warning>);
    takeInsets(<warning descr="Insets creation can be simplified">JBUI.insets(ONE, 2, ONE, 2)</warning>);

    // correct cases:
    JBUI.insets(1);
    JBUI.insets(1, 2);
    JBUI.insets(1, 1, 0, 0);
    JBUI.insets(1, 2, 3, 4);
  }

  void takeInsets(Insets insets) {
    // do nothing
  }
}
