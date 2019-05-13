// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.BuildNumber
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.idea.devkit.util.PsiUtil

/**
 * Inspection that warns plugin authors if they use IntelliJ Platform's APIs that aren't available
 * in some IDE versions specified as compatible with the plugin via the "since-until" constraints.
 *
 * Suppose a plugin specifies `183.1; 181.9` as compatibility range, but some IntelliJ Platform's API
 * in use appeared only in `183.5`. Then the plugin won't work in `[181.1, 181.5)`
 * and this inspection's goal is to prevent it.
 *
 * *Implementation details*.
 * Info on when APIs were first introduced is obtained from "available since" artifacts.
 * They are are built on the build server for each IDE build and uploaded to
 * [IntelliJ artifacts repository](https://www.jetbrains.com/intellij-repository/releases/),
 * containing external annotations [org.jetbrains.annotations.ApiStatus.AvailableSince]
 * where APIs' introduction versions are specified.
 */
class MissingRecentApiInspection : AbstractBaseJavaLocalInspectionTool() {

  companion object {
    val INSPECTION_SHORT_NAME = InspectionProfileEntry.getShortName(MissingRecentApiInspection::class.simpleName!!)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (PsiUtil.isIdeaProject(holder.project)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val module = ModuleUtil.findModuleForPsiElement(holder.file) ?: return PsiElementVisitor.EMPTY_VISITOR
    val targetedSinceUntilRanges = getTargetedSinceUntilRanges(module)
    if (targetedSinceUntilRanges.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return MissingRecentApiVisitor(holder, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, targetedSinceUntilRanges)
  }

  private fun getTargetedSinceUntilRanges(module: Module): List<SinceUntilRange> {
    return DevkitActionsUtil.getCandidatePluginModules(module)
      .mapNotNull { PluginModuleType.getPluginXml(it) }
      .mapNotNull { getSinceUntilRange(it) }
      .filterNot { it.sinceBuild == null }
  }

  private fun getSinceUntilRange(pluginXml: XmlFile): SinceUntilRange? {
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml) ?: return null
    val ideaVersion = ideaPlugin.rootElement.ideaVersion
    val sinceBuild = ideaVersion.sinceBuild.stringValue.orEmpty().let { BuildNumber.fromStringOrNull(it) }
    val untilBuild = ideaVersion.untilBuild.stringValue.orEmpty().let { BuildNumber.fromStringOrNull(it) }
    return SinceUntilRange(sinceBuild, untilBuild)
  }

}