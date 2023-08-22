// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import java.net.URI

interface ClientDesktopPeer {
  fun browse(uri: URI)
}