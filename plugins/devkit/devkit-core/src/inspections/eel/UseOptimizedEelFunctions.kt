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
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.getQualifiedChain
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.getUastParentOfType

private val OPTIMIZED_METHOD_NAMES = setOf("deleteRecursively", "readAllBytes", "readString", "write", "writeString")

@ApiStatus.Internal
@VisibleForTesting
class UseOptimizedEelFunctions : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (ModuleUtilCore.findModuleForPsiElement(holder.file)?.let(PsiUtil::isPluginModule) != true) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val highlightType = if (IntelliJProjectUtil.isIntelliJPlatformProject(holder.project)) {
      ProblemHighlightType.WARNING
    }
    else {
      ProblemHighlightType.INFORMATION
    }
    val aliases = OptimizedEelFunctionCallNameProviders.forLanguage(holder.file.language)?.getAliases(holder.file, OPTIMIZED_METHOD_NAMES).orEmpty()
    val candidateNames = OPTIMIZED_METHOD_NAMES + aliases
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element.firstChild != null || element.text !in candidateNames) return

        val node = element.getUastParentOfType<UCallExpression>() ?: return
        val methodName = node.methodName ?: return
        if (methodName !in OPTIMIZED_METHOD_NAMES) return

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
          val method = node.resolve() ?: return
          val containingClass = method.containingClass?.qualifiedName ?: return
          val actualMethodName = method.name
          "$containingClass.$actualMethodName"
        }

        handleMethod(holder, node, fqn, highlightType)
      }
    }
  }

  private fun handleMethod(holder: ProblemsHolder, node: UCallExpression, fqn: String, highlightType: ProblemHighlightType) {
    val replacement = when {
      fqn == "java.nio.file.Files.readAllBytes" ->
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFiles", "readAllBytes")
      fqn == "java.nio.file.Files.readString" ->
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFiles", "readString")
      fqn == "java.nio.file.Files.write" && isByteArrayWrite(node) ->
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFiles", "write")
      fqn == "java.nio.file.Files.writeString" ->
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFiles", "writeString")
      (
        fqn == "com.intellij.openapi.util.io.NioFiles.deleteRecursively" ||
        fqn == "com.intellij.openapi.util.io.FileUtilRt.deleteRecursively"
      ) &&
      node.valueArgumentCount == 1 ->
        ReplaceWithEelFunction(holder.project, "com.intellij.platform.eel.fs.EelFileUtils", "deleteRecursively")
      else -> return
    }
    val methodPsi = node.methodIdentifier?.sourcePsi ?: return
    holder.registerProblem(
      methodPsi,
      DevKitBundle.message("inspection.message.works.ineffectively.with.remote.eel", methodPsi.text),
      highlightType,
      replacement,
    )
  }

  private fun isByteArrayWrite(node: UCallExpression): Boolean {
    val secondParameterType = node.resolve()?.parameterList?.parameters?.getOrNull(1)?.type as? PsiArrayType ?: return false
    return secondParameterType.componentType == PsiTypes.byteType()
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
