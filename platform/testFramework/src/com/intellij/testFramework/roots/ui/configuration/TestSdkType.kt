// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.roots.ui.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import org.jdom.Element
import java.io.File
import java.nio.file.Path

interface TestSdkType : JavaSdkType, SdkTypeId {
  companion object : SdkType("test-type"), TestSdkType {
    override fun getPresentableName(): String = name
    override fun isValidSdkHome(path: String): Boolean = true
    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String = TestSdkGenerator.findTestSdk(sdkHome)!!.name
    override fun suggestHomePath(path: Path): String? = null
    override fun suggestHomePaths(project: Project?): Collection<String> =TestSdkGenerator.getAllTestSdks().map { it.homePath!! }
    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? = null
    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}
    override fun getBinPath(sdk: Sdk): String = File(sdk.homePath, "bin").path
    override fun getToolsPath(sdk: Sdk): String = File(sdk.homePath, "lib/tools.jar").path
    override fun getVMExecutablePath(sdk: Sdk): String = File(sdk.homePath, "bin/java").path
    override fun getVersionString(sdkHome: String): String? = TestSdkGenerator.findTestSdk(sdkHome)?.versionString
  }
}