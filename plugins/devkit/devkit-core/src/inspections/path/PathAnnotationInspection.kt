// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.annotations.Filename
import com.intellij.platform.eel.annotations.LocalPath
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.util.ThreeState
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
                  AddMultiRoutingAnnotationFix()
                )
            }
            is PathAnnotationInfo.Unspecified -> {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                if (i == 0)
                  DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath")
                else
                  DevKitBundle.message("inspections.message.more.parameters.in.path.of.should.be.annotated.with.multiroutingfilesystempath.or.filename"),
                AddMultiRoutingAnnotationFix()
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
                AddMultiRoutingAnnotationFix()
              )
            }
            is PathAnnotationInfo.LocalPathInfo -> {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method"),
                AddMultiRoutingAnnotationFix()
              )
            }
            is PathAnnotationInfo.Unspecified -> {
              holder.registerProblem(
                arg.sourcePsi ?: sourcePsi,
                DevKitBundle.message("inspections.message.string.without.path.annotation.used.in.path.resolve.method"),
                com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING,
                AddMultiRoutingAnnotationFix()
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
          if (firstArgInfo !is PathAnnotationInfo.Native) {
            // Report error: first argument of FileSystem.getPath() should be annotated with @NativePath
            when (firstArgInfo) {
              is PathAnnotationInfo.MultiRouting -> {
                holder.registerProblem(
                  firstArg.sourcePsi ?: sourcePsi,
                  DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                  AddNativePathAnnotationFix()
                )
              }
              is PathAnnotationInfo.LocalPathInfo -> {
                holder.registerProblem(
                  firstArg.sourcePsi ?: sourcePsi,
                  DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                  AddNativePathAnnotationFix()
                )
              }
              is PathAnnotationInfo.FilenameInfo -> {
                holder.registerProblem(
                  firstArg.sourcePsi ?: sourcePsi,
                  DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                  AddNativePathAnnotationFix()
                )
              }
              is PathAnnotationInfo.Unspecified -> {
                holder.registerProblem(
                  firstArg.sourcePsi ?: sourcePsi,
                  DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
                  AddNativePathAnnotationFix()
                )
              }
              else -> {
                // This should not happen, but we need to handle all cases
                holder.registerProblem(
                  firstArg.sourcePsi ?: sourcePsi,
                  DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath")
                )
              }
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
                    AddNativePathAnnotationFix()
                  )
                }
                is PathAnnotationInfo.LocalPathInfo -> {
                  holder.registerProblem(
                    arg.sourcePsi ?: sourcePsi,
                    DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename"),
                    AddNativePathAnnotationFix()
                  )
                }
                is PathAnnotationInfo.Unspecified -> {
                  holder.registerProblem(
                    arg.sourcePsi ?: sourcePsi,
                    DevKitBundle.message("inspections.message.more.parameters.in.fs.getpath.should.be.annotated.with.nativepath.or.filename"),
                    AddNativePathAnnotationFix()
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

        when {
          // Methods expecting @MultiRoutingFileSystemPath should not accept @NativePath values
          expectedInfo is PathAnnotationInfo.MultiRouting && actualInfo is PathAnnotationInfo.Native -> {
            // Report error: @NativePath string passed to method expecting @MultiRoutingFileSystemPath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              DevKitBundle.message("inspections.message.nativepath.passed.to.multiroutingfilesystempath.method.parameter"),
              AddMultiRoutingAnnotationFix()
            )
          }
          // Methods expecting @NativePath should not accept @MultiRoutingFileSystemPath values
          expectedInfo is PathAnnotationInfo.Native && actualInfo is PathAnnotationInfo.MultiRouting -> {
            // Report error: @MultiRoutingFileSystemPath string passed to method expecting @NativePath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              DevKitBundle.message("inspections.message.multiroutingfilesystempath.passed.to.nativepath.method.parameter"),
              AddNativePathAnnotationFix()
            )
          }
          // Methods expecting @NativePath should not accept @LocalPath values
          expectedInfo is PathAnnotationInfo.Native && actualInfo is PathAnnotationInfo.LocalPathInfo -> {
            // Report error: @LocalPath string passed to method expecting @NativePath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
              AddNativePathAnnotationFix()
            )
          }
          // Methods expecting @LocalPath should not accept @MultiRoutingFileSystemPath values
          expectedInfo is PathAnnotationInfo.LocalPathInfo && actualInfo is PathAnnotationInfo.MultiRouting -> {
            // Report error: @MultiRoutingFileSystemPath string passed to method expecting @LocalPath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              "A string annotated with @MultiRoutingFileSystemPath is passed to a method parameter annotated with @LocalPath",
              AddLocalPathAnnotationFix()
            )
          }
          // Methods expecting @LocalPath should not accept @NativePath values
          expectedInfo is PathAnnotationInfo.LocalPathInfo && actualInfo is PathAnnotationInfo.Native -> {
            // Report error: @NativePath string passed to method expecting @LocalPath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              "A string annotated with @NativePath is passed to a method parameter annotated with @LocalPath",
              AddLocalPathAnnotationFix()
            )
          }
          // Methods expecting @Filename should not accept @MultiRoutingFileSystemPath values
          expectedInfo is PathAnnotationInfo.FilenameInfo && actualInfo is PathAnnotationInfo.MultiRouting -> {
            // Report error: @MultiRoutingFileSystemPath string passed to method expecting @Filename
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              "A string annotated with @MultiRoutingFileSystemPath is passed to a method parameter annotated with @Filename",
              AddFilenameAnnotationFix()
            )
          }
          // Methods expecting @Filename should not accept @LocalPath values
          expectedInfo is PathAnnotationInfo.FilenameInfo && actualInfo is PathAnnotationInfo.LocalPathInfo -> {
            // Report error: @LocalPath string passed to method expecting @Filename
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              "A string annotated with @LocalPath is passed to a method parameter annotated with @Filename",
              AddFilenameAnnotationFix()
            )
          }
          // Methods expecting @Filename should not accept @NativePath values
          expectedInfo is PathAnnotationInfo.FilenameInfo && actualInfo is PathAnnotationInfo.Native -> {
            // Report error: @NativePath string passed to method expecting @Filename
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              "A string annotated with @NativePath is passed to a method parameter annotated with @Filename",
              AddFilenameAnnotationFix()
            )
          }
          // Methods expecting @MultiRoutingFileSystemPath should not accept unannotated values
          expectedInfo is PathAnnotationInfo.MultiRouting && actualInfo is PathAnnotationInfo.Unspecified -> {
            // Report error: Unannotated string passed to method expecting @MultiRoutingFileSystemPath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath"),
              AddMultiRoutingAnnotationFix()
            )
          }
          // Methods expecting @NativePath should not accept unannotated values
          expectedInfo is PathAnnotationInfo.Native && actualInfo is PathAnnotationInfo.Unspecified -> {
            // Report error: Unannotated string passed to method expecting @NativePath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
              AddNativePathAnnotationFix()
            )
          }
          // Methods expecting @LocalPath should not accept unannotated values
          expectedInfo is PathAnnotationInfo.LocalPathInfo && actualInfo is PathAnnotationInfo.Unspecified -> {
            // Report error: Unannotated string passed to method expecting @LocalPath
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              "String without path annotation used in method parameter annotated with @LocalPath",
              AddLocalPathAnnotationFix()
            )
          }
          // Methods expecting @Filename should not accept unannotated values
          expectedInfo is PathAnnotationInfo.FilenameInfo && actualInfo is PathAnnotationInfo.Unspecified -> {
            // Report error: Unannotated string passed to method expecting @Filename
            holder.registerProblem(
              arg.sourcePsi ?: sourcePsi,
              "String without path annotation used in method parameter annotated with @Filename",
              AddFilenameAnnotationFix()
            )
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
      val methodPsi = containingMethod.javaPsi ?: return true

      // Check if the method has a return type with a path annotation
      val expectedInfo = PathAnnotationInfo.forModifierListOwner(methodPsi)
      if (expectedInfo is PathAnnotationInfo.Unspecified) {
        // If the method doesn't have a path annotation, don't register any problems
        return true
      }

      // Check if the return value has a path annotation
      val actualInfo = PathAnnotationInfo.forExpression(returnValue)

      // Check if the return value matches the expected path annotation type
      when {
        // Methods returning @MultiRoutingFileSystemPath should not return @NativePath values
        expectedInfo is PathAnnotationInfo.MultiRouting && actualInfo is PathAnnotationInfo.Native -> {
          holder.registerProblem(
            returnValue.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspections.message.nativepath.passed.to.multiroutingfilesystempath.method.parameter"),
            AddMultiRoutingAnnotationFix()
          )
        }
        // Methods returning @NativePath should not return @MultiRoutingFileSystemPath values
        expectedInfo is PathAnnotationInfo.Native && actualInfo is PathAnnotationInfo.MultiRouting -> {
          holder.registerProblem(
            returnValue.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspections.message.multiroutingfilesystempath.passed.to.nativepath.method.parameter"),
            AddNativePathAnnotationFix()
          )
        }
        // Methods returning @NativePath should not return @LocalPath values
        expectedInfo is PathAnnotationInfo.Native && actualInfo is PathAnnotationInfo.LocalPathInfo -> {
          holder.registerProblem(
            returnValue.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
            AddNativePathAnnotationFix()
          )
        }
        // Methods returning @MultiRoutingFileSystemPath should not return unannotated values
        expectedInfo is PathAnnotationInfo.MultiRouting && actualInfo is PathAnnotationInfo.Unspecified -> {
          holder.registerProblem(
            returnValue.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspections.message.first.argument.path.of.should.be.annotated.with.multiroutingfilesystempath"),
            AddMultiRoutingAnnotationFix()
          )
        }
        // Methods returning @NativePath should not return unannotated values
        expectedInfo is PathAnnotationInfo.Native && actualInfo is PathAnnotationInfo.Unspecified -> {
          holder.registerProblem(
            returnValue.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspections.message.first.argument.fs.getpath.should.be.annotated.with.nativepath"),
            AddNativePathAnnotationFix()
          )
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
            AddMultiRoutingAnnotationFix()
          )
        }
      }
      else if (expectedInfo is PathAnnotationInfo.Native) {
        // Check if the string literal is used in a context that expects @NativePath
        if (nonAnnotatedTargets.isNotEmpty()) {
          holder.registerProblem(
            sourcePsi,
            DevKitBundle.message("inspections.message.nativepath.expected"),
            AddNativePathAnnotationFix()
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

    private fun isPathConstructorOrFactory(method: PsiElement): Boolean {
      val methods = mapOf(
        "java.nio.file.Path" to setOf("of", "get"),
        "java.nio.file.Paths" to setOf("get"),
      )
      // Check if the method is a Path constructor or factory method like Path.of()
      if (method is PsiModifierListOwner) {
        val containingClass = (method as? com.intellij.psi.PsiMember)?.containingClass
        if (containingClass != null) {
          return methods[containingClass.qualifiedName]?.contains(method.name) == true
        }
      }
      return false
    }

    private fun isPathResolveMethod(method: PsiElement): Boolean {
      // Check if the method is Path.resolve() or other similar methods
      if (method is com.intellij.psi.PsiMethod) {
        val containingClass = method.containingClass
        if (containingClass != null && containingClass.qualifiedName == "java.nio.file.Path") {
          // List of Path methods that can take either String or Path parameters
          val pathMethods = setOf("resolve", "resolveSibling", "startsWith", "endsWith")
          return pathMethods.contains(method.name)
        }
      }
      return false
    }

    private fun isFileSystemGetPathMethod(method: PsiElement): Boolean {
      // Check if the method is FileSystem.getPath()
      if (method is com.intellij.psi.PsiMethod) {
        val containingClass = method.containingClass
        if (containingClass != null && containingClass.qualifiedName == "java.nio.file.FileSystem") {
          return method.name == "getPath"
        }
      }
      return false
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

  /**
   * Contains information about path annotation status.
   */
  private sealed class PathAnnotationInfo {
    abstract fun getPathAnnotationStatus(): ThreeState

    object MultiRouting : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.YES
    }

    object Native : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.YES
    }

    object LocalPathInfo : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.YES
    }

    object FilenameInfo : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.YES
    }

    object Unspecified : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.UNSURE
    }

    companion object {
      /**
       * Checks if the given string is a valid filename (no forward or backward slashes).
       */
      fun isValidFilename(str: String): Boolean {
        return !str.contains('/') && !str.contains('\\')
      }

      fun forExpression(expression: UExpression): PathAnnotationInfo {
        val callExpression = expression.getUCallExpression(searchLimit = 1)
        if (callExpression != null) {
          // Check if the expression is a call to Path.toString()
          val method = callExpression.resolve()
          if (method is com.intellij.psi.PsiMethod) {
            val containingClass = method.containingClass
            if (containingClass != null && containingClass.qualifiedName == "java.nio.file.Path" && method.name == "toString") {
              return MultiRouting
            }
          }

          // Check if the argument is a call to System.getProperty("user.home")
          if (isSystemGetPropertyAbsolutePathResult(expression)) {
            return LocalPathInfo
          }
        }

        // Check if the expression has a path annotation
        val sourcePsi = expression.sourcePsi
        if (sourcePsi != null && sourcePsi is PsiModifierListOwner) {
          return forModifierListOwner(sourcePsi)
        }

        // Check if the expression is a reference to a variable with a path annotation
        if (expression is UReferenceExpression) {
          val resolved = expression.resolve()
          if (resolved is PsiModifierListOwner) {
            val info = forModifierListOwner(resolved)
            if (info !is Unspecified) {
              return info
            }

            // Check if the reference is to a variable with a string constant initializer that denotes a filename
            if (resolved is com.intellij.psi.PsiVariable) {
              val initializer = resolved.initializer
              if (initializer != null) {
                // Check if the initializer is a call to Path.toString()
                if (initializer is com.intellij.psi.PsiMethodCallExpression) {
                  val methodExpression = initializer.methodExpression
                  val qualifierExpression = methodExpression.qualifierExpression
                  val methodName = methodExpression.referenceName

                  if (methodName == "toString" && qualifierExpression != null) {
                    val qualifierType = qualifierExpression.type
                    if (qualifierType != null && qualifierType.canonicalText == "java.nio.file.Path") {
                      return MultiRouting
                    }
                  }
                }

                // Check if the initializer is a call to System.getProperty("user.home")
                if (initializer is com.intellij.psi.PsiMethodCallExpression) {
                  val methodExpression = initializer.methodExpression
                  val qualifierExpression = methodExpression.qualifierExpression
                  val methodName = methodExpression.referenceName

                  if (methodName == "getProperty" && qualifierExpression != null) {
                    val qualifierText = qualifierExpression.text
                    if (qualifierText == "System") {
                      val args = initializer.argumentList.expressions
                      if (args.size >= 1) {
                        val arg = args[0]
                        if (arg is com.intellij.psi.PsiLiteralExpression) {
                          val value = arg.value
                          if (value is String && isSystemPropertyAbsolutePathValue(value)) {
                            return LocalPathInfo
                          }
                        }
                      }
                    }
                  }
                }

                // Try to evaluate the initializer as a string constant
                val constantValue = com.intellij.psi.JavaPsiFacade.getInstance(resolved.project)
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
   * Quick fix to add @MultiRoutingFileSystemPath annotation.
   */
  private class AddMultiRoutingAnnotationFix() : LocalQuickFix {
    override fun getFamilyName(): String = DevKitBundle.message("inspections.intention.family.name.add.multiroutingfilesystempath.annotation")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement

      // Try to find the variable reference inside the element
      val targetElement = findVariableToAnnotate(element)

      if (targetElement != null) {
        val annotationOwner = targetElement.modifierList
        if (annotationOwner != null) {
          val annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(
            MultiRoutingFileSystemPath::class.java.name,
            emptyArray(),
            annotationOwner
          )

          // Shorten class references to add imports
          if (annotation != null) {
            // Get the containing file and shorten all class references in it
            val containingFile = annotation.containingFile
            if (containingFile != null) {
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(containingFile)
            }
          }
        }
      }
    }
  }

  /**
   * Quick fix to add @NativePath annotation.
   */
  private class AddNativePathAnnotationFix() : LocalQuickFix {
    override fun getFamilyName(): String = DevKitBundle.message("inspections.intention.family.name.add.nativepath.annotation")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement

      // Try to find the variable reference inside the element
      val targetElement = findVariableToAnnotate(element)

      if (targetElement != null) {
        val annotationOwner = targetElement.modifierList
        if (annotationOwner != null) {
          val annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(
            NativePath::class.java.name,
            emptyArray(),
            annotationOwner
          )

          // Shorten class references to add imports
          if (annotation != null) {
            // Get the containing file and shorten all class references in it
            val containingFile = annotation.containingFile
            if (containingFile != null) {
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(containingFile)
            }
          }
        }
      }
    }
  }

  /**
   * Quick fix to add @LocalPath annotation.
   */
  private class AddLocalPathAnnotationFix() : LocalQuickFix {
    override fun getFamilyName(): String = "Add @LocalPath annotation"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement

      // Try to find the variable reference inside the element
      val targetElement = findVariableToAnnotate(element)

      if (targetElement != null) {
        val annotationOwner = targetElement.modifierList
        if (annotationOwner != null) {
          val annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(
            LocalPath::class.java.name,
            emptyArray(),
            annotationOwner
          )

          // Shorten class references to add imports
          if (annotation != null) {
            // Get the containing file and shorten all class references in it
            val containingFile = annotation.containingFile
            if (containingFile != null) {
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(containingFile)
            }
          }
        }
      }
    }
  }

  /**
   * Quick fix to add @Filename annotation.
   */
  private class AddFilenameAnnotationFix() : LocalQuickFix {
    override fun getFamilyName(): String = "Add @Filename annotation"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement

      // Try to find the variable reference inside the element
      val targetElement = findVariableToAnnotate(element)

      if (targetElement != null) {
        val annotationOwner = targetElement.modifierList
        if (annotationOwner != null) {
          val annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(
            Filename::class.java.name,
            emptyArray(),
            annotationOwner
          )

          // Shorten class references to add imports
          if (annotation != null) {
            // Get the containing file and shorten all class references in it
            val containingFile = annotation.containingFile
            if (containingFile != null) {
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(containingFile)
            }
          }
        }
      }
    }
  }

}

private fun findVariableToAnnotate(element: PsiElement): PsiModifierListOwner? {
  // If the element is already a variable, return it
  if (element is PsiModifierListOwner) {
    return element
  }

  // If the element is a method call, try to find the variable reference inside it
  if (element is com.intellij.psi.PsiMethodCallExpression) {
    val argumentList = element.argumentList
    if (argumentList.expressionCount > 0) {
      val firstArg = argumentList.expressions[0]
      if (firstArg is com.intellij.psi.PsiReferenceExpression) {
        val resolved = firstArg.resolve()
        if (resolved is PsiModifierListOwner) {
          return resolved
        }
      }
    }
  }

  // If the element is a reference expression, resolve it to find the variable declaration
  if (element is com.intellij.psi.PsiReferenceExpression) {
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
  if (parent is com.intellij.psi.PsiMethodCallExpression) {
    val argumentList = parent.argumentList
    if (argumentList.expressionCount > 0) {
      val firstArg = argumentList.expressions[0]
      if (firstArg is com.intellij.psi.PsiReferenceExpression) {
        val resolved = firstArg.resolve()
        if (resolved is PsiModifierListOwner) {
          return resolved
        }
      }
    }
  }

  return null
}

/**
 * Checks if the expression is a call to System.getProperty("user.home").
 */
private fun isSystemGetPropertyAbsolutePathResult(expression: UExpression): Boolean {
  val callExpression = expression.getUCallExpression(searchLimit = 1) ?: return false
  val method = callExpression.resolve()
  if (method is com.intellij.psi.PsiMethod) {
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

private fun isSystemPropertyAbsolutePathValue(propertyName: String): Boolean =
  setOf("java.home", "user.home", "user.dir", "java.io.tmpdir").contains(propertyName)
