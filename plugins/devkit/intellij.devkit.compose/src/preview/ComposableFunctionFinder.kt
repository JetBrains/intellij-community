// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import androidx.compose.runtime.Composer
import com.intellij.openapi.diagnostic.Logger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private val logger = Logger.getInstance(ComposableFunctionFinder::class.java)

internal val PREVIEW_ANNOTATIONS: Set<String> = setOf(
  "org.jetbrains.compose.ui.tooling.preview.Preview",
  "androidx.compose.ui.tooling.preview.Preview", // Android fallback
)

// Composable function signatures typically have these parameter patterns:
// 1. (Composer, int) - Standard compose compiler generated parameters
// 2. () - No parameters for simple previews
// 3. Custom parameters + (Composer, int) - Preview with custom parameters

/**
 * Discovers and validates Composable Preview functions using reflection on bytecode
 */
internal class ComposableFunctionFinder(private val classLoader: ClassLoader) {
  /**
   * Main entry point - finds all valid preview functions in the given classes
   */
  fun findPreviewFunctions(clazzFqn: String, composableMethodNames: Collection<String>): List<ComposablePreviewFunction> {
    val previewFunctions = mutableListOf<ComposablePreviewFunction>()

    val contextClassLoader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = classLoader

    try {
      val clazz = classLoader.loadClass(clazzFqn)
      val functions = findPreviewFunctionsInClass(clazz, composableMethodNames)
      previewFunctions.addAll(functions)
    }
    catch (e: ClassNotFoundException) {
      logger.warn("Class not found: $clazzFqn", e)
    }
    catch (e: Exception) {
      logger.error("Error processing class: $clazzFqn", e)
    }
    finally {
      Thread.currentThread().contextClassLoader = contextClassLoader
    }

    return previewFunctions
  }

  /**
   * Find all preview functions in a specific class
   */
  private fun findPreviewFunctionsInClass(clazz: Class<*>, composableMethodNames: Collection<String>): List<ComposablePreviewFunction> {
    val previewFunctions = mutableListOf<ComposablePreviewFunction>()

    // Get all declared methods (including private ones)
    val methods = clazz.declaredMethods

    for (method in methods) {
      try {
        if (composableMethodNames.contains(method.name) && isValidPreviewFunction(method)) {
          val previewFunction = createPreviewFunction(method, clazz)
          previewFunctions.add(previewFunction)
          logger.debug("Found preview function: ${clazz.name}.${method.name}")
        }
      }
      catch (e: Exception) {
        logger.warn("Error processing method: ${clazz.name}.${method.name}", e)
      }
    }

    if (previewFunctions.isEmpty()) {
      logger.warn("Cannot find valid preview function in: $clazz")
    }

    return previewFunctions
  }

  /**
   * Validates if a method is a valid Composable Preview function
   */
  private fun isValidPreviewFunction(method: Method): Boolean {
    // Validate function signature
    return hasValidComposableSignature(method)
  }

  /**
   * Validates Composable function signature patterns
   */
  private fun hasValidComposableSignature(method: Method): Boolean {
    val parameterTypes = method.parameterTypes
    val parameterCount = parameterTypes.size

    return when {
      // Pattern 1: No parameters (simple preview)
      parameterCount == 0 -> true

      // Pattern 2: Standard Compose generated signature (Composer, int)
      parameterCount == 2 -> {
        isStandardComposableSignature(parameterTypes)
      }

      // Pattern 3: Custom parameters + Compose generated (Composer is last or second-to-last)
      parameterCount > 2 -> {
        hasComposerInSignature(parameterTypes)
      }

      // Pattern 4: Single parameter that might be Composer
      parameterCount == 1 -> {
        isComposerParameter(parameterTypes[0])
      }

      else -> false
    }
  }

  /**
   * Check for standard Composable signature: (Composer, int)
   */
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  private fun isStandardComposableSignature(parameterTypes: Array<Class<*>>): Boolean {
    return parameterTypes.size == 2 &&
           isComposerParameter(parameterTypes[0]) &&
           (parameterTypes[1] == Int::class.javaPrimitiveType || parameterTypes[1] == Integer::class.java)
  }

  /**
   * Check if signature contains Composer parameter
   */
  private fun hasComposerInSignature(parameterTypes: Array<Class<*>>): Boolean {
    return parameterTypes.any { isComposerParameter(it) }
  }

  /**
   * Check if the parameter is a Composer type
   */
  private fun isComposerParameter(parameterType: Class<*>): Boolean {
    return parameterType.name == "androidx.compose.runtime.Composer" ||
           parameterType.simpleName == "Composer" ||
           Composer::class.java.isAssignableFrom(parameterType)
  }

  /**
   * Create ComposablePreviewFunction wrapper
   */
  private fun createPreviewFunction(method: Method, containingClass: Class<*>): ComposablePreviewFunction {
    return ComposablePreviewFunction(
      name = method.name,
      qualifiedName = "${containingClass.name}.${method.name}",
      method = method,
      containingClass = containingClass,
      isStatic = Modifier.isStatic(method.modifiers),
      signature = createSignatureString(method)
    )
  }

  /**
   * Create a human-readable signature string
   */
  private fun createSignatureString(method: Method): String {
    val params = method.parameterTypes.joinToString(", ") { it.simpleName }
    return "${method.name}($params): ${method.returnType.simpleName}"
  }
}

/**
 * Data class representing a discovered preview function
 */
internal data class ComposablePreviewFunction(
  val name: String,
  val qualifiedName: String,
  val method: Method,
  val containingClass: Class<*>,
  val isStatic: Boolean,
  val signature: String,
) {
  /**
   * Invoke this preview function
   */
  fun invoke(vararg args: Any?): Any? {
    method.isAccessible = true
    return if (isStatic) {
      method.invoke(null, *args)
    }
    else {
      val instance = containingClass.getDeclaredConstructor().newInstance()
      method.invoke(instance, *args)
    }
  }
}
