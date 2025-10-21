// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import kotlin.io.path.pathString

@TestApplication
@RunInEdt(writeIntent = true)
class IteratingContentUnderExcludedTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @Test
  fun testDirectoryInfoMustKnowAboutContentDirectoriesBeneathExcluded() {
    /*
      /root
         /rootSub
         /myModule (module content root)
            /src (source root)
            /src1 (source root)
               /excluded1 (excluded)
                  /e.txt
            /excluded (excluded)
               /myModule2 (module2 content root)
                  /src2 (module2 source root)
                     /my2.txt
               /subExcluded
     */
    val root = projectModel.baseProjectDir.virtualFileRoot
    projectModel.baseProjectDir.newVirtualDirectory("rootSub")
    val module = projectModel.baseProjectDir.newVirtualDirectory("myModule")
    val src = projectModel.baseProjectDir.newVirtualDirectory("myModule/src")
    val src1 = projectModel.baseProjectDir.newVirtualDirectory("myModule/src1")
    val excluded1 = projectModel.baseProjectDir.newVirtualDirectory("myModule/src1/excluded1")
    val eTxt = projectModel.baseProjectDir.newVirtualFile("myModule/src1/excluded1/e.txt")
    val excluded = projectModel.baseProjectDir.newVirtualDirectory("myModule/excluded")
    val subExcluded = projectModel.baseProjectDir.newVirtualDirectory("myModule/excluded/subExcluded")
    val module2 = projectModel.baseProjectDir.newVirtualDirectory("myModule/excluded/myModule2")
    val src2 = projectModel.baseProjectDir.newVirtualDirectory("myModule/excluded/myModule2/src2")
    val my2Txt = projectModel.baseProjectDir.newVirtualFile("myModule/excluded/myModule2/src2/my2.txt")
    val myModule = projectModel.createModule("myModule")
    PsiTestUtil.addContentRoot(myModule, module)
    PsiTestUtil.addSourceRoot(myModule, src)
    PsiTestUtil.addExcludedRoot(myModule, excluded)
    PsiTestUtil.addSourceRoot(myModule, src1)
    PsiTestUtil.addExcludedRoot(myModule, excluded1)
    val myModule2 = projectModel.createModule("myModule2")
    PsiTestUtil.addContentRoot(myModule2, module2)
    PsiTestUtil.addSourceRoot(myModule2, src2)
    checkIterate(root, module, src, src1, module2, src2, my2Txt)
    checkIterate(src, src)
    checkIterate(src1, src1)
    checkIterate(excluded1)
    checkIterate(module, module, src, src1, module2, src2, my2Txt)
    checkIterate(eTxt)
    checkIterate(excluded, module2, src2, my2Txt)
    checkIterate(module2, module2, src2, my2Txt)
    checkIterate(subExcluded)
    assertIteratedContent(fileIndex,
                          root,
                          listOf(module, src, src1, module2, src2, my2Txt),
                          listOf(root, excluded1, eTxt, excluded, subExcluded))
  }

  private fun checkIterate(file: VirtualFile, vararg expectToIterate: VirtualFile) {
    val collected: MutableList<VirtualFile> = ArrayList()
    fileIndex.iterateContentUnderDirectory(file) { fileOrDir: VirtualFile -> collected.add(fileOrDir) }
    UsefulTestCase.assertSameElements(collected, *expectToIterate)
  }

  @Test
  fun testDirectoryIndexMustNotGoInsideIgnoredDotGit() {
    val root = projectModel.baseProjectDir.virtualFileRoot
    /*
      /root
         /.git
             g1.txt
             g2.txt
         /myModule (module content root)
            /src (source root)
     */
    val dGit = File(root.path, ".git")
    assertTrue(dGit.mkdir())
    val g1File = File(dGit, "g1.txt")
    assertTrue(g1File.createNewFile())
    val g2File = File(dGit, "g2.txt")
    assertTrue(g2File.createNewFile())
    val module = projectModel.baseProjectDir.newVirtualDirectory("myModule")
    val src = projectModel.baseProjectDir.newVirtualDirectory("myModule/src")
    val myModule = projectModel.createModule("myModule")
    PsiTestUtil.addContentRoot(myModule, module)
    PsiTestUtil.addSourceRoot(myModule, src)
    root.refresh(false, true)
    checkIterate(root, module, src)
    checkIterate(src, src)
    checkIterate(module, module, src)
    val cachedChildren = (root as VirtualFileSystemEntry).cachedChildren
    val dgt = cachedChildren.find { it.name == ".git" }
    // null is fine too - it means .git wasn't even loaded
    if (dgt != null) {
      // but no way .git should be entered
      val dcached = (dgt as VirtualFileSystemEntry).cachedChildren
      UsefulTestCase.assertEmpty(dcached.toString(), dcached)
    }
    val dotGit = UsefulTestCase.refreshAndFindFile(dGit)
    val g1Txt = UsefulTestCase.refreshAndFindFile(g1File)
    val g2Txt = UsefulTestCase.refreshAndFindFile(g2File)
    assertTrue(fileIndex.isUnderIgnored(dotGit))
    assertTrue(FileTypeRegistry.getInstance().isFileIgnored(dotGit))
    assertFalse(FileTypeRegistry.getInstance().isFileIgnored(g1Txt))
    assertFalse(FileTypeRegistry.getInstance().isFileIgnored(g2Txt))
    assertTrue(fileIndex.isUnderIgnored(g1Txt))
    assertTrue(fileIndex.isUnderIgnored(g2Txt))
    checkIterate(dotGit)
    assertIteratedContent(fileIndex,
                          root,
                          listOf(module, src),
                          listOf(root, g1Txt, g2Txt))
  }

  @Test
  fun `iterate over recursive symlink under excluded directories`() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/excluded")
    val contentFile = projectModel.baseProjectDir.newVirtualFile("root/file.txt")
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, contentRoot)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    val linkPath = excludedDir.toNioPath().resolve("link")
    IoTestUtil.createSymLink(contentRoot.toNioPath().pathString, linkPath.pathString)
    PsiTestUtil.addSourceRoot(module, VirtualFileManager.getInstance().refreshAndFindFileByNioPath(linkPath)!!)
    assertIteratedContent(fileIndex, contentRoot, listOf(contentFile), listOf(excludedDir))
  }
  
  @Test
  fun `iterate over recursive symlink from excluded directory`() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    val contentFile = projectModel.baseProjectDir.newVirtualFile("root/file.txt")
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, contentRoot)
    val excludedPath = contentRoot.toNioPath().resolve("excluded")
    IoTestUtil.createSymLink(contentRoot.toNioPath().pathString, excludedPath.pathString)
    val excludedRoot = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(excludedPath)!!
    PsiTestUtil.addExcludedRoot(module, excludedRoot)
    PsiTestUtil.addExcludedRoot(module, contentRoot.findFileOrDirectory("excluded/excluded/excluded/excluded/excluded/excluded/excluded/excluded/excluded/excluded/excluded/")!!)
    assertIteratedContent(fileIndex, contentRoot, listOf(contentFile), listOf(excludedRoot))
  }

  @Test
  fun testTraversingNonProjectFilesShouldBeFast() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
    val root = projectModel.baseProjectDir.virtualFileRoot
    generateSymlinkExplosion(VfsUtilCore.virtualToIoFile(root), 17)

    Benchmark.newBenchmark("traversing non-project roots") { checkIterate(root) }
      .runAsStressTest()
      .start()
  }

  companion object {
    /**
     * Creates a temporal root directory and generates symlinks inside it
     * so traversing the root directory with following symlinks results in O(2^depth) files visited.
     * E.g. amount of files inside the file tree generated for depth=15 is 65536 (`find -follow | wc -l` prints 65536)
     * generate(16) produces 131072 directories
     * generate(17): 262144
     * No symlinks cycles are generated.
     */
    private fun generateSymlinkExplosion(root: File, depth: Int) {
      var prevDirPath = ""
      for (i in 0 until depth) {
        val dir = File(root, "my-$i")
        assertTrue(dir.mkdirs())
        if (i != 0) {
          IoTestUtil.createSymLink(prevDirPath, "$dir/a")
          IoTestUtil.createSymLink(prevDirPath, "$dir/b")
        }
        prevDirPath = dir.path
      }
    }
  }
}
