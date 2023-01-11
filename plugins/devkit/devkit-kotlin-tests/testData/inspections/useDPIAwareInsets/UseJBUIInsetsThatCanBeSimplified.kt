import com.intellij.util.ui.JBUI
import java.awt.Insets

 class UseJBUIInsetsThatCanBeSimplified {

  companion object {
    private val INSETS_CONSTANT_CAN_BE_SIMPLIFIED: Insets = JBUI.<warning descr="Insets creation can be simplified">insets(0)</warning>
    private val INSETS_CONSTANT_CORRECT1: Insets = JBUI.insets(1, 2, 3, 4) // correct
    private val INSETS_CONSTANT_CORRECT2: Insets = JBUI.insets(1) // correct
    private const val ZERO = 0
    private const val ONE = 1
  }

   @Suppress("UNUSED_VARIABLE")
  fun any() {
    // cases that can be simplified:
    JBUI.<warning descr="Insets creation can be simplified">insets(0)</warning>
    val insets1 = JBUI.<warning descr="Insets creation can be simplified">insets(0)</warning>
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(0)</warning>)
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(0, 0)</warning>)
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(1, 1)</warning>)

    // all the same
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(0, 0, 0, 0)</warning>)
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(1, 1, 1, 1)</warning>)

    // 1st == 3rd and 2nd == 4th
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(1, 2, 1, 2)</warning>)

    // 3 zeros
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(1, 0, 0, 0)</warning>)
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(0, 1, 0, 0)</warning>)
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(0, 0, 1, 0)</warning>)
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(0, 0, 0, 1)</warning>)

    // static import:
    JBUI.<warning descr="Insets creation can be simplified">insets(0)</warning>

    // constant used to check expressions evaluation:
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(ONE, ZERO, 0, ZERO)</warning>)
    takeInsets(JBUI.<warning descr="Insets creation can be simplified">insets(ONE, 2, ONE, 2)</warning>)

    // correct cases:
    JBUI.insets(1)
    JBUI.insets(1, 2)
    JBUI.insets(1, 1, 0, 0)
    JBUI.insets(1, 2, 3, 4)
  }

   @Suppress("UNUSED_PARAMETER")
   private fun takeInsets(insets: Insets) {
    // do nothing
  }
}
