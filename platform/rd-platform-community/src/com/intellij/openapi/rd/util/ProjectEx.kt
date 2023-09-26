package com.intellij.openapi.rd.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.rd.util.lifetime.Lifetime

val <T> T.lifetime: Lifetime where T : UserDataHolder, T : Disposable by userData(Key("com.jetbrains.rd.platform.util.lifetime")) {
  it.createLifetime()
}