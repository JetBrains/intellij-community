package com.intellij.workspace.jps

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.workspace.api.VirtualFileUrlManager
import com.intellij.workspace.api.toVirtualFileUrl
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFileContainer
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProviderImpl
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LegacyBridgeFilePointerProviderTest {
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
    virtualFileManager = VirtualFileUrlManagerImpl.getInstance(project)
  }

  @Test
  fun `cache invalidated on rename`() {
    val provider = LegacyBridgeFilePointerProviderImpl(project).also { Disposer.register(disposable.disposable, it) }

    val file = tempDir.newFile("x.txt")
    val url = file.toVirtualFileUrl(virtualFileManager)

    val virtualFile1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
    val pointer1 = provider.getAndCacheFilePointer(url)
    val container1 = provider.getAndCacheFileContainer(LegacyBridgeFileContainer(urls = listOf(url), jarDirectories = emptyList()))

    Assert.assertTrue(file.exists())
    WriteAction.runAndWait<Throwable> { virtualFile1.rename(null, "y.txt") }
    Assert.assertFalse(file.exists())

    file.writeText("")

    val pointer2 = provider.getAndCacheFilePointer(url)
    val container2 = provider.getAndCacheFileContainer(LegacyBridgeFileContainer(urls = listOf(url), jarDirectories = emptyList()))

    Assert.assertEquals(url.url, pointer2.url)
    Assert.assertEquals(url.url, container2.urls.single())
    Assert.assertEquals(File(file.parentFile, "y.txt").toVirtualFileUrl(virtualFileManager).url, pointer1.url)
    Assert.assertEquals(File(file.parentFile, "y.txt").toVirtualFileUrl(virtualFileManager).url, container1.urls.single())
  }

  @Test
  fun `cache invalidated on move`() {
    val provider = LegacyBridgeFilePointerProviderImpl(project).also { Disposer.register(disposable.disposable, it) }

    val targetFolder = tempDir.newFolder("target")
    val targetFolderVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFolder)!!

    val file = tempDir.newFile("x.txt")
    val fileAfterMove = File(targetFolder, "x.txt")

    val url = file.toVirtualFileUrl(virtualFileManager)
    val urlAfterMove = fileAfterMove.toVirtualFileUrl(virtualFileManager)

    val virtualFile1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
    val pointer1 = provider.getAndCacheFilePointer(url)
    val container1 = provider.getAndCacheFileContainer(LegacyBridgeFileContainer(urls = listOf(url), jarDirectories = emptyList()))

    Assert.assertTrue(file.exists())
    WriteAction.runAndWait<Throwable> { virtualFile1.move(null, targetFolderVirtualFile) }
    Assert.assertFalse(file.exists())

    file.writeText("")

    val pointer2 = provider.getAndCacheFilePointer(url)
    val container2 = provider.getAndCacheFileContainer(LegacyBridgeFileContainer(urls = listOf(url), jarDirectories = emptyList()))

    Assert.assertEquals(url.url, pointer2.url)
    Assert.assertEquals(url.url, container2.urls.single())
    Assert.assertEquals(urlAfterMove.url, pointer1.url)
    Assert.assertEquals(urlAfterMove.url, container1.urls.single())
  }
}