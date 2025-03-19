package com.intellij.driver.client.impl

import com.intellij.driver.model.transport.Ref

interface RefWrapper {
  companion object {
    fun wrapRef(ref: Ref): RefWrapper {
      return object : RefWrapper {
        override fun getRef() = ref
        override fun getRefPluginId() = ""
      }
    }
  }

  fun getRef(): Ref
  fun getRefPluginId(): String?
}