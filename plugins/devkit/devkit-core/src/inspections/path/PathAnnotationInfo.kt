// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.isInheritorOf
import com.intellij.platform.eel.annotations.Filename
import com.intellij.platform.eel.annotations.LocalPath
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost

/**
 * Contains information about path annotation status.
 */
internal sealed interface PathAnnotationInfo {
  sealed class Specified : PathAnnotationInfo {
    abstract fun getAnnotationClass(): Class<*>
    val shortAnnotationName: String = getAnnotationClass().simpleName.let { "@$it" }
    val quickFixesFor: (PsiElement?) -> List<LocalQuickFix> = { psiElement ->
      val variableToAnnotate = psiElement?.let { findVariableToAnnotate(it) }
      if (variableToAnnotate != null) {
        val action = AddAnnotationModCommandAction(getAnnotationClass().canonicalName, variableToAnnotate)
        listOfNotNull(LocalQuickFix.from(action))
      }
      else emptyList()
    }
  }

  object MultiRouting : Specified() {
    override fun getAnnotationClass(): Class<*> = MultiRoutingFileSystemPath::class.java
  }

  object Native : Specified() {
    override fun getAnnotationClass(): Class<*> = NativePath::class.java
  }

  object LocalPathInfo : Specified() {
    override fun getAnnotationClass(): Class<*> = LocalPath::class.java
  }

  object FilenameInfo : Specified() {
    override fun getAnnotationClass(): Class<*> = Filename::class.java
  }

  object Unspecified : PathAnnotationInfo

  companion object {
    /**
     * Checks if the given string is a valid filename (no forward or backward slashes).
     */
    fun isValidFilename(str: String): Boolean {
      return !str.contains('/') && !str.contains('\\')
    }

    fun forExpression(expression: UExpression): PathAnnotationInfo {
      if (expression is UQualifiedReferenceExpression) {
        val propertyName = getKotlinStdlibPathsExtensionPropertyNameOrNull(expression)
        if (propertyName in setOf("name", "nameWithoutExtension", "extension")) {
          return FilenameInfo
        }
        if (propertyName in setOf("pathString")) {
          return MultiRouting
        }
      }

      val callExpression = expression.getUCallExpression()
      if (callExpression != null) {
        if (callExpression.receiverType?.isInheritorOf("java.nio.file.Path") == true) {
          // Check for Path.toString() which returns @MultiRoutingFileSystemPath
          if (callExpression.methodName == "toString") {
            return MultiRouting
          }

          // Check for Kotlin extension methods that return @Filename
          val methodName = callExpression.methodName
          if (methodName != null && VirtualPathAnnotationRegistry.isKotlinExtensionMethodReturningFilename(methodName)) {
            return FilenameInfo
          }

          // Check for Kotlin extension methods that return @MultiRoutingFileSystemPath
          if (methodName != null && VirtualPathAnnotationRegistry.isKotlinExtensionMethodReturningMultiRoutingPath(methodName)) {
            return MultiRouting
          }
        }

        // Check if the argument is a call to System.getProperty("user.home")
        if (isSystemGetPropertyAbsolutePathResult(expression)) {
          return LocalPathInfo
        }

        // Check if the argument is a call to a method that effectively returns a @LocalPath annotated string
        if (isMethodReturningLocalPathResult(expression)) {
          return LocalPathInfo
        }
      }

      // Check if the expression is a reference to a variable that is initialized with a Kotlin extension method
      if (expression is UReferenceExpression) {
        val resolved = expression.resolve()
        if (resolved is PsiVariable) {
          val initializer = resolved.initializer
          if (initializer != null) {
            // Check if the initializer is a method call
            if (initializer is PsiMethodCallExpression) {
              val methodExpression = initializer.methodExpression
              val qualifierExpression = methodExpression.qualifierExpression
              val methodName = methodExpression.referenceName

              // Check if the method is called on a Path object
              if (qualifierExpression != null && methodName != null) {
                val qualifierType = qualifierExpression.type
                if (qualifierType != null && qualifierType.canonicalText == "java.nio.file.Path") {
                  // Check for Kotlin extension methods that return @Filename
                  val filenameExtensionMethods = setOf("getName", "getNameWithoutExtension", "getExtension")
                  if (methodName in filenameExtensionMethods) {
                    return FilenameInfo
                  }

                  // Check for Kotlin extension methods that return @MultiRoutingFileSystemPath
                  val multiRoutingExtensionMethods = setOf("getPathString", "absolutePathString")
                  if (methodName in multiRoutingExtensionMethods) {
                    return MultiRouting
                  }
                }
              }
            }

            // Check if the initializer text contains Kotlin extension method calls
            val initializerText = initializer.text

            // Check for Kotlin extension methods that return @Filename
            for (methodName in VirtualPathAnnotationRegistry.getKotlinExtensionMethodsReturningFilename()) {
              if (initializerText.contains(".$methodName")) {
                return FilenameInfo
              }
            }

            // Check for Kotlin extension methods that return @MultiRoutingFileSystemPath
            for (methodName in VirtualPathAnnotationRegistry.getKotlinExtensionMethodsReturningMultiRoutingPath()) {
              if (initializerText.contains(".$methodName")) {
                return MultiRouting
              }
            }
          }
        }
      }

      // Check if the expression has a path annotation
      val sourcePsi = expression.sourcePsi
      if (sourcePsi != null && sourcePsi is PsiModifierListOwner) {
        return forModifierListOwner(sourcePsi)
      }

      // Check if the expression is a reference to a variable with a path annotation
      if (expression is UReferenceExpression) {
        val resolvedUElement = expression.resolveToUElement()
        if (resolvedUElement is UAnnotated) {
          if (resolvedUElement.findAnnotation(NativePath::class.java.canonicalName) != null) {
            return Native
          }
          if (resolvedUElement.findAnnotation(MultiRoutingFileSystemPath::class.java.canonicalName) != null) {
            return MultiRouting
          }
          if (resolvedUElement.findAnnotation(LocalPath::class.java.canonicalName) != null) {
            return LocalPathInfo
          }
          if (resolvedUElement.findAnnotation(Filename::class.java.canonicalName) != null) {
            return FilenameInfo
          }
        }

        if (resolvedUElement is UVariable) {
          val initializer = resolvedUElement.uastInitializer
          if (initializer is UQualifiedReferenceExpression) {
            val resolvedInitializer = initializer.resolveToUElement()
            if (resolvedInitializer is UAnnotated) {
              if (resolvedInitializer.findAnnotation(NativePath::class.java.canonicalName) != null) {
                return Native
              }
              if (resolvedInitializer.findAnnotation(MultiRoutingFileSystemPath::class.java.canonicalName) != null) {
                return MultiRouting
              }
              if (resolvedInitializer.findAnnotation(LocalPath::class.java.canonicalName) != null) {
                return LocalPathInfo
              }
              if (resolvedInitializer.findAnnotation(Filename::class.java.canonicalName) != null) {
                return FilenameInfo
              }
            }
          }
        }

        (resolvedUElement as? UVariable)?.uastInitializer?.getUCallExpression()?.let { callExpression ->
          if (callExpression.receiverType?.isInheritorOf("java.nio.file.Path") == true
              && callExpression.methodName == "toString") {
            return MultiRouting
          }
        }

        val resolved = expression.resolve()
        if (resolved is PsiModifierListOwner) {
          val info = forModifierListOwner(resolved)
          if (info !is Unspecified) {
            return info
          }

          // Check if the reference is to a variable with a string constant initializer that denotes a filename
          if (resolved is PsiVariable) {
            val initializer = resolved.initializer
            if (initializer != null) {
              // Check if the initializer is a call to System.getProperty("user.home")
              if (initializer is PsiMethodCallExpression) {
                val methodExpression = initializer.methodExpression
                val qualifierExpression = methodExpression.qualifierExpression
                val methodName = methodExpression.referenceName

                if (methodName == "getProperty" && qualifierExpression != null) {
                  val qualifierText = qualifierExpression.text
                  if (qualifierText == "System") {
                    val args = initializer.argumentList.expressions
                    if (args.size >= 1) {
                      val arg = args[0]
                      if (arg is PsiLiteralExpression) {
                        val value = arg.value
                        if (value is String && isSystemPropertyAbsolutePathValue(value)) {
                          return LocalPathInfo
                        }
                      }
                    }
                  }
                }

                // Check if the initializer is a call to a method that effectively returns a @LocalPath annotated string
                val method = initializer.resolveMethod()
                if (method != null && VirtualPathAnnotationRegistry.isMethodReturningLocalPath(method)) {
                  return LocalPathInfo
                }
              }

              val evaluatedValue = expression.evaluate()
              if (evaluatedValue is String && isValidFilename(evaluatedValue)) {
                return FilenameInfo
              }

              // Try to evaluate the initializer as a string constant
              val constantValue = JavaPsiFacade.getInstance(resolved.project)
                .constantEvaluationHelper.computeConstantExpression(initializer)
              if (constantValue is String && isValidFilename(constantValue)) {
                return FilenameInfo
              }
            }
          }
        }
      }

      // We don't check if the expression is a string literal or constant that denotes a filename here
      // because we want to handle that in the visitCallExpression method

      return Unspecified
    }

    fun forModifierListOwner(owner: PsiModifierListOwner): PathAnnotationInfo {
      // Check if the owner has a path annotation
      if (AnnotationUtil.isAnnotated(owner, MultiRoutingFileSystemPath::class.java.name, AnnotationUtil.CHECK_TYPE)) {
        return MultiRouting
      }
      if (AnnotationUtil.isAnnotated(owner, NativePath::class.java.name, AnnotationUtil.CHECK_TYPE)) {
        return Native
      }
      if (AnnotationUtil.isAnnotated(owner, LocalPath::class.java.name, AnnotationUtil.CHECK_TYPE)) {
        return LocalPathInfo
      }
      if (AnnotationUtil.isAnnotated(owner, Filename::class.java.name, AnnotationUtil.CHECK_TYPE)) {
        return FilenameInfo
      }
      return Unspecified
    }
  }
}

/**
 * Checks if the expression is a call to System.getProperty("user.home").
 */
internal fun isSystemGetPropertyAbsolutePathResult(expression: UExpression): Boolean {
  val callExpression = expression.getUCallExpression() ?: return false
  val method = callExpression.resolve()
  if (method is PsiMethod) {
    val containingClass = method.containingClass
    if (containingClass != null && containingClass.qualifiedName == "java.lang.System" && method.name == "getProperty") {
      val arguments = callExpression.valueArguments
      val arg = arguments.firstOrNull() // it is expected to be the first and the only argument of `System.getProperty()` method call
      if (arg != null) {
        if (arg is UInjectionHost) {
          val stringValue = arg.evaluateToString()
          if (stringValue != null && isSystemPropertyAbsolutePathValue(stringValue)) {
            return true
          }
        }
        else if (arg is ULiteralExpression) {
          val value = arg.value
          if (value is String && isSystemPropertyAbsolutePathValue(value)) {
            return true
          }
        }
      }
    }
  }
  return false
}

/**
 * Checks if the expression is a call to a method that effectively returns a `@LocalPath` annotated string.
 */
private fun isMethodReturningLocalPathResult(expression: UExpression): Boolean {
  val callExpression = expression.getUCallExpression() ?: return false
  val method = callExpression.resolve()
  if (method is PsiMethod) {
    return VirtualPathAnnotationRegistry.isMethodReturningLocalPath(method)
  }
  return false
}

private fun isSystemPropertyAbsolutePathValue(propertyName: String): Boolean =
  setOf("java.home", "user.home", "user.dir", "java.io.tmpdir").contains(propertyName)

private fun findVariableToAnnotate(element: PsiElement): PsiModifierListOwner? {
  // If the element is already a variable, return it
  if (element is PsiModifierListOwner) {
    return element
  }

  // If the element is a method call, try to find the variable reference inside it
  if (element is PsiMethodCallExpression) {
    val argumentList = element.argumentList
    if (argumentList.expressionCount > 0) {
      val firstArg = argumentList.expressions[0]
      if (firstArg is PsiReferenceExpression) {
        val resolved = firstArg.resolve()
        if (resolved is PsiModifierListOwner) {
          return resolved
        }
      }
    }
  }

  // If the element is a reference expression, resolve it to find the variable declaration
  if (element is PsiReferenceExpression) {
    val resolved = element.resolve()
    if (resolved is PsiModifierListOwner) {
      return resolved
    }
  }

  // Try to find a reference expression inside the element
  val references = element.references
  for (reference in references) {
    val resolved = reference.resolve()
    if (resolved is PsiModifierListOwner) {
      return resolved
    }
  }

  // Try to find a reference expression in the parent of the element
  val parent = element.parent
  if (parent is PsiMethodCallExpression) {
    val argumentList = parent.argumentList
    if (argumentList.expressionCount > 0) {
      val firstArg = argumentList.expressions[0]
      if (firstArg is PsiReferenceExpression) {
        val resolved = firstArg.resolve()
        if (resolved is PsiModifierListOwner) {
          return resolved
        }
      }
    }
  }

  return null
}