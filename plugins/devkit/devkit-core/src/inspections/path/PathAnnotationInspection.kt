// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.platform.eel.annotations.LocalPath
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Inspection that checks for proper usage of path annotations ([MultiRoutingFileSystemPath], [NativePath], and [LocalPath]).
 * It highlights cases where a string annotated with one path annotation is used in a context that expects a string
 * with a different path annotation.
 */
class PathAnnotationInspection : DevKitUastInspectionBase() {
  override fun getDisplayName(): String = DevKitBundle.message("inspections.path.annotation.usage.problems")
  override fun getGroupDisplayName(): String = "DevKit"
  override fun getShortName(): String = "PathAnnotationInspection"

  override fun buildInternalVisitor(@NotNull holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return com.intellij.uast.UastHintedVisitorAdapter.create(
      holder.file.language,
      PathAnnotationVisitor(holder),
      arrayOf(UCallExpression::class.java, UInjectionHost::class.java, UReturnExpression::class.java)
    )
  }

  /**
   * Visitor that checks for path annotation issues.
   */
  private inner class PathAnnotationVisitor(
    private val holder: ProblemsHolder,
  ) : AbstractUastNonRecursiveVisitor() {
    // `expectedInfo` to `actualInfo`
    private val compatibleAnnotations = mapOf(
      PathAnnotationInfo.MultiRouting to setOf(PathAnnotationInfo.FilenameInfo, PathAnnotationInfo.LocalPathInfo),
      PathAnnotationInfo.Native to setOf(PathAnnotationInfo.FilenameInfo),
      PathAnnotationInfo.LocalPathInfo to setOf(PathAnnotationInfo.FilenameInfo),
    )

    override fun visitCallExpression(node: UCallExpression): Boolean {
      val sourcePsi = node.sourcePsi ?: return true
      val target = node.resolve() ?: return true

      // Check if the method is a Path constructor or factory method
      if (isPathConstructorOrFactory(target)) {
        val arguments = node.valueArguments

        // Special case for Path.of(System.getProperty("user.home"))
        if (arguments.size == 1 && isSystemGetPropertyAbsolutePathResult(arguments[0])) {
          // If it's Path.of(System.getProperty("user.home")), don't register any problems
          return true
        }

        for (i in 0 until arguments.size) {
          val arg = arguments[i]

          // Check if the argument is a string literal that denotes a valid filename
          if (arg is UInjectionHost) {
            val stringValue = arg.evaluateToString()
            if (stringValue != null && PathAnnotationInfo.isValidFilename(stringValue)) {
              // If it's a valid filename, don't register any problems
              continue
            }
          }

          // Check if the argument is a reference to a variable with a string constant initializer that denotes a valid filename
          if (arg is UReferenceExpression) {
            val resolved = arg.resolve()
            if (resolved is com.intellij.psi.PsiVariable) {
              val initializer = resolved.initializer
              if (initializer != null) {
                // Try to evaluate the initializer as a string constant
                val constantValue = com.intellij.psi.JavaPsiFacade.getInstance(resolved.project)
                  .constantEvaluationHelper.computeConstantExpression(initializer)
                if (constantValue is String && PathAnnotationInfo.isValidFilename(constantValue)) {
                  // If it's a valid filename, don't register any problems
                  continue
                }
              }
            }
          }

          // Check if the argument is a call to System.getProperty("user.home")
          if (isSystemGetPropertyAbsolutePathResult(arg)) {
            // If it's System.getProperty("user.home"), don't register any problems
            continue
          }

          val argInfo = PathAnnotationInfo.forExpression(arg)
          when (argInfo) {
            is PathAnnotationInfo.MultiRouting, PathAnnotationInfo.FilenameInfo, PathAnnotationInfo.LocalPathInfo -> Unit
            is PathAnnotationInfo.Native -> {
              if (i == 0)
                holder.registerProblem(
                  arg.sourcePsi ?: sourcePsi,
                  DevKitBundle.message("inspections.message.nativepath.should.not.be.used.directly.constructing.path"),
                  *PathAnnotationInfo.MultiRouting.quickFixesFor(arg.sourcePsi).toTypedArray()
                )
            }
            is PathAnnotationInfo.Unspecified -> {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                if (i == 0)
                  DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
                else
                  DevKitBundle.message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename"),
                *PathAnnotationInfo.MultiRouting.quickFixesFor(arg.sourcePsi).toTypedArray()
              )
            }
          }
        }
      }

      // Check if the method is Path.resolve()
      if (isPathResolveMethod(target)) {
        // Check if the argument is annotated with @MultiRoutingFileSystemPath or @Filename
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
          val arg = arguments[0]

          // Check if the argument is of type java.nio.file.Path
          val parameter = getParameterForArgument(target, 0)
          if (parameter != null && isPathType(parameter)) {
            // If the parameter is of type Path, don't register any problems
            return true
          }

          // Check if the argument is a string literal that denotes a valid filename
          if (arg is UInjectionHost) {
            val stringValue = arg.evaluateToString()
            if (stringValue != null && PathAnnotationInfo.isValidFilename(stringValue)) {
              // If it's a valid filename, don't register any problems
              return true
            }
          }

          // Check if the argument is a reference to a variable with a string constant initializer that denotes a valid filename
          if (arg is UReferenceExpression) {
            val resolved = arg.resolve()
            if (resolved is com.intellij.psi.PsiVariable) {
              val initializer = resolved.initializer
              if (initializer != null) {
                // Try to evaluate the initializer as a string constant
                val constantValue = com.intellij.psi.JavaPsiFacade.getInstance(resolved.project)
                  .constantEvaluationHelper.computeConstantExpression(initializer)
                if (constantValue is String && PathAnnotationInfo.isValidFilename(constantValue)) {
                  // If it's a valid filename, don't register any problems
                  return true
                }
              }
            }
          }

          val argInfo = PathAnnotationInfo.forExpression(arg)
          when (argInfo) {
            is PathAnnotationInfo.MultiRouting, PathAnnotationInfo.FilenameInfo -> Unit
            is PathAnnotationInfo.Native -> {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method"),
                *PathAnnotationInfo.MultiRouting.quickFixesFor(arg.sourcePsi).toTypedArray()
              )
            }
            is PathAnnotationInfo.LocalPathInfo -> {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method"),
                *PathAnnotationInfo.MultiRouting.quickFixesFor(arg.sourcePsi).toTypedArray()
              )
            }
            is PathAnnotationInfo.Unspecified -> {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method"),
                com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING,
                *PathAnnotationInfo.MultiRouting.quickFixesFor(arg.sourcePsi).toTypedArray()
              )
            }
          }
        }
      }

      // Check if the method is FileSystem.getPath()
      if (isFileSystemGetPathMethod(target)) {
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
          // Check first argument (should be annotated with @NativePath)
          val firstArg = arguments[0]
          val firstArgInfo = PathAnnotationInfo.forExpression(firstArg)
          when (firstArgInfo) {
            is PathAnnotationInfo.Native -> Unit
            is PathAnnotationInfo.MultiRouting -> {
              holder.registerProblem(
                firstArg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                *PathAnnotationInfo.Native.quickFixesFor(firstArg.sourcePsi).toTypedArray()
              )
            }
            is PathAnnotationInfo.LocalPathInfo -> {
              holder.registerProblem(
                firstArg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                *PathAnnotationInfo.Native.quickFixesFor(firstArg.sourcePsi).toTypedArray()
              )
            }
            is PathAnnotationInfo.FilenameInfo -> {
              holder.registerProblem(
                firstArg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                *PathAnnotationInfo.Native.quickFixesFor(firstArg.sourcePsi).toTypedArray()
              )
            }
            is PathAnnotationInfo.Unspecified -> {
              holder.registerProblem(
                firstArg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                *PathAnnotationInfo.Native.quickFixesFor(firstArg.sourcePsi).toTypedArray()
              )
            }
          }

          // Check remaining arguments (should be annotated with either @NativePath or @Filename)
          if (arguments.size > 1) {
            for (i in 1 until arguments.size) {
              val arg = arguments[i]

              // Check if the argument is a string literal that denotes a valid filename
              if (arg is UInjectionHost) {
                val stringValue = arg.evaluateToString()
                if (stringValue != null && PathAnnotationInfo.isValidFilename(stringValue)) {
                  // If it's a valid filename, don't register any problems
                  continue
                }
              }

              // Check if the argument is a reference to a variable with a string constant initializer that denotes a valid filename
              if (arg is UReferenceExpression) {
                val resolved = arg.resolve()
                if (resolved is com.intellij.psi.PsiVariable) {
                  val initializer = resolved.initializer
                  if (initializer != null) {
                    // Try to evaluate the initializer as a string constant
                    val constantValue = com.intellij.psi.JavaPsiFacade.getInstance(resolved.project)
                      .constantEvaluationHelper.computeConstantExpression(initializer)
                    if (constantValue is String && PathAnnotationInfo.isValidFilename(constantValue)) {
                      // If it's a valid filename, don't register any problems
                      continue
                    }
                  }
                }
              }

              val argInfo = PathAnnotationInfo.forExpression(arg)
              when (argInfo) {
                is PathAnnotationInfo.Native, PathAnnotationInfo.FilenameInfo -> Unit
                is PathAnnotationInfo.MultiRouting -> {
                  holder.registerProblem(
                    arg.sourcePsi ?: sourcePsi,
                    DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename"),
                    *PathAnnotationInfo.Native.quickFixesFor(arg.sourcePsi).toTypedArray()
                  )
                }
                is PathAnnotationInfo.LocalPathInfo -> {
                  holder.registerProblem(
                    arg.sourcePsi ?: sourcePsi,
                    DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename"),
                    *PathAnnotationInfo.Native.quickFixesFor(arg.sourcePsi).toTypedArray()
                  )
                }
                is PathAnnotationInfo.Unspecified -> {
                  holder.registerProblem(
                    arg.sourcePsi ?: sourcePsi,
                    DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename"),
                    *PathAnnotationInfo.Native.quickFixesFor(arg.sourcePsi).toTypedArray()
                  )
                }
              }
            }
          }
        }
      }

      // Check if the method expects a specific path annotation
      for ((index, arg) in node.valueArguments.withIndex()) {
        val parameter = getParameterForArgument(target, index) ?: continue

        // Skip validation if the parameter is of type java.nio.file.Path
        if (isPathType(parameter)) {
          continue
        }

        val expectedInfo = PathAnnotationInfo.forModifierListOwner(parameter)

        // skip right away if we do not expect anything
        // `when` smart-casts `expectedInfo` to `PathAnnotationInfo.Specified`
        when (expectedInfo) {
          is PathAnnotationInfo.Specified -> Unit
          is PathAnnotationInfo.Unspecified -> continue
        }
        val actualInfo = PathAnnotationInfo.forExpression(arg)

        // Check if the argument is a string literal that denotes a valid filename
        if (arg is UInjectionHost) {
          val stringValue = arg.evaluateToString()
          if (stringValue != null && PathAnnotationInfo.isValidFilename(stringValue)) {
            // If it's a valid filename, don't register any problems
            continue
          }
        }

        // Check if the argument is a reference to a variable with a string constant initializer that denotes a valid filename
        if (arg is UReferenceExpression) {
          val resolved = arg.resolve()
          if (resolved is com.intellij.psi.PsiVariable) {
            val initializer = resolved.initializer
            if (initializer != null) {
              // Try to evaluate the initializer as a string constant
              val constantValue = com.intellij.psi.JavaPsiFacade.getInstance(resolved.project)
                .constantEvaluationHelper.computeConstantExpression(initializer)
              if (constantValue is String && PathAnnotationInfo.isValidFilename(constantValue)) {
                // If it's a valid filename, don't register any problems
                continue
              }
            }
          }
        }

        when (actualInfo) {
          is PathAnnotationInfo.Unspecified -> {
            // Report error: Unannotated string passed to the method that expects a particular path annotation
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.method.parameter.annotated.with", expectedInfo.shortAnnotationName),
              *expectedInfo.quickFixesFor(arg.sourcePsi).toTypedArray()
            )
          }
          is PathAnnotationInfo.Specified -> {
            if (expectedInfo != actualInfo && compatibleAnnotations[expectedInfo]?.contains(actualInfo) != true) {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.string.annotated.with.passed.to.method.parameter.annotated.with", actualInfo.shortAnnotationName, expectedInfo.shortAnnotationName),
                *expectedInfo.quickFixesFor(arg.sourcePsi).toTypedArray()
              )
            }
          }
        }
      }

      return true
    }

    override fun visitReturnExpression(node: UReturnExpression): Boolean {
      val sourcePsi = node.sourcePsi ?: return true
      val returnValue = node.returnExpression ?: return true

      // Skip if the return value is a string literal that denotes a valid filename
      if (returnValue is UInjectionHost) {
        val stringValue = returnValue.evaluateToString()
        if (stringValue != null && PathAnnotationInfo.isValidFilename(stringValue)) {
          // If it's a valid filename, don't register any problems
          return true
        }
      }

      // Skip if the return value is a reference to a variable with a string constant initializer that denotes a valid filename
      if (returnValue is UReferenceExpression) {
        val resolved = returnValue.resolve()
        if (resolved is com.intellij.psi.PsiVariable) {
          val initializer = resolved.initializer
          if (initializer != null) {
            // Try to evaluate the initializer as a string constant
            val constantValue = com.intellij.psi.JavaPsiFacade.getInstance(resolved.project)
              .constantEvaluationHelper.computeConstantExpression(initializer)
            if (constantValue is String && PathAnnotationInfo.isValidFilename(constantValue)) {
              // If it's a valid filename, don't register any problems
              return true
            }
          }
        }
      }

      // Get the containing method
      val containingMethod = node.getContainingUMethod() ?: return true
      val methodPsi = containingMethod.javaPsi

      // Check if the method has a return type with a path annotation
      val expectedInfo = PathAnnotationInfo.forModifierListOwner(methodPsi)
      when (expectedInfo) {
        is PathAnnotationInfo.Specified -> Unit
        is PathAnnotationInfo.Unspecified -> {
          // If the method doesn't have a path annotation, don't register any problems
          return true
        }
      }

      // Check if the return value has a path annotation
      val actualInfo = PathAnnotationInfo.forExpression(returnValue)

      // Check if the return value matches the expected path annotation type
      when (actualInfo) {
        is PathAnnotationInfo.Unspecified -> {
          // Report error: Unannotated string passed to the method that expects a particular path annotation
          holder.registerProblem(
            returnValue.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspection.message.return.value.without.path.annotation.where.expected", expectedInfo.shortAnnotationName),
            *expectedInfo.quickFixesFor(returnValue.sourcePsi).toTypedArray()
          )
        }
        is PathAnnotationInfo.Specified -> {
          if (expectedInfo != actualInfo && compatibleAnnotations[expectedInfo]?.contains(actualInfo) != true) {
            holder.registerProblem(
              returnValue.sourcePsi ?: sourcePsi,
              DevKitBundle.message("inspection.message.method.annotated.with.returns.value.annotated.with", expectedInfo.shortAnnotationName, actualInfo.shortAnnotationName),
              *expectedInfo.quickFixesFor(returnValue.sourcePsi).toTypedArray()
            )
          }
        }
      }

      return true
    }

    override fun visitElement(node: UElement): Boolean {
      if (node is UInjectionHost) {
        val psi = node.sourcePsi
        if (psi != null) {
          visitLiteralExpression(psi, node)
        }
      }
      return super.visitElement(node)
    }

    private fun visitLiteralExpression(sourcePsi: PsiElement, expression: UInjectionHost) {
      val stringValue = expression.evaluateToString() ?: return
      if (stringValue.isBlank()) return

      // Skip processing if this is a string literal used in Path.of() or FileSystem.getPath() methods
      // as those are already handled in visitCallExpression
      val parent = expression.uastParent
      if (parent is UCallExpression) {
        val method = parent.resolve()
        if (method != null && (isPathConstructorOrFactory(method) || isFileSystemGetPathMethod(method))) {
          // Skip processing as this will be handled by visitCallExpression
          return
        }
      }

      val nonAnnotatedTargets = mutableSetOf<PsiModifierListOwner>()
      val expectedInfo = getExpectedPathAnnotationInfo(expression, nonAnnotatedTargets)

      // Check if the string literal is a valid filename (no forward or backward slashes)
      if (PathAnnotationInfo.isValidFilename(stringValue)) {
        // If it's a valid filename, don't register any problems
        return
      }

      if (expectedInfo is PathAnnotationInfo.MultiRouting) {
        // Check if the string literal is used in a context that expects @MultiRoutingFileSystemPath
        if (nonAnnotatedTargets.isNotEmpty()) {
          holder.registerProblem(
            sourcePsi,
            DevKitBundle.message("inspections.message.multiroutingfilesystempath.expected"),
            *expectedInfo.quickFixesFor(sourcePsi).toTypedArray()
          )
        }
      }
      else if (expectedInfo is PathAnnotationInfo.Native) {
        // Check if the string literal is used in a context that expects @NativePath
        if (nonAnnotatedTargets.isNotEmpty()) {
          holder.registerProblem(
            sourcePsi,
            DevKitBundle.message("inspections.message.nativepath.expected"),
            *expectedInfo.quickFixesFor(sourcePsi).toTypedArray()
          )
        }
      }
    }

    private fun getExpectedPathAnnotationInfo(
      expression: UExpression,
      nonAnnotatedTargets: MutableSet<PsiModifierListOwner>,
    ): PathAnnotationInfo {
      // Check if the expression is passed to a method that expects a specific path annotation
      val parent = expression.uastParent
      if (parent is UCallExpression) {
        val method = parent.resolve() ?: return PathAnnotationInfo.Unspecified
        val index = parent.valueArguments.indexOf(expression)
        if (index >= 0) {
          val parameter = getParameterForArgument(method, index) ?: return PathAnnotationInfo.Unspecified

          // Check if the method effectively accepts a @LocalPath annotated string as a parameter
          if (VirtualPathAnnotationRegistry.isMethodAcceptingLocalPath(method)) {
            return PathAnnotationInfo.LocalPathInfo
          }

          val info = PathAnnotationInfo.forModifierListOwner(parameter)
          if (info !is PathAnnotationInfo.Unspecified) {
            return info
          }
          nonAnnotatedTargets.add(parameter)
        }
      }

      // Check if the expression is assigned to a variable with a specific path annotation
      if (parent is UVariable) {
        val javaPsi = parent.javaPsi
        if (javaPsi is PsiModifierListOwner) {
          val info = PathAnnotationInfo.forModifierListOwner(javaPsi)
          if (info !is PathAnnotationInfo.Unspecified) {
            return info
          }
          nonAnnotatedTargets.add(javaPsi)
        }
      }

      // Check if the expression is passed to a Path constructor or factory method
      if (isPassedToMultiRoutingMethod(expression)) {
        return PathAnnotationInfo.MultiRouting
      }

      return PathAnnotationInfo.Unspecified
    }

    private fun isPassedToMultiRoutingMethod(expression: UExpression): Boolean {
      // Check if the expression is passed to a Path constructor or factory method
      val parent = expression.uastParent
      if (parent is UCallExpression) {
        val method = parent.resolve() ?: return false
        if (isPathConstructorOrFactory(method)) {
          return true
        }
      }
      return false
    }

    private fun getParameterForArgument(method: PsiElement, index: Int): PsiModifierListOwner? {
      if (method is com.intellij.psi.PsiMethod) {
        val parameters = method.parameterList.parameters
        if (index < parameters.size) {
          return parameters[index]
        }
      }
      return null
    }

    private fun isPathType(parameter: PsiModifierListOwner): Boolean {
      if (parameter is com.intellij.psi.PsiParameter) {
        val type = parameter.type
        return type.canonicalText == "java.nio.file.Path"
      }
      return false
    }
  }
}