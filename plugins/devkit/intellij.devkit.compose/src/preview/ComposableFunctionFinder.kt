// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import androidx.compose.runtime.Composer
import com.intellij.openapi.diagnostic.Logger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private val logger = Logger.getInstance(ComposableFunctionFinder::class.java)

/**
 * Discovers and validates Composable Preview functions using reflection on bytecode
 */
internal class ComposableFunctionFinder(private val classLoader: ClassLoader) {
  companion object {
    private val PREVIEW_ANNOTATIONS = setOf(
      "androidx.compose.desktop.ui.tooling.preview.Preview",
      "androidx.compose.ui.tooling.preview.Preview", // Android fallback
      "Preview" // Short name fallback
    )

    // Composable function signatures typically have these parameter patterns:
    // 1. (Composer, int) - Standard compose compiler generated parameters
    // 2. () - No parameters for simple previews
    // 3. Custom parameters + (Composer, int) - Preview with custom parameters
  }

  /**
   * Main entry point - finds all valid preview functions in the given classes
   */
  fun findPreviewFunctions(clazzFqn: String): List<ComposablePreviewFunction> {
    val previewFunctions = mutableListOf<ComposablePreviewFunction>()

    try {
      val clazz = classLoader.loadClass(clazzFqn)
      val functions = findPreviewFunctionsInClass(clazz)
      previewFunctions.addAll(functions)
    }
    catch (e: ClassNotFoundException) {
      logger.warn("Class not found: $clazzFqn", e)
    }
    catch (e: Exception) {
      logger.error("Error processing class: $clazzFqn", e)
    }

    return previewFunctions
  }

  /**
   * Find all preview functions in a specific class
   */
  private fun findPreviewFunctionsInClass(clazz: Class<*>): List<ComposablePreviewFunction> {
    val previewFunctions = mutableListOf<ComposablePreviewFunction>()

    // Get all declared methods (including private ones)
    val methods = clazz.declaredMethods

    for (method in methods) {
      try {
        if (isValidPreviewFunction(method)) {
          val previewFunction = createPreviewFunction(method, clazz)
          previewFunctions.add(previewFunction)
          logger.debug("Found preview function: ${clazz.name}.${method.name}")
        }
      }
      catch (e: Exception) {
        logger.warn("Error processing method: ${clazz.name}.${method.name}", e)
      }
    }

    return previewFunctions
  }

  /**
   * Validates if a method is a valid Composable Preview function
   */
  private fun isValidPreviewFunction(method: Method): Boolean {
    // Check for required annotations
    // This doesn't work for us since we are processing Composable function representations in the byte code
    //        if (!hasComposableAnnotation(method)) {
    //            return false
    //        }

    // Validate function signature
    if (!hasValidComposableSignature(method)) {
      return false
    }

    if (!hasPreviewAnnotation(method)) {
      return false
    }


    // Should be public or internal (accessible)
    if (!isAccessibleFunction(method)) {
      return false
    }

    return true
  }

  /**
   * Check if a method has @Preview annotation
   */
  private fun hasPreviewAnnotation(method: Method): Boolean {
    return method.annotations.any { annotation ->
      val annotationName = annotation.annotationClass.java.name
      PREVIEW_ANNOTATIONS.any { previewAnnotation ->
        annotationName.endsWith(previewAnnotation)
      }
    } || method.declaredAnnotations.any { annotation ->
      // Also, check declared annotations for bytecode compatibility
      val annotationName = annotation.annotationClass.java.simpleName
      PREVIEW_ANNOTATIONS.contains(annotationName)
    }
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
   * Check if a function is accessible (public or internal)
   */
  private fun isAccessibleFunction(method: Method): Boolean {
    val modifiers = method.modifiers
    return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
           || !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers)
  }

  /**
   * Extract preview parameters from @Preview annotation
   */
  private fun extractPreviewParameters(method: Method): PreviewParameters {
    val previewAnnotations = method.annotations.filter { annotation ->
      val annotationName = annotation.annotationClass.java.name
      PREVIEW_ANNOTATIONS.any { previewAnnotation ->
        annotationName.endsWith(previewAnnotation)
      }
    }

    if (previewAnnotations.isEmpty()) {
      return PreviewParameters()
    }

    val previewAnnotation = previewAnnotations.first()

    return try {
      // Use reflection to extract annotation parameters
      val annotationClass = previewAnnotation.annotationClass.java

      val name = getAnnotationValue(previewAnnotation, annotationClass, "name") as? String ?: ""
      val group = getAnnotationValue(previewAnnotation, annotationClass, "group") as? String ?: ""
      val widthDp = getAnnotationValue(previewAnnotation, annotationClass, "widthDp") as? Int ?: -1
      val heightDp = getAnnotationValue(previewAnnotation, annotationClass, "heightDp") as? Int ?: -1
      val showBackground =
        getAnnotationValue(previewAnnotation, annotationClass, "showBackground") as? Boolean ?: false

      PreviewParameters(
        name = name,
        group = group,
        widthDp = widthDp,
        heightDp = heightDp,
        showBackground = showBackground
      )
    }
    catch (e: Exception) {
      logger.warn("Failed to extract preview parameters from: ${method.name}", e)
      PreviewParameters()
    }
  }

  /**
   * Helper to safely extract annotation values using reflection
   */
  private fun getAnnotationValue(annotation: Annotation, annotationClass: Class<*>, paramName: String): Any? {
    return try {
      val method = annotationClass.getDeclaredMethod(paramName)
      method.invoke(annotation)
    }
    catch (_: Exception) {
      null
    }
  }

  /**
   * Create ComposablePreviewFunction wrapper
   */
  private fun createPreviewFunction(method: Method, containingClass: Class<*>): ComposablePreviewFunction {
    val previewParams = extractPreviewParameters(method)

    return ComposablePreviewFunction(
      name = method.name,
      qualifiedName = "${containingClass.name}.${method.name}",
      method = method,
      containingClass = containingClass,
      parameters = previewParams,
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
data class ComposablePreviewFunction(
  val name: String,
  val qualifiedName: String,
  val method: Method,
  val containingClass: Class<*>,
  val parameters: PreviewParameters,
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

/**
 * Preview annotation parameters
 */
data class PreviewParameters(
  val name: String = "",
  val group: String = "",
  val widthDp: Int = -1,
  val heightDp: Int = -1,
  val showBackground: Boolean = false,
  val backgroundColor: Long = 0xFFFFFFFF,
)