// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.util.Consumer
import org.jdom.Element
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.facet.KotlinBaseFacetBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import com.intellij.openapi.application.runWriteAction
import javax.swing.JComponent

class KotlinSdkType : SdkType("KotlinSDK") {
    companion object {
        @JvmField
        val INSTANCE = KotlinSdkType()

        @JvmOverloads
        fun setUpIfNeeded(disposable: Disposable? = null, checkIfNeeded: () -> Boolean = { true }) {
            val projectSdks: Array<Sdk> = ProjectJdkTable.getInstance().allJdks
            if (projectSdks.any { it.sdkType is KotlinSdkType }) return
            if (!checkIfNeeded()) return // do not create Kotlin SDK

            val newSdkName = SdkConfigurationUtil.createUniqueSdkName(INSTANCE, "", projectSdks.toList())
            val newJdk = ProjectJdkImpl(newSdkName, INSTANCE)
            INSTANCE.setupSdkPaths(newJdk)

            ApplicationManager.getApplication().invokeAndWait {
                runWriteAction {
                    if (ProjectJdkTable.getInstance().allJdks.any { it.sdkType is KotlinSdkType }) return@runWriteAction
                    if (disposable != null) {
                        ProjectJdkTable.getInstance().addJdk(newJdk, disposable)
                    } else {
                        ProjectJdkTable.getInstance().addJdk(newJdk)
                    }
                }
            }
        }
    }

    override fun getPresentableName() = KotlinBaseFacetBundle.message("framework.name.kotlin.sdk")

    override fun getIcon() = KotlinIcons.SMALL_LOGO

    override fun isValidSdkHome(path: String) = true

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String) = "Kotlin SDK"

    override fun suggestHomePath() = null

    override fun sdkHasValidPath(sdk: Sdk) = true

    override fun getVersionString(sdk: Sdk): String = KotlinPluginLayout.standaloneCompilerVersion.rawVersion

    override fun supportsCustomCreateUI() = true

    override fun showCustomCreateUI(sdkModel: SdkModel, parentComponent: JComponent, selectedSdk: Sdk?, sdkCreatedCallback: Consumer<in Sdk>) {
        sdkCreatedCallback.consume(createSdkWithUniqueName(sdkModel.sdks.toList()))
    }

    fun createSdkWithUniqueName(existingSdks: Collection<Sdk>): ProjectJdkImpl {
        val sdkName = suggestSdkName(SdkConfigurationUtil.createUniqueSdkName(this, "", existingSdks), "")
        return ProjectJdkImpl(sdkName, this)
    }

    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator) = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}

    override fun allowCreationByUser() = false
}