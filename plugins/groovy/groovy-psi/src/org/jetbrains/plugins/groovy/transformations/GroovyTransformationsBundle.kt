/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName(name = "GroovyTransformationsBundle")

package org.jetbrains.plugins.groovy.transformations

import com.intellij.CommonBundle
import com.intellij.reference.SoftReference
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.util.*

const val BUNDLE: String = "org.jetbrains.plugins.groovy.transformations.GroovyTransformationsBundle"

fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = CommonBundle.message(getBundle(), key, *params)

private var ourBundle: Reference<ResourceBundle>? = null

private fun getBundle(): ResourceBundle {
  var bundle = SoftReference.dereference(ourBundle)

  if (bundle == null) {
    bundle = ResourceBundle.getBundle(BUNDLE)!!
    ourBundle = SoftReference<ResourceBundle>(bundle)
  }

  return bundle
}