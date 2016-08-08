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
package com.intellij.configurationStore

import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeProcessor
import org.jdom.Element
import java.io.OutputStream
import java.security.MessageDigest
import java.util.function.Function

interface SchemeDataHolder<in MUTABLE_SCHEME : Scheme> {
  /**
   * You should call updateDigest() after read on init.
   */
  fun read(): Element

  fun updateDigest(scheme: MUTABLE_SCHEME)
}

interface SerializableScheme {
  fun writeScheme(): Element
}

/**
 * A scheme processor can implement this interface to provide a file extension different from default .xml.
 * @see SchemeProcessor
 */
interface SchemeExtensionProvider {
  /**
   * @return The scheme file extension **with e leading dot**, for example ".ext".
   */
  val schemeExtension: String

  /**
   * @return True if the upgrade from the old default .xml extension is needed.
   */
  val isUpgradeNeeded: Boolean
}

abstract class LazySchemeProcessor<SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  open fun getName(attributeProvider: Function<String, String?>): String {
    return attributeProvider.apply("name") ?: throw IllegalStateException("name is missed in the scheme data")
  }

  abstract fun createScheme(dataHolder: SchemeDataHolder<MUTABLE_SCHEME>, name: String, attributeProvider: Function<String, String?>): MUTABLE_SCHEME

  override final fun writeScheme(scheme: MUTABLE_SCHEME) = (scheme as SerializableScheme).writeScheme()

  open fun isSchemeFile(name: CharSequence) = true

  open fun isSchemeDefault(scheme: MUTABLE_SCHEME, digest: ByteArray) = false
}

class DigestOutputStream(val digest: MessageDigest) : OutputStream() {
  override fun write(b: Int) {
    digest.update(b.toByte())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    digest.update(b, off, len)
  }

  override fun toString(): String {
    return "[Digest Output Stream] " + digest.toString()
  }
}

fun Element.digest(): ByteArray {
  // sha-1 is enough, sha-256 is slower, see https://www.nayuki.io/page/native-hash-functions-for-java
  val digest = MessageDigest.getInstance("SHA-1")
  serializeElementToBinary(this, DigestOutputStream(digest))
  return digest.digest()
}