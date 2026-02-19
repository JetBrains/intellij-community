// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import org.jetbrains.jps.builders.BuildResult
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.model.java.JavaSourceRootType

class RuntimeModuleRepositoryIncrementalBuildTest : RuntimeModuleRepositoryTestCase() {
  fun `test up to date`() {
    addModule("a", withTests = false)
    buildRepository().assertSuccessful()
    assertUpToDate()
  }

  fun `test add source root`() {
    val a = addModule("a", withTests = false, withSources = false)
    buildAndCheck {
      descriptor("a", resourceDirName = null)
    }
    
    a.addSourceRoot(getUrl("a/src"), JavaSourceRootType.SOURCE)
    buildAndCheck {
      descriptor("a")
    }
    assertUpToDate()
  }
  
  fun `test add source file`() {
    addModule("a", withTests = false)
    buildAndCheck {
      descriptor("a")
    }
    
    createFile("a/src/A.java", "class A {}")
    assertUpToDate()
  }
  
  fun `test add remove dependency`() {
    val a = addModule("a", withTests = false)
    val b = addModule("b", withTests = false)
    buildAndCheck {
      descriptor("a")
      descriptor("b")
    }
    
    b.dependenciesList.addModuleDependency(a)
    buildAndCheck {
      descriptor("a")
      descriptor("b", "a")
    }
    assertUpToDate()
    
    b.dependenciesList.clear()
    buildAndCheck {
      descriptor("a")
      descriptor("b")
    }
    assertUpToDate()
  }
  
  fun `test add remove module`() {
    addModule("a", withTests = false)
    buildAndCheck {
      descriptor("a")
    }
    
    val b = addModule("b", withTests = false)
    buildAndCheck {
      descriptor("a")
      descriptor("b")
    }
    
    myProject.removeModule(b)
    buildAndCheck {
      descriptor("a")
    }
    assertUpToDate()
  }

  fun `test delete output`() {
    addModule("a", withTests = false)
    buildAndCheck {
      descriptor("a")
    }

    deleteFile("out/${RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME}")
    buildAndCheck {
      descriptor("a")
    }
  }

  private fun assertUpToDate() {
    buildRepository().assertUpToDate()
  }

  private fun buildRepository(): BuildResult {
    return doBuild(CompileScopeTestBuilder.make().targetTypes(RuntimeModuleRepositoryTarget))
  }
}