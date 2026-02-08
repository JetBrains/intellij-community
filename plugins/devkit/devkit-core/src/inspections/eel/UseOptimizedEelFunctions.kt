// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.getQualifiedChain
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.getUastParentOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

@ApiStatus.Internal
@VisibleForTesting
class UseOptimizedEelFunctions : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (ModuleUtilCore.findModuleForPsiElement(holder.file)?.let(PsiUtil::isPluginModule) != true) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return UastVisitorAdapter(object : AbstractUastNonRecursiveVisitor() {
      private val visitedCalls = hashSetOf<UExpression>()

      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
        if (!visitedCalls.add(node)) {
          return true
        }

        val selector = node.selector
        if (selector is UCallExpression) {
          return visitCallExpression(selector)
        }

        return false
      }

      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (!visitedCalls.add(node)) {
          return true
        }

        val methodName = node.methodName ?: return true

        val receiverName = node.receiver
          ?.getQualifiedChain()
          ?.lastOrNull()
          ?.let { it as? UReferenceExpression }
          ?.getQualifiedName()

        val fqn = if (receiverName != null) {
          "$receiverName.$methodName"
        }
        else {
          // Handle static imports and aliases: resolve the method to get its fully qualified name
          val method = node.resolve() ?: return true
          val containingClass = method.containingClass?.qualifiedName ?: return true
          val actualMethodName = method.name
          "$containingClass.$actualMethodName"
        }

        handleMethod(holder, node, fqn)

        return true
      }
    }, true)
  }

  private fun handleMethod(holder: ProblemsHolder, node: UCallExpression, fqn: String) {
    val methodPsi = node.methodIdentifier?.sourcePsi ?: return
    if (fqn == "java.nio.file.Files.readAllBytes") {
      holder.registerProblem(
        methodPsi, DevKitBundle.message("inspection.message.works.ineffectively.with.remote.eel"), ProblemHighlightType.WARNING,
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFiles", "readAllBytes"),
      )
    }
    if (fqn == "java.nio.file.Files.readString") {
      holder.registerProblem(
        methodPsi, DevKitBundle.message("inspection.message.works.ineffectively.with.remote.eel"), ProblemHighlightType.WARNING,
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFiles", "readString"),
      )
    }
    if (
      (
        fqn == "com.intellij.openapi.util.io.NioFiles.deleteRecursively" ||
        fqn == "com.intellij.openapi.util.io.FileUtilRt.deleteRecursively"
      ) &&
      node.valueArgumentCount == 1
    ) {
      holder.registerProblem(
        methodPsi, DevKitBundle.message("inspection.message.works.ineffectively.with.remote.eel"), ProblemHighlightType.WARNING,
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFileUtils", "deleteRecursively"),
      )
    }
  }

  private class ReplaceWithEelFunction(
    project: Project,
    private val receiver: String,
    private val method: String,
  ) : LocalQuickFix {
    private val receiverModuleName: String? =
      JavaPsiFacade.getInstance(project)
        .findClass(receiver, GlobalSearchScope.allScope(project))
        ?.let(ModuleUtilCore::findModuleForPsiElement)
        ?.name

    override fun getFamilyName(): @IntentionFamilyName String =
      DevKitBundle.message("intention.family.name.replace.it.with.eel.api")

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      doTheJob(project, previewDescriptor, preview = true)
      return IntentionPreviewInfo.DIFF
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      doTheJob(project, descriptor, preview = false)
    }

    private fun doTheJob(project: Project, descriptor: ProblemDescriptor, preview: Boolean) {
      val methodCall = descriptor.psiElement.getUastParentOfType<UCallExpression>() ?: return
      val call = methodCall.uastParent as? UQualifiedReferenceExpression ?: methodCall
      val factory = call.getUastElementFactory(project) ?: return
      val context = call.sourcePsi ?: return

      if (!preview) {
        ensureModuleAdded(project, context)
      }

      val newCall = factory.createCallExpression(
        receiver = factory.createQualifiedReference(receiver, context),
        methodName = method,
        parameters = methodCall.valueArguments,
        expectedReturnType = null,
        kind = methodCall.kind,
        context = context,
      ) ?: return

      call.replace(newCall)
    }

    private fun ensureModuleAdded(project: Project, context: PsiElement) {
      if (receiverModuleName == null) return
      val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return
      val model = ModuleRootManager.getInstance(module).modifiableModel

      try {
        val receiverModule = ModuleManager.getInstance(project).findModuleByName(receiverModuleName) ?: return
        if (module != receiverModule && receiverModule !in model.moduleDependencies) {
          model.addModuleEntries(listOf(receiverModule), DependencyScope.COMPILE, false)
          model.commit()
        }
      }
      finally {
        if (!model.isDisposed && !model.isChanged) {
          model.dispose()
        }
      }
    }
  }
}