// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file: ApiStatus.Internal
@file: JvmName("SyntaxElementUtil")

package com.intellij.platform.syntax.psi.util

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Modifier

private val LOG = fileLogger()

/**
 * @param tokenDeclarationContainer a class or interface containing public static final fields of type [SyntaxElementType]
 * @param skipFields a list of field names to skip
 *
 * @return a list of [SyntaxElementType]s declared in the given class which are public static final and which names are not in [skipFields]
 */
fun getTokensInClass(tokenDeclarationContainer: Class<*>, vararg skipFields: String): List<SyntaxElementType> {
  val skipSet = skipFields.toSet()
  return tokenDeclarationContainer.fields.mapNotNull { f ->
    if (f.name in skipSet) {
      return@mapNotNull null
    }

    val modifiers = f.modifiers
    if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers))) {
      return@mapNotNull null
    }

    val value = try {
      f.get(null)
    }
    catch (_: IllegalAccessException) {
      null
    }

    value as? SyntaxElementType ?: run {
      LOG.error("Field ${f.name} of ${tokenDeclarationContainer.name} is not of type SyntaxElementType")
      null
    }
  }
}