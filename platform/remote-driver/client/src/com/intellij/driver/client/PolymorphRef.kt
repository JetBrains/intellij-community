package com.intellij.driver.client

import com.intellij.driver.model.RdTarget

interface PolymorphRefRegistry {
  fun convert(ref: PolymorphRef, target: RdTarget): PolymorphRef
}

interface PolymorphRef