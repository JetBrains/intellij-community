// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Experimental
interface HostedRepositoryCoordinates {
  val serverPath: ServerPath

  fun getWebURI(): URI
}
