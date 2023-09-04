// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava.git

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

val key = Key.create<String>("PATH_BEFORE_GROOVY_TO_JAVA_CONVERSION")

var VirtualFile.pathBeforeGroovyToJavaConversion: String?
  get() = getUserData(key)
  set(value) = putUserData(key, value)
