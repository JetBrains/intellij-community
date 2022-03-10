// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyProjectWizardUtils")
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.CommonBundle
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.AFTER_PROPERTY_CHANGE
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.layout.*
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription
import org.jetbrains.plugins.groovy.config.loadLatestGroovyVersions
import javax.swing.SwingUtilities

const val GROOVY_SDK_FALLBACK_VERSION = "3.0.9"

private const val MAIN_FILE = "Main.groovy"
private const val MAIN_GROOVY_TEMPLATE = "template.groovy"

fun Row.groovySdkComboBox(context: WizardContext, property: ObservableMutableProperty<DistributionInfo?>): Cell<DistributionComboBox> {
  val groovyLibraryDescription = GroovyLibraryDescription()
  val comboBox = DistributionComboBox(context.project, object : FileChooserInfo {
    override val fileChooserTitle = GroovyBundle.message("dialog.title.select.groovy.sdk")
    override val fileChooserDescription: String? = null
    override val fileChooserDescriptor = groovyLibraryDescription.createFileChooserDescriptor()
    override val fileChooserMacroFilter = FileChooserInfo.DIRECTORY_PATH
  })
  comboBox.specifyLocationActionName = GroovyBundle.message("dialog.title.specify.groovy.sdk")
  comboBox.addLoadingItem()
  val pathToGroovyHome = groovyLibraryDescription.findPathToGroovyHome()
  if (pathToGroovyHome != null) {
    comboBox.addDistributionIfNotExists(LocalDistributionInfo(pathToGroovyHome.path))
  }
  loadLatestGroovyVersions(object : DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
    override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
      versions.sortedWith(::moveUnstableVersionToTheEnd)
        .map(::FrameworkLibraryDistributionInfo)
        .forEach(comboBox::addDistributionIfNotExists)
      comboBox.removeLoadingItem()
    }

    override fun onError(errorMessage: String) {
      comboBox.removeLoadingItem()
    }
  })
  return cell(comboBox)
    .applyToComponent { bindSelectedDistribution(property) }
    .validationRequestor(AFTER_PROPERTY_CHANGE(property))
    .validationOnInput { validateGroovySdk(property.get()) }
    .validationOnApply { validateGroovySdkWithDialog(property.get()) }
    .columns(COLUMNS_MEDIUM)
}

private fun ValidationInfoBuilder.validateGroovySdk(distribution: DistributionInfo?): ValidationInfo? {
  if (isBlankDistribution(distribution)) {
    return error(GroovyBundle.message("dialog.title.validation.path.should.not.be.empty"))
  }
  if (isInvalidSdk(distribution)) {
    return error(GroovyBundle.message("dialog.title.validation.path.does.not.contain.groovy.sdk"))
  }
  return null
}

private fun ValidationInfoBuilder.validateGroovySdkWithDialog(distribution: DistributionInfo?): ValidationInfo? {
  if (isBlankDistribution(distribution)) {
    if (Messages.showDialog(GroovyBundle.message("dialog.title.no.jdk.specified.prompt"),
                            GroovyBundle.message("dialog.title.no.jdk.specified.title"),
                            arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
                            Messages.getWarningIcon()) != Messages.YES) {
      return error(GroovyBundle.message("dialog.title.no.jdk.specified.error"))
    }
  }
  if (isInvalidSdk(distribution)) {
    if (Messages.showDialog(
        GroovyBundle.message(
          "dialog.title.validation.directory.you.specified.does.not.contain.groovy.sdk.do.you.want.to.create.project.with.this.configuration"),
        GroovyBundle.message("dialog.title.validation.invalid.sdk.specified.title"),
        arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
        Messages.getWarningIcon()) != Messages.YES) {
      return error(GroovyBundle.message("dialog.title.validation.invalid.sdk.specified.error"))
    }
  }
  return null
}

private fun isBlankDistribution(distribution: DistributionInfo?): Boolean {
  return distribution == null || (distribution is LocalDistributionInfo &&
                                  distribution.path == "")
}

private fun isInvalidSdk(distribution: DistributionInfo?): Boolean {
  return distribution == null || (distribution is LocalDistributionInfo &&
                                  GroovyConfigUtils.getInstance().getSDKVersionOrNull(distribution.path) == null)
}

fun Panel.addSampleCodeCheckbox(property: GraphProperty<Boolean>) {
  row {
    checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
      .bindSelected(property)
  }.topGap(TopGap.SMALL)
}


fun ModuleBuilder.createSampleGroovyCodeFile(project: Project, sourceDirectory: VirtualFile) {
  WriteCommandAction.runWriteCommandAction(project, GroovyBundle.message("new.project.wizard.groovy.creating.main.file"), null,
     Runnable {
       val fileTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(MAIN_GROOVY_TEMPLATE)
       val helloWorldFile = sourceDirectory.createChildData(this, MAIN_FILE)
       VfsUtil.saveText(helloWorldFile, fileTemplate.text)
     })
}

fun moveUnstableVersionToTheEnd(left: FrameworkLibraryVersion, right: FrameworkLibraryVersion): Int {
  val leftVersion = left.versionString
  val rightVersion = right.versionString
  val leftUnstable = GroovyConfigUtils.isUnstable(leftVersion)
  val rightUnstable = GroovyConfigUtils.isUnstable(rightVersion)
  return when {
    leftUnstable == rightUnstable -> -GroovyConfigUtils.compareSdkVersions(leftVersion, rightVersion)
    leftUnstable -> 1
    else -> -1
  }
}

class FrameworkLibraryDistributionInfo(val version: FrameworkLibraryVersion) : AbstractDistributionInfo() {
  override val name: String = version.versionString
  override val description: String? = null
}