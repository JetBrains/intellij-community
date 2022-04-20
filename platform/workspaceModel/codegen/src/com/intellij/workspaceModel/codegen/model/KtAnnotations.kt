// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.model

import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Open
import kotlin.reflect.KClass

class KtAnnotations {
  val list = mutableListOf<KtAnnotation>()
  val byName by lazy {
    list.associateBy { it.name.text }
  }

  override fun toString(): String = list.joinToString()

  val flags by lazy {
    val result = Flags()
    list.forEach {
      when (it.name.text) {
        Open::class.java.simpleName -> result.open = true
        Abstract::class.java.simpleName -> result.abstract = true
      }
    }
    result
  }

  operator fun get(name: String): List<String>? =
    byName[name]?.args?.map { it.text.removeSurrounding("\"") }

  operator fun get(c: Class<*>): List<String>? = get(c.simpleName)

  data class Flags(
    var content: Boolean = false,
    var open: Boolean = false,
    var abstract: Boolean = false,
    var sealed: Boolean = false,
    var relation: Boolean = false,
  )
}

operator fun KtAnnotations?.contains(kClass: KClass<*>): Boolean {
  return this != null && kClass.simpleName in byName
}