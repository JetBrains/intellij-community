package com.intellij.grazie.cloud.license

import com.intellij.grazie.GrazieBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls

private const val RegisterActionId = "Register"
private const val RegisterPluginsActionId = "RegisterPlugins"

private fun obtainRegisterAction(): AnAction? {
  val manager = ActionManager.getInstance()
  return manager.getAction(RegisterActionId) ?: manager.getAction(RegisterPluginsActionId)
}

internal fun notifyOnLicensingError(error: String) {
  UIUtil.invokeLaterIfNeeded {
    Messages.showErrorDialog(error, GrazieBundle.message("grazie.licensing.error.dialog.title"))
  }
}

internal fun openGzlActivationDialog() {
  UIUtil.invokeLaterIfNeeded({
    val action = obtainRegisterAction()
    checkNotNull(action) { "Failed to obtain register action" }
    val event = AnActionEvent.createFromDataContext(
      "",
      Presentation(),
      createRegisterActionDataContext(
        productCode = GrazieLicenseCode,
        message = GrazieBundle.message("grazie.license.dialog.activate.license.message"),
        requestRevalidate = false,
        fetchOnStart = true
      )
    )
    action.actionPerformed(event)
  })
}

@Suppress("SameParameterValue")
private fun createRegisterActionDataContext(
  productCode: String?,
  message: @Nls String?,
  requestRevalidate: Boolean,
  fetchOnStart: Boolean
): DataContext {
  return DataContext {
    return@DataContext when (it) {
      "register.product-descriptor.code" -> productCode
      "register.message" -> message
      "register.request.revalidate" -> requestRevalidate
      "register.fetch.on.start" -> fetchOnStart
      else -> null
    }
  }
}
