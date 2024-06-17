// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.Property

interface GradleProperties  {

  val javaHomeProperty: Property<String>?

  val gradleLoggingLevel: Property<String>?

  val parallel: Property<Boolean>?
  val isolatedProjects: Property<Boolean>?

  val jvmOptions: Property<String>?

  object EMPTY : GradleProperties {
    override val javaHomeProperty: Nothing? = null
    override val gradleLoggingLevel: Nothing? = null
    override val parallel: Nothing? = null
    override val isolatedProjects: Nothing? = null
    override val jvmOptions: Nothing? = null
  }
}