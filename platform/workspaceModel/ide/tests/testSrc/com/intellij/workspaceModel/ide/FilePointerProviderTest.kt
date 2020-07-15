package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.toVirtualFileUrl
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FileContainerDescription
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerProvider
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerProviderImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer.FilePointerScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class FilePointerProviderTest {
  @JvmField
  @Rule
  val application = ApplicationRule()

  @JvmField
  @Rule
  val tempDir = TempDirectory()

  @Rule
  @JvmField
  var temporaryDirectoryRule = TemporaryDirectory()

  @JvmField
  @Rule
  val disposable = DisposableRule()

  private lateinit var project: Project
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun prepareProject() {
    project = createEmptyTestProject(temporaryDirectoryRule, disposable)
    virtualFileManager = VirtualFileUrlManager.getInstance(project)
  }

  @Test
  fun `cache invalidated on rename`() {
    val provider = FilePointerProviderImpl(project).also { Disposer.register(disposable.disposable, it) }

    val file = tempDir.newFile("x.txt")
    val url = file.toVirtualFileUrl(virtualFileManager)

    val virtualFile1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
    val pointer1 = provider.getAndCacheFilePointer(url, FilePointerScope.Test)
    val parentDisposable = Disposer.newDisposable().also { Disposer.register(disposable.disposable, it) }
    val container1 = provider.getAndCacheFileContainer(FileContainerDescription(urls = listOf(url), jarDirectories = emptyList()),
                                                       parentDisposable)

    assertTrue(file.exists())
    WriteAction.runAndWait<Throwable> { virtualFile1.rename(null, "y.txt") }
    assertFalse(file.exists())

    file.writeText("")

    val pointer2 = provider.getAndCacheFilePointer(url, FilePointerScope.Test)
    val container2 = provider.getAndCacheFileContainer(FileContainerDescription(urls = listOf(url), jarDirectories = emptyList()),
                                                       parentDisposable)

    assertEquals(url.url, pointer2.url)
    assertEquals(url.url, container2.urls.single())
    assertFalse(pointer1.isValid)
    assertTrue(container1.isEmpty)
  }

  @Test
  fun `cache invalidated on move`() {
    val provider = FilePointerProviderImpl(project).also { Disposer.register(disposable.disposable, it) }

    val targetFolder = tempDir.newDirectory("target")
    val targetFolderVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFolder)!!

    val file = tempDir.newFile("x.txt")
    val fileAfterMove = File(targetFolder, "x.txt")

    val url = file.toVirtualFileUrl(virtualFileManager)
    val urlAfterMove = fileAfterMove.toVirtualFileUrl(virtualFileManager)

    val virtualFile1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
    val pointer1 = provider.getAndCacheFilePointer(url, FilePointerScope.Test)
    val parentDisposable = Disposer.newDisposable().also { Disposer.register(disposable.disposable, it) }
    val container1 = provider.getAndCacheFileContainer(FileContainerDescription(urls = listOf(url), jarDirectories = emptyList()),
                                                       parentDisposable)

    assertTrue(file.exists())
    WriteAction.runAndWait<Throwable> { virtualFile1.move(null, targetFolderVirtualFile) }
    assertFalse(file.exists())

    file.writeText("")

    val pointer2 = provider.getAndCacheFilePointer(url, FilePointerScope.Test)
    val container2 = provider.getAndCacheFileContainer(FileContainerDescription(urls = listOf(url), jarDirectories = emptyList()),
                                                       parentDisposable)

    assertEquals(url.url, pointer2.url)
    assertEquals(url.url, container2.urls.single())
    assertFalse(pointer1.isValid)
    assertTrue(container1.isEmpty)
  }

  @Test
  fun `file pointers cache isn't reloaded with module change`() = WriteCommandAction.runWriteCommandAction(project) {
    val modifiableModel = ModuleManager.getInstance(project).modifiableModel
    val module = modifiableModel.newNonPersistentModule("myModule", "myModule")
    modifiableModel.commit()

    val pointerProvider = FilePointerProvider.getInstance(module) as FilePointerProviderImpl

    assertTrue(pointerProvider.getFilePointers().isEmpty())

    val modModuleRootModel = ModuleRootManager.getInstance(module).modifiableModel
    val contentUrl = VfsUtilCore.pathToUrl(temporaryDirectoryRule.newPath("first").toFile().absolutePath)
    modModuleRootModel.addContentEntry(contentUrl)
    modModuleRootModel.commit()

    val pointer = pointerProvider.getFilePointers().values.single().first

    val modModuleRootModel2 = ModuleRootManager.getInstance(module).modifiableModel
    val contentUrl2 = VfsUtilCore.pathToUrl(temporaryDirectoryRule.newPath("second").toFile().absolutePath)
    modModuleRootModel2.addContentEntry(contentUrl2)
    modModuleRootModel2.commit()

    val pointers = pointerProvider.getFilePointers().values.map { it.first }
    assertEquals(2, pointers.size)
    assertSame(pointer, pointers.first())
  }

  @Test
  fun `file pointers removed on entity removal`() = WriteCommandAction.runWriteCommandAction(project) {
    val modifiableModel = ModuleManager.getInstance(project).modifiableModel
    val module = modifiableModel.newNonPersistentModule("myModule", "myModule")
    modifiableModel.commit()

    val pointerProvider = FilePointerProvider.getInstance(module) as FilePointerProviderImpl

    assertTrue(pointerProvider.getFilePointers().isEmpty())

    val modModuleRootModel = ModuleRootManager.getInstance(module).modifiableModel
    val contentUrl = VfsUtilCore.pathToUrl(temporaryDirectoryRule.newPath("first").toFile().absolutePath)
    val contentEntry = modModuleRootModel.addContentEntry(contentUrl)
    modModuleRootModel.commit()

    val pointer = pointerProvider.getFilePointers().values.single().first

    val modModuleRootModel2 = ModuleRootManager.getInstance(module).modifiableModel
    modModuleRootModel2.removeContentEntry(contentEntry)
    modModuleRootModel2.commit()

    assertFalse(pointer.isValid)
  }
}