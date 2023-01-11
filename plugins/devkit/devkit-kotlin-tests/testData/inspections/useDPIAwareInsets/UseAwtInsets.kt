import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Insets

class UseAwtInsets {

  companion object {
    private val AWT_INSETS = <warning descr="'Insets' is not DPI-aware">Insets(1, 2, 3, 4)</warning>
    private val AWT_INSETS_IN_JB_UI_INSETS: Insets = JBUI.insets(Insets(1, 2, 3, 4)) // correct usage
    private val AWT_INSETS_IN_JB_INSETS: Insets = JBInsets.create(Insets(1, 2, 3, 4)) // correct usage
  }

  @Suppress("UNUSED_VARIABLE")
  fun any() {
    val myInsets1 = <warning descr="'Insets' is not DPI-aware">Insets(1, 2, 3, 4)</warning>
    val myInsets2 = <warning descr="'Insets' is not DPI-aware">Insets(0, 0, 0, 0)</warning>
    takeInsets(<warning descr="'Insets' is not DPI-aware">Insets(1, 2, 3, 4)</warning>)
    takeInsets(<warning descr="'Insets' is not DPI-aware">Insets(0, 0, 0, 0)</warning>)

    // correct cases:
    val myEmptyInsets3 = JBUI.insets(Insets(1, 2, 3, 4))
    takeInsets(JBUI.insets(Insets(1, 2, 3, 4)))
    val myEmptyInsets4 = JBUI.insets(Insets(1, 2, 3, 4))
    val myEmptyInsets5 = JBInsets.create(Insets(1, 2, 3, 4))
    takeInsets(JBInsets.create(Insets(1, 2, 3, 4)))
    val myEmptyInsets6 = JBInsets.create(Insets(1, 2, 3, 4))
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeInsets(insets: Insets?) {
    // do nothing
  }
}
