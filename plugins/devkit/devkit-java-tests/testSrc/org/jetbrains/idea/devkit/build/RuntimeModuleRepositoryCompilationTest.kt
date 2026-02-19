// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build

import com.intellij.compiler.server.impl.BuildProcessCustomPluginsConfiguration
import com.intellij.devkit.runtimeModuleRepository.jps.build.RawDescriptorListBuilder
import com.intellij.devkit.runtimeModuleRepository.jps.build.checkRuntimeModuleRepository
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.Path

@TestApplication
class RuntimeModuleRepositoryCompilationTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()
  
  private lateinit var outputUrl: String
  
  @BeforeEach
  fun setUp() {
    projectModel.createModule("intellij.idea.community.main")
    val libraryName = "devkit.runtime.module.repository.jps"
    projectModel.addProjectLevelLibrary(libraryName) { model ->
      IntelliJProjectConfiguration.getProjectLibraryClassesRootUrls(libraryName).forEach { url ->
        model.addRoot(url, OrderRootType.CLASSES)
      }
    }
    
    outputUrl = "${projectModel.baseProjectDir.virtualFileRoot.url}/out"
    CompilerProjectExtension.getInstance(projectModel.project)!!.apply {
      runWriteActionAndWait {
        compilerOutputUrl = outputUrl
      }
    }
    BuildProcessCustomPluginsConfiguration.getInstance(projectModel.project).addProjectLibrary(libraryName)
  }

  @Test
  fun `create modules and add dependencies`() {
    val a = projectModel.createModule("a")
    buildAndCheck {
      descriptor("a", resourceDirName = null)
    }

    val b = projectModel.createModule("b")
    buildAndCheck {
      descriptor("a", resourceDirName = null)
      descriptor("b", resourceDirName = null)
    }
    
    ModuleRootModificationUtil.addDependency(a, b, DependencyScope.RUNTIME, false)
    buildAndCheck {
      descriptor("a", "b", resourceDirName = null)
      descriptor("b", resourceDirName = null)
    }
  }

  @Test
  fun `create and remove source roots`() {
    val a = projectModel.createModule("a")
    buildAndCheck {
      descriptor("a", resourceDirName = null)
    }
    
    val resourceRoot = projectModel.baseProjectDir.newVirtualFile("resources/a.txt").parent
    PsiTestUtil.addSourceRoot(a, resourceRoot, JavaResourceRootType.RESOURCE)
    buildAndCheck {
      descriptor("a")
    }

    PsiTestUtil.removeSourceRoot(a, resourceRoot)
    buildAndCheck {
      descriptor("a", resourceDirName = null)
    }
  }

  private fun buildAndCheck(expected: RawDescriptorListBuilder.() -> Unit) {
    val messages = CompilerTester(projectModel.project, projectModel.project.modules.asList(), projectModel.disposableRule.disposable, false).make()
    UsefulTestCase.assertEmpty(messages.filter { it.category in setOf(CompilerMessageCategory.ERROR, CompilerMessageCategory.WARNING) })
    checkRuntimeModuleRepository(Path(VfsUtil.urlToPath(outputUrl)), expected)
  }
}