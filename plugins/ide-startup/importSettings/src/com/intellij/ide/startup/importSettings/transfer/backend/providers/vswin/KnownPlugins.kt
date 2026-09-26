// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin

import com.intellij.ide.startup.importSettings.TransferableIdeFeatureId
import com.intellij.ide.startup.importSettings.models.BuiltInFeature

object KnownPlugins {
  val ReSharper = BuiltInFeature(TransferableIdeFeatureId.ReSharper, "ReSharper", isHidden = true)
}