// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.util.JpsPathUtil
import kotlin.io.path.Path

class RuntimeModuleRepositoryBuilderTest : RuntimeModuleRepositoryTestCase() {
  fun `test module with tests`() {
    addModule("a", withTests = true)
    buildAndCheck { 
      descriptor("a")
      testDescriptor("a.tests", "a")
    }
  }
  
  fun `test module without sources`() {
    addModule("a", withTests = false, withSources = false)
    buildAndCheck { 
      descriptor("a", resourceDirName = null)
    }
  }
  
  fun `test module with resources only`() {
    val module = addModule("a", withTests = false, withSources = false)
    module.addSourceRoot(getUrl("a/res"), JavaResourceRootType.RESOURCE)
    buildAndCheck { 
      descriptor("a")
    }
  }

  fun `test dependency`() {
    val a = addModule("a", withTests = false)
    addModule("b", a, withTests = false)
    buildAndCheck { 
      descriptor("a")
      descriptor("b", "a")
    }
  }
  
  fun `test transitive dependency`() {
    val a = addModule("a", withTests = false)
    val b = addModule("b", a, withTests = false)
    addModule("c", b, withTests = false)
    buildAndCheck { 
      descriptor("a")
      descriptor("b", "a")
      descriptor("c", "b")
    }
  }
  
  fun `test dependency with tests`() {
    val a = addModule("a", withTests = true)
    addModule("b", a, withTests = true)
    buildAndCheck { 
      descriptor("a")
      testDescriptor("a.tests", "a")
      descriptor("b", "a")
      testDescriptor("b.tests", "b", "a", "a.tests")
    }
  }
  
  fun `test dependency on test only module with non-standard name`() {
    val aTests = addModule("a.test", withSources = false, withTests = true)
    val b = addModule("b", withTests = true)
    val dependency = b.dependenciesList.addModuleDependency(aTests)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.TEST
    buildAndCheck {
      descriptor("a.test", resourceDirName = null)
      testDescriptor("a.test.tests", resourceDirName = "a.test")
      descriptor("b")
      testDescriptor("b.tests", "b", "a.test.tests")
    }
  }

  fun `test transitive dependency via module without tests`() {
    val a = addModule("a", withTests = true)
    val b = addModule("b", a, withTests = false)
    addModule("c", b, withTests = true)
    buildAndCheck {
      descriptor("a")
      descriptor("b", "a")
      descriptor("c", "b")
      testDescriptor("a.tests", "a")
      testDescriptor("c.tests", "c", "b", "a.tests")
    }
  }
  
  fun `test do not add unnecessary transitive dependencies via module without tests`() {
    val a = addModule("a", withTests = true)
    val b = addModule("b", withTests = false)
    val c = addModule("c", a, b, withTests = false)
    addModule("d", c, withTests = true)
    buildAndCheck {
      descriptor("a")
      descriptor("b")
      descriptor("c", "a", "b")
      descriptor("d", "c")
      testDescriptor("a.tests", "a")
      testDescriptor("d.tests", "d", "c", "a.tests")
    }
  }

  fun `test circular dependency with tests`() {
    val a = addModule("a", withTests = true)
    val b = addModule("b", a, withTests = true)
    val dependency = a.dependenciesList.addModuleDependency(b)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.RUNTIME
    buildAndCheck {
      descriptor("a", "b")
      testDescriptor("a.tests", "a", "b", "b.tests")
      descriptor("b", "a")
      testDescriptor("b.tests", "b", "a", "a.tests")
    }
  }
  
  fun `test circular dependency without tests`() {
    val a = addModule("a", withTests = false)
    val b = addModule("b", a, withTests = false)
    val dependency = a.dependenciesList.addModuleDependency(b)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.RUNTIME
    addModule("c", b, withTests = true)
    buildAndCheck {
      descriptor("a", "b")
      descriptor("b", "a")
      descriptor("c", "b")
      testDescriptor("c.tests", "c", "b")
    }
  }

  fun `test separate module for tests`() {
    val a = addModule("a", withTests = false)
    addModule("a.tests", a, withTests = true, withSources = false)
    buildAndCheck {
      descriptor("a")
      testDescriptor("a.tests", "a", resourceDirName = "a.tests")
    }
  }
  
  fun `test module with production roots named like a test module`() {
    val name = "a.tests.actually.not"
    addModule(name, withTests = false)
    buildAndCheck {
      descriptor(name)
      descriptor(name)
    }
  }

  fun `test module library`() {
    val a = addModule("a", withTests = false)
    val lib = a.libraryCollection.addLibrary("lib", JpsJavaLibraryType.INSTANCE)
    a.dependenciesList.addLibraryDependency(lib)
    lib.addRoot(getUrl("project/lib"), JpsOrderRootType.COMPILED)
    buildAndCheck { 
      descriptor("a", "lib.a.lib")
      descriptor("lib.a.lib", listOf($$"$PROJECT_DIR$/lib"), emptyList())
    }
  }
  
  fun `test project library`() {
    val a = addModule("a", withTests = false)
    val lib = myProject.libraryCollection.addLibrary("lib", JpsJavaLibraryType.INSTANCE)
    a.dependenciesList.addLibraryDependency(lib)
    lib.addRoot(getUrl("project/lib"), JpsOrderRootType.COMPILED)
    buildAndCheck { 
      descriptor("a", "lib.lib")
      descriptor("lib.lib", listOf($$"$PROJECT_DIR$/lib"), emptyList())
    }
  }
  
  fun `test library with roots from Maven repository`() {
    val a = addModule("a", withTests = false)
    val lib = myProject.libraryCollection.addLibrary("lib", JpsJavaLibraryType.INSTANCE)
    a.dependenciesList.addLibraryDependency(lib)
    val mavenRepoRoot = Path(JpsMavenSettings.getMavenRepositoryPath())
    val relativeLibPath = "org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar"
    lib.addRoot(JpsPathUtil.getLibraryRootUrl(mavenRepoRoot.resolve(relativeLibPath)), JpsOrderRootType.COMPILED)
    buildAndCheck { 
      descriptor("a", "lib.lib")
      descriptor("lib.lib", listOf($$"$MAVEN_REPOSITORY$/$$relativeLibPath"), emptyList())
    }
  }
  
  fun `test library with test scope`() {
    val a = addModule("a", withTests = true)
    val lib = myProject.libraryCollection.addLibrary("lib", JpsJavaLibraryType.INSTANCE)
    val dependency = a.dependenciesList.addLibraryDependency(lib)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.TEST
    lib.addRoot(getUrl("project/lib"), JpsOrderRootType.COMPILED)
    buildAndCheck { 
      descriptor("a")
      testDescriptor("a.tests", "a", "lib.lib")
      descriptor("lib.lib", listOf($$"$PROJECT_DIR$/lib"), emptyList())
    }
  }
}

