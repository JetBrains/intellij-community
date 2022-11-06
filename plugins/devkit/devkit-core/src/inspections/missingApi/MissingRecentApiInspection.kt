// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.BuildNumber
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.idea.devkit.util.PsiUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

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
class MissingRecentApiInspection : LocalInspectionTool() {

  companion object {
    val INSPECTION_SHORT_NAME = InspectionProfileEntry.getShortName(MissingRecentApiInspection::class.java.simpleName)
  }

  /**
   * Actual "since" build constraint of the plugin under development.
   *
   * Along with [untilBuildString] it may be set manually if values in plugin.xml
   * differ from the actual values. For example, it is the case for gradle-intellij-plugin,
   * which allows to override "since" and "until" values during plugin build.
   */
  private var sinceBuildString: String? = null

  /**
   * Actual "until" build constraint of the plugin under development.
   */
  private var untilBuildString: String? = null

  private val sinceBuild: BuildNumber?
    get() = sinceBuildString?.let { BuildNumber.fromStringOrNull(it) }

  private val untilBuild: BuildNumber?
    get() = untilBuildString?.let { BuildNumber.fromStringOrNull(it) }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val project = holder.project
    val virtualFile = holder.file.virtualFile
    if (PsiUtil.isIdeaProject(project) || virtualFile != null && TestSourcesFilter.isTestSources(virtualFile, project)) {
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

  override fun createOptionsPanel(): JComponent {
    val emptyBuildNumber = BuildNumber.fromString("1.0")!!

    val sinceField = BuildNumberField("since", emptyBuildNumber)
    sinceBuild?.also { sinceField.value = it }
    sinceField.emptyText.text = DevKitBundle.message("inspections.missing.recent.api.settings.since.empty.text")
    sinceField.valueEditor.addListener { value ->
      sinceBuildString = value.takeIf { it != emptyBuildNumber }?.asString()
    }

    val untilField = BuildNumberField("until", untilBuild ?: emptyBuildNumber)
    untilField.emptyText.text = DevKitBundle.message("inspections.missing.recent.api.settings.until.empty.text")
    untilBuild?.also { untilField.value = it }
    untilField.valueEditor.addListener { value ->
      untilBuildString = value.takeIf { it != emptyBuildNumber }?.asString()
    }

    val formBuilder = FormBuilder.createFormBuilder()
      .addComponent(JBLabel(DevKitBundle.message("inspections.missing.recent.api.settings.range")))
      .addLabeledComponent(DevKitBundle.message("inspections.missing.recent.api.settings.since"), sinceField)
      .addLabeledComponent(DevKitBundle.message("inspections.missing.recent.api.settings.until"), untilField)

    val container = JPanel(BorderLayout())
    container.add(formBuilder.panel, BorderLayout.NORTH)
    return container
  }

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