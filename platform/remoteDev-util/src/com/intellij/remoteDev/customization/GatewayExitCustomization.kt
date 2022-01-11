package com.intellij.remoteDev.customization

import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.util.Alarm
import com.intellij.util.application

interface GatewayExitCustomization {
  /**
   * For 'Do not ask again' checkbox. The value must be the same for the whole session.
   */
  val rememberId: String
  val isEnabled: Boolean
  val title: String
  val body: String

  val primaryActionButtonText: String

  /**
   * Return null here if you don't need secondary action.
   */
  val secondaryActionButtonText: String? get() = null

  /**
   * If primaryAction() or secondaryAction() returns true, the client will quit.
   * Don't do any heavy tasks here, return something as soon as possible.
   */
  fun primaryAction(): Boolean
  fun secondaryAction(): Boolean = true
}

open class DefaultGatewayExitCustomizationImpl : GatewayExitCustomization {
  override val rememberId = "DefaultGatewayExitMsg"
  override val isEnabled = System.getProperty("gtw.disable.exit.dialog")?.toBoolean() != true

  override val title = RemoteDevUtilBundle.message("stop.the.ide.backend.or.keep.it.running")
  override val body = RemoteDevUtilBundle.message("you.are.about.to.close.the.jetbrains.client.do.you.also.want.to.stop.the.ide.backend.or.keep.it.running.on.the.host")

  override val primaryActionButtonText = RemoteDevUtilBundle.message("close.and.stop")
  override val secondaryActionButtonText = RemoteDevUtilBundle.message("close.and.keep.running")

  override fun primaryAction(): Boolean {
    Alarm().addRequest({ application.exit() }, 3000)
    return true
  }

  override fun secondaryAction(): Boolean {
    return true
  }
}