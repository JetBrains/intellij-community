// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyProjectWizardUtils")
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.CommonBundle
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Groovy.logGroovyLibraryChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Groovy.logGroovyLibraryFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.roots.ui.distribution.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.application
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription
import org.jetbrains.plugins.groovy.config.loadLatestGroovyVersions
import javax.swing.SwingUtilities

const val GROOVY_SDK_FALLBACK_VERSION: String = "3.0.9"

fun <S> S.setupGroovySdkUI(builder: Panel) where S : NewProjectWizardStep, S : BuildSystemGroovyNewProjectWizardData {
  val groovyLibraryDescription = GroovyLibraryDescription()
  val comboBox = DistributionComboBox(context.project, object : FileChooserInfo {
    override val fileChooserDescriptor = groovyLibraryDescription.createFileChooserDescriptor().withTitle(GroovyBundle.message("dialog.title.select.groovy.sdk"))
    override val fileChooserMacroFilter = FileChooserInfo.DIRECTORY_PATH
  })
  comboBox.specifyLocationActionName = GroovyBundle.message("dialog.title.specify.groovy.sdk")
  comboBox.addLoadingItem()
  application.executeOnPooledThread {
    val pathToGroovyHome = groovyLibraryDescription.findPathToGroovyHome()
    if (pathToGroovyHome != null) {
      application.invokeLater {
        comboBox.addDistributionIfNotExists(LocalDistributionInfo(pathToGroovyHome.path))
      }
    }
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
  builder.row(GroovyBundle.message("label.groovy.sdk")) {
    cell(comboBox)
      .applyToComponent { bindSelectedDistribution(groovySdkProperty) }
      .validationRequestor(WHEN_PROPERTY_CHANGED(groovySdkProperty))
      .validationOnInput { validateGroovySdk(groovySdk) }
      .validationOnApply { validateGroovySdkWithDialog(groovySdk) }
      .columns(COLUMNS_MEDIUM)
      .whenItemSelectedFromUi { logGroovySdkChanged(groovySdk) }
      .onApply { logGroovySdkFinished(groovySdk) }
  }.bottomGap(BottomGap.SMALL)
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

fun NewProjectWizardStep.logGroovySdkChanged(sdk: DistributionInfo?) {
  val type = getGroovySdkType(sdk)
  val version = getGroovySdkVersion(sdk)
  logGroovyLibraryChanged(type, version)
}

fun NewProjectWizardStep.logGroovySdkFinished(sdk: DistributionInfo?) {
  val type = getGroovySdkType(sdk)
  val version = getGroovySdkVersion(sdk)
  logGroovyLibraryFinished(type, version)
}

private fun getGroovySdkType(sdk: DistributionInfo?): String {
  return when (sdk) {
    is FrameworkLibraryDistributionInfo -> NewProjectWizardConstants.GroovySdk.MAVEN
    is LocalDistributionInfo -> NewProjectWizardConstants.GroovySdk.LOCAL
    null -> NewProjectWizardConstants.GroovySdk.NONE
    else -> error("Unexpected distribution type: $sdk")
  }
}

private fun getGroovySdkVersion(sdk: DistributionInfo?): String? {
  return when (sdk) {
    is FrameworkLibraryDistributionInfo ->
      sdk.version.versionString

    is LocalDistributionInfo ->
      GroovyConfigUtils.getInstance().getSDKVersionOrNull(sdk.path)

    null -> null

    else -> error("Unexpected distribution type: $sdk")
  }
}

class FrameworkLibraryDistributionInfo(val version: FrameworkLibraryVersion) : AbstractDistributionInfo() {
  override val name: String = version.versionString
  override val description: String? = null
}
