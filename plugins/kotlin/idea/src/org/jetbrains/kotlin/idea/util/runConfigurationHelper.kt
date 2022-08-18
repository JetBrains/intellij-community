// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.util.SystemProperties
import kotlin.io.path.Path
import kotlin.io.path.exists

class JavaParametersBuilder(private val project: Project) {
    private var setDefaultSdk = false
    private var mainClassName: String? = null
    private var sdk: Sdk? = null

    fun build(): JavaParameters {
        return JavaParameters().apply {
            this.mainClass = mainClassName
            this.jdk = sdk
            if (jdk == null && setDefaultSdk) {
                this.jdk = SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome())
            }
            this.setShortenCommandLine(getDefaultShortenCommandLineMethod(jdk?.homePath), project)
        }
    }

    /**
     * This method is partially copied from IDEA sources but doesn't check presence of dynamic.classpath property
     * because we want to shorten command line for scratches and repl anyway
     * @see [com.intellij.execution.ShortenCommandLine.getDefaultMethod]
     */
    private fun getDefaultShortenCommandLineMethod(rootPath: String?): ShortenCommandLine {
        if (rootPath != null && JdkUtil.isModularRuntime(rootPath)) return ShortenCommandLine.ARGS_FILE
        return if (JdkUtil.useClasspathJar()) ShortenCommandLine.MANIFEST else ShortenCommandLine.CLASSPATH_FILE
    }

    fun withMainClassName(name: String?): JavaParametersBuilder {
        mainClassName = name
        return this
    }

    fun withSdkFrom(module: Module?, setDefault: Boolean = false): JavaParametersBuilder {
        if (module != null) {
            val sdk = module.let { ModuleRootManager.getInstance(module).sdk }
            if (sdk != null && sdk.sdkType is JavaSdkType && sdk.homePath?.let { Path(it).exists() } == true) {
                this.sdk = sdk
            }
        }
        setDefaultSdk = setDefault
        return this
    }

    companion object {
        fun getModuleDependencies(module: Module): List<String> =
            CompilerPaths.getOutputPaths(arrayOf(module)).toList() + OrderEnumerator.orderEntries(module).withoutSdk().recursively().pathsList.pathList
    }
}