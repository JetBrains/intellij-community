package com.intellij.remoteDev.tests.modelSources

import com.jetbrains.rd.generator.nova.Ext
import com.jetbrains.rd.generator.nova.PredefinedType.void
import com.jetbrains.rd.generator.nova.async
import com.jetbrains.rd.generator.nova.call

/**
 * Model to bind client <-> server agents during test session
 */
@Suppress("unused")
object DistributedTestBridgeModel : Ext(TestRoot) {

  init {
    call("syncCall", void, void).async
  }
}
