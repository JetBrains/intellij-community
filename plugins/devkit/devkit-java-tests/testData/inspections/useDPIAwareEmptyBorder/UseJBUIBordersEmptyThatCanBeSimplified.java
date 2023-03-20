import com.intellij.util.ui.JBUI;

import javax.swing.border.Border;

import static com.intellij.util.ui.JBUI.Borders;
import static com.intellij.util.ui.JBUI.Borders.empty;

class UseJBUIBordersEmptyThatCanBeSimplified {
  private static final Border EMPTY_CONSTANT_CAN_BE_SIMPLIFIED = <warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0)</warning>;
  private static final Border EMPTY_CONSTANT_CORRECT1 = JBUI.Borders.empty(); // correct
  private static final Border EMPTY_CONSTANT_CORRECT2 = JBUI.Borders.empty(1); // correct

  private static final int ZERO = 0;
  private static final int ONE = 1;

  void any() {
    // cases that can be simplified:
    <warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0)</warning>;
    Border border1 = <warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0)</warning>;
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0)</warning>);

    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0, 0)</warning>);
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(1, 1)</warning>);

    // all the same
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0, 0, 0, 0)</warning>);
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(1, 1, 1, 1)</warning>);

    // 1st == 3rd and 2nd == 4th
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(1, 2, 1, 2)</warning>);

    // 3 zeros
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(1, 0, 0, 0)</warning>);
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0, 1, 0, 0)</warning>);
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0, 0, 1, 0)</warning>);
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(0, 0, 0, 1)</warning>);

    // more specific imports:
    <warning descr="Empty border creation can be simplified">Borders.empty(0)</warning>;
    <warning descr="Empty border creation can be simplified">empty(0)</warning>;
    takeBorder(<warning descr="Empty border creation can be simplified">empty(0, 0, 0, 0)</warning>);

    // constant used to check expressions evaluation:
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(ONE, ZERO, 0, ZERO)</warning>);
    takeBorder(<warning descr="Empty border creation can be simplified">JBUI.Borders.empty(ONE, 2, ONE, 2)</warning>);


    // correct cases:
    JBUI.Borders.empty(1);
    JBUI.Borders.empty(1, 2);
    JBUI.Borders.empty(1, 1, 0, 0);
    JBUI.Borders.empty(1, 2, 3, 4);
  }

  void takeBorder(Border border) {
    // do nothing
  }
}
