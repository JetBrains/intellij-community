// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.impl.intellij.debug.InspectableSwingIcon
import com.intellij.platform.icons.scale.IconScale

/**
 * A Swing icon of [descriptor], as the Inspector sees it. It does not need the active icon manager: `testIcons` installs a
 * manager that cannot create Swing icons.
 */
internal fun swingIconOf(descriptor: IconDescriptor, scale: IconScale = IconScale.Default): InspectableSwingIcon =
  IntelliJIconManager().createSwingIcon(descriptor, scale) as InspectableSwingIcon
