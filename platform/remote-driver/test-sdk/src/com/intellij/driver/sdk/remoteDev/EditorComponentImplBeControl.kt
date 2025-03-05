package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.ui.components.common.EditorComponentImpl
import com.intellij.driver.sdk.ui.remote.Component


class EditorComponentImplBeControlBuilder : BeControlBuilder {
  override fun build(driver: Driver, frontendComponent: Component, backendComponent: Component): Component {
    return EditorComponentImplBeControl(driver, frontendComponent, backendComponent)
  }
}

class EditorComponentImplBeControl(
  driver: Driver,
  frontendComponent: Component,
  backendComponent: Component,
) : BeControlComponentBase(driver, frontendComponent, backendComponent), EditorComponentImpl {
  private val frontendEditorComponentImpl: EditorComponentImpl by lazy {
    driver.cast(onFrontend { byClass("EditorComponentImpl") }.component, EditorComponentImpl::class)
  }

  override fun getEditor(): Editor {
    return frontendEditorComponentImpl.getEditor()
  }

  override fun isEditable() = frontendEditorComponentImpl.isEditable()
}