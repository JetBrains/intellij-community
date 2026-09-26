// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder

@Deprecated(message = "Use com.intellij.openapi.util.RemoveUserDataKt.removeUserData",
            replaceWith = ReplaceWith("removeUserData(key)", "com.intellij.openapi.util.removeUserData"))
fun <T> UserDataHolder.removeUserData(key: Key<T>): T? {
  val data = getUserData(key)
  putUserData(key, null)
  return data
}
