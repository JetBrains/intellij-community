// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.extractModule

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiPackage
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle

internal fun showOptionalDescriptorClassesAreNotSeparatedView(
  commonPackage: PsiPackage,
  classesFromOptionalDescriptor: Collection<PsiClass>,
  classesFromMainDescriptor: Collection<PsiClass>,
  configFileName: String,
  project: Project,
) {
  val usages =
    classesFromOptionalDescriptor.map { UsageInfo2UsageAdapter(UsageInfo(it, false))} +
    classesFromMainDescriptor.map { UsageInfo2UsageAdapter(UsageInfo(it, true)) }
  val packageName = commonPackage.qualifiedName
  val presentation = createPresentation(packageName, configFileName)
  val message = DevKitBundle.message("extract.optional.dependency.classes.are.not.separated.description", packageName, configFileName, "plugin.xml")
  val usageTarget = ClassesAreMixedUnderPackageUsageTarget(commonPackage, message)

  ApplicationManager.getApplication().invokeLater {
    if (!project.isDisposed) {
      UsageViewManager.getInstance(project).showUsages(arrayOf(usageTarget), usages.toTypedArray(), presentation)
    }
  }
}

private class ClassesAreMixedUnderPackageUsageTarget(
  psiPackage: PsiPackage,
  private val description: @Nls String,
) : PsiElement2UsageTargetAdapter(psiPackage, false) {
  override fun getPresentableText(): String {
    return description
  }

  override fun isValid(): Boolean {
    return false
  }
}

private fun createPresentation(packageName: String, configFileName: String): UsageViewPresentation {
  val title = DevKitBundle.message("extract.optional.dependency.classes.mixed.under.same.package.title")
  val toolWindowTitle = DevKitBundle.message("extract.optional.dependency.classes.mixed.in.same.package.toolwindow.title", packageName)
  return UsageViewPresentation().apply {
    tabName = title
    tabText = title
    toolwindowTitle = toolWindowTitle
    searchString = title
    codeUsagesString = DevKitBundle.message("extract.optional.dependency.classes.from.config.file.node.text", configFileName)
    nonCodeUsagesString = DevKitBundle.message("extract.optional.dependency.classes.from.config.file.node.text", "plugin.xml")
    targetsNodeText = DevKitBundle.message("extract.optional.dependency.classes.mixed.under.same.package.root.node")
    isMergeDupLinesAvailable = false
    isExcludeAvailable = false
  }
}
