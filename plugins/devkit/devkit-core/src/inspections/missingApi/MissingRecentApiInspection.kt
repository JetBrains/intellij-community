// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlFile
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil

internal val MISSING_API_INSPECTION_SHORT_NAME = InspectionProfileEntry.getShortName(MissingRecentApiInspection::class.java.simpleName)

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
 * They are built on the build server for each IDE build and uploaded to
 * [IntelliJ artifacts repository](https://www.jetbrains.com/intellij-repository/releases/),
 * containing external annotations [org.jetbrains.annotations.ApiStatus.AvailableSince]
 * where APIs' introduction versions are specified.
 */
@VisibleForTesting
@IntellijInternalApi
@Internal
class MissingRecentApiInspection : LocalInspectionTool() {

  /**
   * Actual "since" build constraint of the plugin under development.
   *
   * Along with [untilBuildString] it may be set manually if values in plugin.xml
   * differ from the actual values. For example, it is the case for gradle-intellij-plugin,
   * which allows to override "since" and "until" values during plugin build.
   */
  var sinceBuildString: String? = null

  /**
   * Actual "until" build constraint of the plugin under development.
   */
  var untilBuildString: String? = null

  private val sinceBuild: BuildNumber?
    get() = sinceBuildString?.let { BuildNumber.fromStringOrNull(it) }

  private val untilBuild: BuildNumber?
    get() = untilBuildString?.let { BuildNumber.fromStringOrNull(it) }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val project = holder.project
    val virtualFile = holder.file.virtualFile
    if (IntelliJProjectUtil.isIntelliJPlatformProject(project) || virtualFile != null && TestSourcesFilter.isTestSources(virtualFile, project)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val module = ModuleUtil.findModuleForPsiElement(holder.file) ?: return PsiElementVisitor.EMPTY_VISITOR
    val targetedSinceUntilRanges = getTargetedSinceUntilRanges(module)
    if (targetedSinceUntilRanges.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return ApiUsageUastVisitor.createPsiElementVisitor(
      MissingRecentApiUsageProcessor(holder, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, targetedSinceUntilRanges)
    )
  }

  override fun getOptionsPane(): OptPane = pane(
    group(DevKitBundle.message("inspections.missing.recent.api.settings.range"),
          string("sinceBuildString", DevKitBundle.message("inspections.missing.recent.api.settings.since"), BuildNumberValidator()),
          string("untilBuildString", DevKitBundle.message("inspections.missing.recent.api.settings.until"), BuildNumberValidator())))

  private fun getTargetedSinceUntilRanges(module: Module): List<SinceUntilRange> {
    if (sinceBuild == null && untilBuild == null) {
      return DevkitActionsUtil.getCandidatePluginModules(module)
        .mapNotNull { PluginModuleType.getPluginXml(it) }
        .mapNotNull { getSinceUntilRange(it) }
        .filterNot { it.sinceBuild == null }
    }
    return listOf(SinceUntilRange(sinceBuild, untilBuild))
  }

  private fun getSinceUntilRange(pluginXml: XmlFile): SinceUntilRange? {
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml) ?: return null
    val ideaVersion = ideaPlugin.ideaVersion
    val sinceBuild = ideaVersion.sinceBuild.value
    val untilBuild = ideaVersion.untilBuild.value
    return SinceUntilRange(sinceBuild, untilBuild)
  }

}