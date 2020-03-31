// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt

interface GHPRTimelineLoaderHolder {
  @get:CalledInAwt
  val timelineLoader: GHPRTimelineLoader?

  @CalledInAwt
  fun acquireTimelineLoader(disposable: Disposable): GHPRTimelineLoader
}