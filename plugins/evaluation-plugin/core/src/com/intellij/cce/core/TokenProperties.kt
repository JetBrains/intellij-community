// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

import com.google.gson.*
import com.intellij.cce.actions.TextRange
import java.lang.reflect.Type

interface TokenProperties {
  val tokenType: TypeProperty
  val location: SymbolLocation

  fun additionalProperty(name: String): String?

  fun describe(): String

  fun hasFeature(feature: String): Boolean

  fun withFeatures(features: Set<String>): TokenProperties

  interface Adapter<T> {
    fun adapt(props: TokenProperties): T?
  }

  object JsonAdapter : JsonDeserializer<TokenProperties>, JsonSerializer<TokenProperties> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TokenProperties {
      val simpleTokenProperties = context.deserialize<SimpleTokenProperties>(json, SimpleTokenProperties::class.java)
      if (simpleTokenProperties.tokenType == TypeProperty.LINE) {
        return context.deserialize(json, LineProperties::class.java)
      }
      else if (json.asJsonObject.has("docComment")) {
        return context.deserialize<DocumentationProperties>(json, DocumentationProperties::class.java)

      }
      return simpleTokenProperties
    }

    override fun serialize(src: TokenProperties, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
      return context.serialize(src)
    }
  }

  companion object {
    val UNKNOWN = SimpleTokenProperties.create(TypeProperty.UNKNOWN, SymbolLocation.UNKNOWN) {}
  }
}


class LineProperties(val completableRanges: List<TextRange>) : TokenProperties {
  override val tokenType: TypeProperty = TypeProperty.LINE
  override val location: SymbolLocation = SymbolLocation.UNKNOWN
  override fun additionalProperty(name: String): String? = null
  override fun describe(): String = ""
  override fun hasFeature(feature: String): Boolean = false
  override fun withFeatures(features: Set<String>): TokenProperties = this
}


object PropertyAdapters {
  internal const val LANGUAGE_PROPERTY = "lang"

  object Jvm : Base<JvmProperties>("Jvm") {
    override fun build(props: TokenProperties): JvmProperties = JvmProperties(props)
  }

  abstract class Base<T>(internal val language: String) : TokenProperties.Adapter<T> {
    final override fun adapt(props: TokenProperties): T? {
      return if (props.additionalProperty(LANGUAGE_PROPERTY) != language) null else build(props)
    }

    abstract fun build(props: TokenProperties): T
  }
}

class JvmProperties(private val props: TokenProperties) : TokenProperties by props {
  companion object {
    const val STATIC = "isStatic"
    const val PACKAGE = "packageName"
    const val CONTAINING_CLASS = "containingClass"

    fun create(tokenType: TypeProperty, location: SymbolLocation, init: Builder.() -> Unit): TokenProperties {
      val builder = Builder()
      builder.init()
      return SimpleTokenProperties.create(tokenType, location) {
        builder.isStatic?.let { put(STATIC, it.toString()) }
        builder.packageName?.let { put(PACKAGE, it) }
        builder.declaringClass?.let { put(CONTAINING_CLASS, it) }

        put(PropertyAdapters.LANGUAGE_PROPERTY, PropertyAdapters.Jvm.language)
      }
    }
  }

  val isStatic: Boolean = props.additionalProperty(STATIC) == "true"
  val packageName: String? = props.additionalProperty(PACKAGE)
  val containingClass: String? = props.additionalProperty(CONTAINING_CLASS)

  class Builder {
    var isStatic: Boolean? = null
    var packageName: String? = null
    var declaringClass: String? = null
  }
}

class SimpleTokenProperties private constructor(
  override val tokenType: TypeProperty,
  override val location: SymbolLocation,
  private val features: MutableSet<String>,
  private val additional: Map<String, String>
) : TokenProperties {

  companion object {
    fun create(tokenType: TypeProperty, location: SymbolLocation, init: MutableMap<String, String>.() -> Unit): TokenProperties {
      val props = mutableMapOf<String, String>()
      props.init()
      return SimpleTokenProperties(tokenType, location, mutableSetOf(), props)
    }
  }

  override fun additionalProperty(name: String): String? {
    return additional[name]
  }

  override fun describe(): String {
    return buildString {
      append("tokenType=$tokenType")
      append(", location=$location")
      if (additional.isNotEmpty()) {
        append(additional.entries.sortedBy { it.key }.joinToString(separator = ", ", prefix = " | "))
      }
    }
  }

  override fun hasFeature(feature: String): Boolean = features.contains(feature)

  override fun withFeatures(features: Set<String>): TokenProperties =
    SimpleTokenProperties(tokenType, location, this.features.apply { addAll(features) }, additional)
}

class DocumentationProperties(val docComment: String, val startOffset: Int, val endOffset: Int, val docStartOffset: Int, val docEndOffset: Int, val nameIdentifierOffset: Int) : TokenProperties {
  override val tokenType: TypeProperty = TypeProperty.UNKNOWN
  override val location: SymbolLocation = SymbolLocation.UNKNOWN
  override fun additionalProperty(name: String): String? = null
  override fun describe(): String = ""
  override fun hasFeature(feature: String): Boolean = false
  override fun withFeatures(features: Set<String>): TokenProperties = this
}

enum class SymbolLocation {
  PROJECT, LIBRARY, UNKNOWN
}

enum class TypeProperty {
  KEYWORD,
  VARIABLE,
  LOCAL_VARIABLE,
  LINE,
  PARAMETER,

  // TODO: consider constructors separately
  TYPE_REFERENCE,
  METHOD_CALL,
  FIELD,
  ARGUMENT_NAME,
  PARAMETER_MEMBER,

  TYPE_DECLARATION,
  METHOD,
  METHOD_BODY,
  CLASS,
  FUNCTION,
  FILE,
  TOKEN,
  UNKNOWN,
}

enum class CaretPosition {
  BEGINNING,
  MIDDLE,
  UNKNOWN,
}
