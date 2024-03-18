import com.intellij.openapi.actionSystem.*

class UnspecifiedActionPlaces {

  companion object {
    private const val CUSTOM_UNKNOWN = "unknown"
    private const val CUSTOM_UNKNOWN_FROM_PLATFORM_VALUE = ActionPlaces.UNKNOWN
  }

  fun any() {
    val actionManager = ActionManager.getInstance()
    // popup menu:
    actionManager.createActionPopupMenu("asdasd", ActionGroup())
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">""</warning>, ActionGroup())
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">"unknown"</warning>, ActionGroup())
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">ActionPlaces.UNKNOWN</warning>, ActionGroup())
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">CUSTOM_UNKNOWN</warning>, ActionGroup())
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">CUSTOM_UNKNOWN_FROM_PLATFORM_VALUE</warning>, ActionGroup())
    // toolbar:
    actionManager.createActionToolbar("asdasd", ActionGroup(), false)
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">""</warning>, ActionGroup(), false)
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">"unknown"</warning>, ActionGroup(), false)
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">ActionPlaces.UNKNOWN</warning>, ActionGroup(), false)
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">CUSTOM_UNKNOWN</warning>, ActionGroup(), false)
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">CUSTOM_UNKNOWN_FROM_PLATFORM_VALUE</warning>, ActionGroup(), false)
  }
}

// test in top-level function:
fun topLevelFunction() {
  val actionManager = ActionManager.getInstance()
  // popup menu:
  actionManager.createActionPopupMenu("asdasd", ActionGroup())
  actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">""</warning>, ActionGroup())
  actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">"unknown"</warning>, ActionGroup())
  actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">ActionPlaces.UNKNOWN</warning>, ActionGroup())
  // toolbar:
  actionManager.createActionToolbar("asdasd", ActionGroup(), false)
  actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">""</warning>, ActionGroup(), false)
  actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">"unknown"</warning>, ActionGroup(), false)
  actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">ActionPlaces.UNKNOWN</warning>, ActionGroup(), false)
}
