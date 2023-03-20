// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.test.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.projectModel.FullJdk
import org.jetbrains.kotlin.projectModel.KotlinSdk
import org.jetbrains.kotlin.projectModel.MockJdk
import org.jetbrains.kotlin.projectModel.ResolveSdk
import org.jetbrains.kotlin.test.TestJdkKind
import java.io.File

fun HeavyPlatformTestCase.projectLibrary(
        libraryName: String = "TestLibrary",
        classesRoot: VirtualFile? = null,
        sourcesRoot: VirtualFile? = null,
        kind: PersistentLibraryKind<*>? = null
): LibraryEx {
    return runWriteAction {
        val modifiableModel = ProjectLibraryTable.getInstance(project).modifiableModel
        val library = try {
            modifiableModel.createLibrary(libraryName, kind) as LibraryEx
        } finally {
            modifiableModel.commit()
        }
        with(library.modifiableModel) {
            classesRoot?.let { addRoot(it, OrderRootType.CLASSES) }
            sourcesRoot?.let { addRoot(it, OrderRootType.SOURCES) }
            commit()
        }
        library
    }
}

fun moduleLibrary(
    module: Module,
    libraryName: String = "TestLibrary",
    classesRoot: VirtualFile? = null,
    sourcesRoot: VirtualFile? = null,
): LibraryEx {
    return runWriteAction {
        val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
        val moduleLibraryTable = modifiableModel.moduleLibraryTable
        val library = try {
            moduleLibraryTable.createLibrary(libraryName) as LibraryEx
        } finally {
            modifiableModel.commit()
        }

        with(library.modifiableModel) {
            classesRoot?.let { addRoot(it, OrderRootType.CLASSES) }
            sourcesRoot?.let { addRoot(it, OrderRootType.SOURCES) }
            commit()
        }

        library
    }
}

val File.jarRoot: VirtualFile
    get() {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this) ?: error("Cannot find file $this")
        return JarFileSystem.getInstance().getRootByLocal(virtualFile) ?: error("Can't find root by file $virtualFile")
    }

fun Module.addDependency(
        library: Library,
        dependencyScope: DependencyScope = DependencyScope.COMPILE,
        exported: Boolean = false
) = ModuleRootModificationUtil.addDependency(this, library, dependencyScope, exported)

fun Module.addDependency(sdk: ResolveSdk, testRootDisposable: Disposable) {
    when (sdk) {
        FullJdk -> ConfigLibraryUtil.configureSdk(this, PluginTestCaseBase.addJdk(testRootDisposable) {
            PluginTestCaseBase.jdk(TestJdkKind.FULL_JDK)
        })

        MockJdk -> ConfigLibraryUtil.configureSdk(this, PluginTestCaseBase.addJdk(testRootDisposable) {
            PluginTestCaseBase.jdk(TestJdkKind.MOCK_JDK)
        })

        KotlinSdk -> {
            KotlinSdkType.setUpIfNeeded(testRootDisposable)
            ConfigLibraryUtil.configureSdk(
                this,
                runReadAction { ProjectJdkTable.getInstance() }.findMostRecentSdkOfType(KotlinSdkType.INSTANCE)
                    ?: error("Kotlin SDK wasn't created")
            )
        }

        else -> error("Don't know how to set up SDK of type: ${sdk::class}")
    }
}