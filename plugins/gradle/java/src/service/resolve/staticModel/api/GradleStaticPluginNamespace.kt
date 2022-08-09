// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.staticModel.api

internal interface GradleStaticPluginNamespace {

  fun task(
    name: String,
    description: String? = null,
    configurationParameters: Map</*parameter name*/ String, /* Type FQN */ String> = emptyMap(),
  )

  fun extension(
    name: String,
    typeFqn: String,
    description: String? = null,
  )

  fun configuration(
    name: String,
    description: String? = null,
  )

}