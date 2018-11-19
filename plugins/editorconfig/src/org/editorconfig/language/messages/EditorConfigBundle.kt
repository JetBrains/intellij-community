// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.messages

import com.intellij.CommonBundle
import com.intellij.reference.SoftReference
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.util.*

object EditorConfigBundle {
  @NonNls
  const val BUNDLE: String = "messages.EditorConfigBundle"

  private var bundleReference: Reference<ResourceBundle>? = null

  fun get(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    CommonBundle.message(getBundle(), key, *params)

  @Suppress("RemoveRedundantSpreadOperator")
  operator fun get(@PropertyKey(resourceBundle = BUNDLE) key: String) = get(key, *emptyArray())

  @JvmStatic
  fun message(key : String) = EditorConfigBundle[key]

  @JvmStatic
  fun message(key: String, param: String) = EditorConfigBundle.get(key, param)

  private fun getBundle() = SoftReference.dereference(bundleReference) ?: run {
    val bundle = ResourceBundle.getBundle(BUNDLE)
    bundleReference = SoftReference(bundle)
    bundle
  }
}