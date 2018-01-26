// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.CommonBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.*

@NonNls
private const val LANG_BUNDLE = "messages.langBundle"

private var ourBundle: Reference<ResourceBundle>? = null

private fun getBundle() = ourBundle?.get() ?: ResourceBundle.getBundle(LANG_BUNDLE).also(::storeBundle)

private fun storeBundle(bundle: ResourceBundle) {
  ourBundle = SoftReference(bundle)
}

@Nls
fun message(@PropertyKey(resourceBundle = LANG_BUNDLE) key: String, vararg params: Any): String {
  return CommonBundle.message(getBundle(), key, *params)
}
