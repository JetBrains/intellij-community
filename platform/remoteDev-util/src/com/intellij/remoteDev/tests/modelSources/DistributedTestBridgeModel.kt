package com.intellij.remoteDev.tests.modelSources

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.string
import com.jetbrains.rd.generator.nova.PredefinedType.void

/**
 * Model to bind client <-> server agents during test session
 */
@Suppress("unused")
object DistributedTestBridgeModel : Ext(TestRoot) {

  init {
    call("syncCall", void, void).async
    signal("sendMessage", string)
      .doc("Send message between peers")
  }
}
