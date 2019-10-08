// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

enum class GHRepositoryPermissionLevel {
  //Can read and clone this repository. Can also open and comment on issues and pull requests
  READ,
  //Can read and clone this repository. Can also manage issues and pull requests
  TRIAGE,
  //Can read, clone, and push to this repository. Can also manage issues and pull requests
  WRITE,
  //Can read, clone, and push to this repository. They can also manage issues, pull requests, and some repository settings
  MAINTAIN,
  //Can read, clone, and push to this repository. Can also manage issues, pull requests, and repository settings, including adding collaborators
  ADMIN
}
