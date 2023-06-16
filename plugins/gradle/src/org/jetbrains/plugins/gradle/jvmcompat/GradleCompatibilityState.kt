// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

class GradleCompatibilityState() : IdeVersionedDataState() {
  constructor(
    initialVersionMappings: List<VersionMapping>,
    initialSupportedJavaVersions: List<String>,
    initialSupportedGradleVersions: List<String>
  ) : this() {
    versionMappings.addAll(initialVersionMappings)
    supportedJavaVersions.addAll(initialSupportedJavaVersions)
    supportedGradleVersions.addAll(initialSupportedGradleVersions)
  }

  var versionMappings by list<VersionMapping>()
  var supportedJavaVersions by list<String>()
  var supportedGradleVersions by list<String>()
}