// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag

class SdkHomeBean : BaseState() {
  @get:OptionTag("SDK_HOME")
  var sdkHome by string()
}
