// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization.service

import com.intellij.platform.workspace.storage.impl.ClassToIntConverter
import com.intellij.platform.workspace.storage.tests.metadata.serialization.NEW_VERSION_PACKAGE_NAME
import com.intellij.platform.workspace.storage.tests.metadata.serialization.deserialization
import com.intellij.platform.workspace.storage.tests.metadata.serialization.toCacheVersion
import com.intellij.platform.workspace.storage.tests.metadata.serialization.toCurrentVersion

internal class TestClassToIntConverter(
  private val classToIntConverter: ClassToIntConverter
): ClassToIntConverter by classToIntConverter {

  override fun getInt(clazz: Class<*>): Int {
    return classToIntConverter.getInt(
      if (deserialization && clazz.name.contains(NEW_VERSION_PACKAGE_NAME))
        clazz.toCacheVersion()
      else
        clazz
    )
  }

  override fun getClassOrDie(id: Int): Class<*> {
    val clazz = classToIntConverter.getClassOrDie(id)
    return if (deserialization) clazz.toCurrentVersion() else clazz.toCacheVersion()
  }
}