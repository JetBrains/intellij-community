import java.awt.Insets
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

internal class UseSwingEmptyBorder {

  companion object {
    private val SWING_EMPTY_BORDER = <warning descr="'EmptyBorder' is not DPI-aware">EmptyBorder(1, 2, 3, 4)</warning>
    private val SWING_EMPTY_BORDER_INSETS = EmptyBorder(Insets(1, 2, 3, 4)) // correct usage
  }

  @Suppress("UNUSED_VARIABLE")
  fun any() {
    val myEmptyBorder1: Border = <warning descr="'EmptyBorder' is not DPI-aware">EmptyBorder(1, 2, 3, 4)</warning>
    val myEmptyBorder2: Border = <warning descr="'EmptyBorder' is not DPI-aware">EmptyBorder(0, 0, 0, 0)</warning>
    takeBorder(<warning descr="'EmptyBorder' is not DPI-aware">EmptyBorder(1, 2, 3, 4)</warning>)
    takeBorder(<warning descr="'EmptyBorder' is not DPI-aware">EmptyBorder(0, 0, 0, 0)</warning>)

    // correct cases:
    val myEmptyBorder3: Border = EmptyBorder(Insets(1, 2, 3, 4))
    takeBorder(EmptyBorder(Insets(1, 2, 3, 4)))
  }

  @Suppress("UNUSED_PARAMETER")
  fun takeBorder(border: Border?) {
    // do nothing
  }
}