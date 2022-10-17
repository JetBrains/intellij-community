// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.base.BaseProperties

interface LocalProperties : BaseProperties {

  object EMPTY : LocalProperties {
    override val javaHomeProperty: Nothing? = null
  }
}