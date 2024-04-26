// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.indexing.ContentSpec
import com.intellij.openapi.roots.impl.indexing.resolveVirtualFile
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.SdkIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import java.util.*
import kotlin.test.assertNotNull

@RunsInEdt
abstract class IndexableFilesIndexOriginsTestBase {
  companion object {
    @JvmField
    @ClassRule
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val edtRule: EdtRule = EdtRule()

  @Rule
  @JvmField
  val projectModelRule: ProjectModelRule = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule: DisposableRule = DisposableRule()

  @Rule
  @JvmField
  val tempDirectory: TempDirectory = TempDirectory()

  val project: Project get() = projectModelRule.project

  @Before
  fun setUp() {
    runWriteAction {
      (IndexableSetContributor.EP_NAME.point as ExtensionPointImpl<*>).unregisterExtensions({ _, _ -> false }, false)
      (AdditionalLibraryRootsProvider.EP_NAME.point as ExtensionPointImpl<*>).unregisterExtensions({ _, _ -> false }, false)
    }
  }

  protected fun createModuleContentOrigin(fileSpec: ContentSpec, module: com.intellij.openapi.module.Module): IndexableSetOrigin =
    IndexableEntityProviderMethods.createIterators(module, IndexingUrlRootHolder.fromUrl(fileSpec.virtualFileUrl)).first().origin

  protected fun createLibraryOrigin(library: Library): IndexableSetOrigin =
    LibraryIndexableFilesIteratorImpl.createIteratorList(library).also { assertSize(1, it) }.first().origin

  protected fun createSdkOrigin(sdk: Sdk): IndexableSetOrigin = SdkIndexableFilesIteratorImpl.createIterator(sdk).origin

  protected fun createIndexableSetOrigin(contributor: IndexableSetContributor,
                                         project: Project?): IndexableSetOrigin =
    IndexableEntityProviderMethods.createForIndexableSetContributor(contributor,
                                                                    project != null,
                                                                    project?.let { contributor.getAdditionalProjectRootsToIndex(project) }
                                                                    ?: contributor.additionalRootsToIndex).also {
      assertSize(1, it)
    }.first().origin

  protected fun createSyntheticLibraryOrigin(syntheticLibrary: SyntheticLibrary): IndexableSetOrigin =
    IndexableEntityProviderMethods.createForSyntheticLibrary(syntheticLibrary, syntheticLibrary.allRoots).also {
      assertSize(1, it)
    }.first().origin

  protected fun assertOrigin(origin: IndexableSetOrigin, fileSpec: ContentSpec) {
    assertOrigin(origin, fileSpec, "")
  }

  protected fun assertEveryFileOrigin(origin: IndexableSetOrigin, vararg fileSpecs: ContentSpec) {
    for (fileSpec in fileSpecs) {
      assertOrigin(origin, fileSpec)
    }
  }

  protected fun assertOrigin(origin: IndexableSetOrigin, fileSpec: ContentSpec, childPath: String) {
    val file: VirtualFile = if (childPath.isEmpty()) {
      fileSpec.file
    }
    else {
      var currentFile: VirtualFile = fileSpec.file
      childPath.split("/").forEach {
        val child = currentFile.findChild(it)
        assertNotNull(child, "$currentFile doesn't have a child named $it")
        currentFile = child
      }
      currentFile
    }
    val origins = IndexableFilesIndex.getInstance(project).getOrigins(Collections.singleton(file))
    assertThat(origins).containsExactly(origin)
  }

  protected fun assertOrigins(expectedOrigins: Collection<IndexableSetOrigin>, fileSpecs: Collection<ContentSpec>) {
    val actualOrigins = IndexableFilesIndex.getInstance(project).getOrigins(fileSpecs.map { spec -> spec.file })
    assertThat(actualOrigins).containsExactlyInAnyOrderElementsOf(expectedOrigins)
  }

  protected fun assertNoOrigin(vararg fileSpecs: ContentSpec) {
    for (spec in fileSpecs) {
      val origins = IndexableFilesIndex.getInstance(project).getOrigins(Collections.singleton(spec.file))
      assertThat(origins).withFailMessage { "Found unexpected origins $origins for ${spec.file}" }.isEmpty()
    }
  }

  protected fun fireRootsChanged() {
    ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
  }

  private fun maskAdditionalLibraryRootsProviders(vararg additionalLibraryRootsProvider: AdditionalLibraryRootsProvider) {
    runWriteAction {
      ExtensionTestUtil.maskExtensions(AdditionalLibraryRootsProvider.EP_NAME, additionalLibraryRootsProvider.toList(),
                                       disposableRule.disposable)
      fireRootsChanged()
    }
  }

  protected fun createAndSetAdditionalLibraryRootProviderWithSingleLibrary(
    sourceRoots: List<VirtualFile>,
    classRoots: List<VirtualFile> = emptyList(),
    excludedRoots: Set<VirtualFile> = emptySet(),
    excludeCondition: Condition<in VirtualFile>? = null
  ): SyntheticLibrary {
    val syntheticLibrary = SyntheticLibrary.newImmutableLibrary(sourceRoots, classRoots, excludedRoots, excludeCondition)
    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): List<SyntheticLibrary> = listOf(syntheticLibrary)
    }
    maskAdditionalLibraryRootsProviders(additionalLibraryRootsProvider)
    return syntheticLibrary
  }

  protected fun maskIndexableSetContributors(vararg indexableSetContributor: IndexableSetContributor) {
    runWriteAction {
      ExtensionTestUtil.maskExtensions(IndexableSetContributor.EP_NAME, indexableSetContributor.toList(), disposableRule.disposable)
      fireRootsChanged()
    }
  }

  protected val ContentSpec.file: VirtualFile
    get() = resolveVirtualFile()

  private val ContentSpec.virtualFileUrl: VirtualFileUrl
    get() = file.toVirtualFileUrl(WorkspaceModel.getInstance(project).getVirtualFileUrlManager())
}