package com.intellij.remoteDev.tests.modelSources

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*

/**
 * Model to bind client <-> server agents during test session
 */
@Suppress("unused")
object DistributedTestBridgeModel : Ext(TestRoot) {

  init {
    call("syncCall", void, void)
  }
}
