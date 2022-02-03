// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import org.jetbrains.annotations.ApiStatus

@Deprecated("Use CollapsibleRow instead")
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
interface CollapsiblePanel : Panel {

  var expanded: Boolean

}
