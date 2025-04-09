// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import com.intellij.util.ThreeState
import org.jetbrains.annotations.NotNull
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Inspection that checks for proper usage of path annotations ([MultiRoutingFileSystemPath] and [NativePath]).
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
      arrayOf(UCallExpression::class.java, UInjectionHost::class.java)
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
        // Check if the argument is annotated with @NativePath
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
          val arg = arguments[0]
          val argInfo = PathAnnotationInfo.forExpression(arg)
          if (argInfo is PathAnnotationInfo.Native) {
            // Report error: @NativePath string used in Path constructor or factory method
            holder.registerProblem(
              sourcePsi,
              DevKitBundle.message("inspections.message.nativepath.should.not.be.used.directly.constructing.path"),
              AddMultiRoutingAnnotationFix(argInfo.getAnnotationCandidate())
            )
          } else if (argInfo is PathAnnotationInfo.Unspecified) {
            // Report normal warning: non-annotated string used in Path constructor or factory method
            holder.registerProblem(
              sourcePsi,
              "String without path annotation is used in Path constructor or factory method",
              AddMultiRoutingAnnotationFix(argInfo.getAnnotationCandidate())
            )
          }
        }
      }

      // Check if the method is Path.resolve()
      if (isPathResolveMethod(target)) {
        // Check if the argument is annotated with @MultiRoutingFileSystemPath
        val arguments = node.valueArguments
        if (arguments.isNotEmpty()) {
          val arg = arguments[0]
          val argInfo = PathAnnotationInfo.forExpression(arg)
          if (argInfo is PathAnnotationInfo.Unspecified) {
            // Report weak warning: non-annotated string used in Path.resolve()
            holder.registerProblem(
              sourcePsi,
              "String without path annotation is used in Path.resolve() method",
              com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING,
              AddMultiRoutingAnnotationFix(argInfo.getAnnotationCandidate())
            )
            return true
          }
        }
      }

      // Check if the method expects a specific path annotation
      for ((index, arg) in node.valueArguments.withIndex()) {
        val parameter = getParameterForArgument(target, index) ?: continue
        val expectedInfo = PathAnnotationInfo.forModifierListOwner(parameter)
        val actualInfo = PathAnnotationInfo.forExpression(arg)

        if (expectedInfo is PathAnnotationInfo.MultiRouting && actualInfo is PathAnnotationInfo.Native) {
          // Report error: @NativePath string passed to method expecting @MultiRoutingFileSystemPath
          holder.registerProblem(
            arg.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspections.message.nativepath.passed.to.multiroutingfilesystempath.method.parameter"),
            AddMultiRoutingAnnotationFix(actualInfo.getAnnotationCandidate())
          )
        }
        else if (expectedInfo is PathAnnotationInfo.Native && actualInfo is PathAnnotationInfo.MultiRouting) {
          // Report error: @MultiRoutingFileSystemPath string passed to method expecting @NativePath
          holder.registerProblem(
            arg.sourcePsi ?: sourcePsi,
            DevKitBundle.message("inspections.message.multiroutingfilesystempath.passed.to.nativepath.method.parameter"),
            AddNativePathAnnotationFix(actualInfo.getAnnotationCandidate())
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

      val nonAnnotatedTargets = mutableSetOf<PsiModifierListOwner>()
      val expectedInfo = getExpectedPathAnnotationInfo(expression, nonAnnotatedTargets)

      if (expectedInfo is PathAnnotationInfo.MultiRouting) {
        // Check if the string literal is used in a context that expects @MultiRoutingFileSystemPath
        val fixes = mutableListOf<LocalQuickFix>()
        for (target in nonAnnotatedTargets) {
          if (target is PsiModifierListOwner) {
            fixes.add(AddMultiRoutingAnnotationFix(target))
          }
        }
        if (fixes.isNotEmpty()) {
          holder.registerProblem(
            sourcePsi,
            DevKitBundle.message("inspections.message.multiroutingfilesystempath.expected"),
            *fixes.toTypedArray()
          )
        }
      }
      else if (expectedInfo is PathAnnotationInfo.Native) {
        // Check if the string literal is used in a context that expects @NativePath
        val fixes = mutableListOf<LocalQuickFix>()
        for (target in nonAnnotatedTargets) {
          if (target is PsiModifierListOwner) {
            fixes.add(AddNativePathAnnotationFix(target))
          }
        }
        if (fixes.isNotEmpty()) {
          holder.registerProblem(
            sourcePsi,
            DevKitBundle.message("inspections.message.nativepath.expected"),
            *fixes.toTypedArray()
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
        val method = parent.resolve() ?: return PathAnnotationInfo.Unspecified(null)
        val index = parent.valueArguments.indexOf(expression)
        if (index >= 0) {
          val parameter = getParameterForArgument(method, index) ?: return PathAnnotationInfo.Unspecified(null)
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
      if (isPassedToMultiRoutingMethod(expression, nonAnnotatedTargets)) {
        return PathAnnotationInfo.MultiRouting()
      }

      // Check if the expression is passed to a method that expects a native path
      if (isPassedToNativePathMethod(expression, nonAnnotatedTargets)) {
        return PathAnnotationInfo.Native()
      }

      return PathAnnotationInfo.Unspecified(null)
    }

    private fun isPathConstructorOrFactory(method: PsiElement): Boolean {
      // Check if the method is a Path constructor or factory method like Path.of()
      if (method is PsiModifierListOwner) {
        val containingClass = (method as? com.intellij.psi.PsiMember)?.containingClass
        if (containingClass != null) {
          val qualifiedName = containingClass.qualifiedName
          return qualifiedName == "java.nio.file.Path" || qualifiedName == "java.nio.file.Paths"
        }
      }
      return false
    }

    private fun isPathResolveMethod(method: PsiElement): Boolean {
      // Check if the method is Path.resolve()
      if (method is com.intellij.psi.PsiMethod) {
        val containingClass = method.containingClass
        if (containingClass != null && containingClass.qualifiedName == "java.nio.file.Path") {
          return method.name == "resolve"
        }
      }
      return false
    }

    private fun isPassedToMultiRoutingMethod(
      expression: UExpression,
      nonAnnotatedTargets: MutableSet<PsiModifierListOwner>,
    ): Boolean {
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

    private fun isPassedToPathResolveMethod(
      expression: UExpression,
    ): Boolean {
      // Check if the expression is passed to Path.resolve()
      val parent = expression.uastParent
      if (parent is UCallExpression) {
        val method = parent.resolve() ?: return false
        if (isPathResolveMethod(method)) {
          return true
        }
      }
      return false
    }

    private fun isPassedToNativePathMethod(
      expression: UExpression,
      nonAnnotatedTargets: MutableSet<PsiModifierListOwner>,
    ): Boolean {
      // Check if the expression is passed to a method that expects a native path
      // This would be specific to your codebase, but could include methods like:
      // - Docker container path methods
      // - WSL path methods
      // For now, we'll just return false as a placeholder
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
  }

  /**
   * Contains information about path annotation status.
   */
  sealed class PathAnnotationInfo {
    abstract fun getPathAnnotationStatus(): ThreeState

    class MultiRouting(private val annotationCandidate: PsiModifierListOwner? = null) : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.YES

      fun getAnnotationCandidate(): PsiModifierListOwner? = annotationCandidate
    }

    class Native(private val annotationCandidate: PsiModifierListOwner? = null) : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.YES

      fun getAnnotationCandidate(): PsiModifierListOwner? = annotationCandidate
    }

    class Unspecified(private val annotationCandidate: PsiModifierListOwner?) : PathAnnotationInfo() {
      override fun getPathAnnotationStatus(): ThreeState = ThreeState.UNSURE
      fun getAnnotationCandidate(): PsiModifierListOwner? = annotationCandidate
    }

    companion object {
      fun forExpression(expression: UExpression): PathAnnotationInfo {
        // Check if the expression has a path annotation
        val sourcePsi = expression.sourcePsi
        if (sourcePsi != null && sourcePsi is PsiModifierListOwner) {
          return forModifierListOwner(sourcePsi)
        }

        // Check if the expression is a reference to a variable with a path annotation
        if (expression is UReferenceExpression) {
          val resolved = expression.resolve()
          if (resolved is PsiModifierListOwner) {
            return forModifierListOwner(resolved)
          }
        }

        return Unspecified(null)
      }

      fun forModifierListOwner(owner: PsiModifierListOwner): PathAnnotationInfo {
        // Check if the owner has a path annotation
        if (AnnotationUtil.isAnnotated(owner, MultiRoutingFileSystemPath::class.java.name, AnnotationUtil.CHECK_TYPE)) {
          return MultiRouting(owner)
        }
        if (AnnotationUtil.isAnnotated(owner, NativePath::class.java.name, AnnotationUtil.CHECK_TYPE)) {
          return Native(owner)
        }
        return Unspecified(owner)
      }
    }
  }

  /**
   * Quick fix to add @MultiRoutingFileSystemPath annotation.
   */
  private class AddMultiRoutingAnnotationFix(private val target: PsiModifierListOwner?) : LocalQuickFix {
    override fun getFamilyName(): String = DevKitBundle.message("inspections.intention.family.name.add.multiroutingfilesystempath.annotation")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      if (target != null) {
        val annotationOwner = target.modifierList
        if (annotationOwner != null) {
          AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(
            MultiRoutingFileSystemPath::class.java.name,
            emptyArray(),
            annotationOwner
          )
        }
      }
    }
  }

  /**
   * Quick fix to add @NativePath annotation.
   */
  private class AddNativePathAnnotationFix(private val target: PsiModifierListOwner?) : LocalQuickFix {
    override fun getFamilyName(): String = DevKitBundle.message("inspections.intention.family.name.add.nativepath.annotation")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      if (target != null) {
        val annotationOwner = target.modifierList
        if (annotationOwner != null) {
          AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(
            NativePath::class.java.name,
            emptyArray(),
            annotationOwner
          )
        }
      }
    }
  }
}
