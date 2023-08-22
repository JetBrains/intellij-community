package com.intellij.driver.client.impl

import com.intellij.driver.model.transport.Ref

interface RefWrapper {
  fun getRef(): Ref
  fun getRefPluginId(): String?
}