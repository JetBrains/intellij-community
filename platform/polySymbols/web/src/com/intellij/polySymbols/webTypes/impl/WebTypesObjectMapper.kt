// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.polySymbols.webTypes.json.WebTypes
import com.intellij.util.containers.Interner
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.deser.jdk.StringDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.type.TypeFactory

internal val objectMapper: ObjectMapper = JsonMapper.builder()
  .typeFactory(TypeFactory.createDefaultInstance().withClassLoader(WebTypes::class.java.classLoader))
  .addModule(SimpleModule().also { module ->
    val interner = Interner.createStringInterner()
    module.addDeserializer(String::class.java, object : StringDeserializer() {
      override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String? {
        return super.deserialize(p, ctxt)?.let { synchronized(interner) { interner.intern(it) } }
      }
    })
  })
  .build()
