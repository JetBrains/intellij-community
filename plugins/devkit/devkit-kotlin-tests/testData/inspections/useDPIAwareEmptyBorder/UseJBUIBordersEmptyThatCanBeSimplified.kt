import com.intellij.util.ui.JBUI
import javax.swing.border.Border

internal class UseJBUIBordersEmptyThatCanBeSimplified {

  companion object {
    private val EMPTY_CONSTANT_CAN_BE_SIMPLIFIED = JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0)</warning>
    private val EMPTY_CONSTANT_CORRECT1: Border = JBUI.Borders.empty() // correct
    private val EMPTY_CONSTANT_CORRECT2 = JBUI.Borders.empty(1) // correct
    private const val ZERO = 0
    private const val ONE = 1
  }

  @Suppress("UNUSED_VARIABLE")
  fun any() {
    // cases that can be simplified:
    JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0)</warning>
    val border1 = JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0)</warning>
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0)</warning>)
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0, 0)</warning>)
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(1, 1)</warning>)

    // all the same
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0, 0, 0, 0)</warning>)
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(1, 1, 1, 1)</warning>)

    // 1st == 3rd and 2nd == 4th
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(1, 2, 1, 2)</warning>)

    // 3 zeros
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(1, 0, 0, 0)</warning>)
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0, 1, 0, 0)</warning>)
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0, 0, 1, 0)</warning>)
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0, 0, 0, 1)</warning>)

    // more specific imports:
    JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0)</warning>
    JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0)</warning>
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(0, 0, 0, 0)</warning>)

    // constant used to check expressions evaluation:
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(ONE, ZERO, 0, ZERO)</warning>)
    takeBorder(JBUI.Borders.<warning descr="Empty border creation can be simplified">empty(ONE, 2, ONE, 2)</warning>)


    // correct cases:
    JBUI.Borders.empty(1)
    JBUI.Borders.empty(1, 2)
    JBUI.Borders.empty(1, 1, 0, 0)
    JBUI.Borders.empty(1, 2, 3, 4)
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeBorder(border: Border?) {
    // do nothing
  }
}