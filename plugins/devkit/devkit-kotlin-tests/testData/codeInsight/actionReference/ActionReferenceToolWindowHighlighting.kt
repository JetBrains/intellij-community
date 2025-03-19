import com.intellij.openapi.actionSystem.ex.ActionUtil

class ActionReferenceToolWindowHighlighting {

  fun testToolWindowExtensionHighlighting() {
    ActionUtil.wrap("ActivateToolWindowIdToolWindow")
    ActionUtil.wrap("ActivateToolWindowIdWithSpacesToolWindow")

    ActionUtil.wrap("<error descr="Cannot resolve action or group 'ActivateINVALID_VALUEToolWindow'">ActivateINVALID_VALUEToolWindow</error>")

    ActionUtil.getActionGroup("<error descr="Cannot resolve group 'ActivateToolWindowIdToolWindow'">ActivateToolWindowIdToolWindow</error>")
  }

  fun testToolWindowToolWindowIdHighlighting() {
    ActionUtil.wrap("ActivateToolWindowIdFromConstantsToolWindow")
  }
}