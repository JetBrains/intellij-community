// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("AntUtil")

package com.intellij.lang.ant.config.impl

import com.intellij.openapi.externalSystem.model.ProjectSystemId

val SYSTEM_ID = ProjectSystemId("ANT")

val KNOWN_ANT_FILES = setOf("build.xml", "jbuild.xml")
