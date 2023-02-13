// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_SOURCE
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.NOT_IN_PROJECT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
class SyntheticLibraryInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @Test
  fun `add remove library`() {
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("lib/src")
    val srcFile = projectModel.baseProjectDir.newVirtualDirectory("lib/src/src.txt")
    val binaryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib/binary")
    val binaryFile = projectModel.baseProjectDir.newVirtualDirectory("lib/binary/bin.txt")
    val registration = registerSyntheticLibrary(
      SyntheticLibrary.newImmutableLibrary(listOf(srcRoot), listOf(binaryRoot), emptySet(), null)
    )
    fileIndex.assertScope(srcRoot, IN_LIBRARY or IN_SOURCE)
    fileIndex.assertScope(srcFile, IN_LIBRARY or IN_SOURCE)
    assertEquals(srcRoot, fileIndex.getSourceRootForFile(srcFile))
    assertNull(fileIndex.getClassRootForFile(srcFile))
    fileIndex.assertScope(binaryRoot, IN_LIBRARY)
    fileIndex.assertScope(binaryFile, IN_LIBRARY)
    assertEquals(binaryRoot, fileIndex.getClassRootForFile(binaryFile))
    assertNull(fileIndex.getSourceRootForFile(binaryFile))
    
    registration.unregister()
    
    fileIndex.assertScope(srcRoot, NOT_IN_PROJECT)
    fileIndex.assertScope(binaryRoot, NOT_IN_PROJECT)
  }
  
  private interface LibraryRegistration {
    fun unregister()
  }
  
  //todo move to testFramework to allow reusing it in other tests
  private fun registerSyntheticLibrary(library: SyntheticLibrary): LibraryRegistration {
    val disposable = Disposer.newDisposable()
    Disposer.register(projectModel.project, disposable)
    val provider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(library)
      }
    }
    AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(provider, disposable)
    
    //ensure that invokeLater from ProjectRootManagerComponent finishes 
    UIUtil.dispatchAllInvocationEvents()
    
    return object : LibraryRegistration {
      override fun unregister() {
        Disposer.dispose(disposable)
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }

}