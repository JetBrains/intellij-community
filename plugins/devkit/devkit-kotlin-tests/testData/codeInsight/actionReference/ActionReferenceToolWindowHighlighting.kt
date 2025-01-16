import com.intellij.openapi.actionSystem.ex.ActionUtil

class ActionReferenceToolWindowHighlighting {

  fun testToolWindowExtensionHighlighting() {
    ActionUtil.wrap("ActivateToolWindowIdToolWindow")
    ActionUtil.wrap("ActivateToolWindowIdWithSpacesToolWindow")

    ActionUtil.wrap("<error descr="Cannot resolve action id 'ActivateINVALID_VALUEToolWindow'">ActivateINVALID_VALUEToolWindow</error>")

    ActionUtil.getActionGroup("<error descr="Cannot resolve group id 'ActivateToolWindowIdToolWindow'">ActivateToolWindowIdToolWindow</error>")
  }

  fun testToolWindowToolWindowIdHighlighting() {
    ActionUtil.wrap("ActivateToolWindowIdFromConstantsToolWindow")
  }
}