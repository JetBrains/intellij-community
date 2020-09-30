// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DirectoryIndexBeneathTest extends DirectoryIndexTestCase {
  public void testDirectoryInfoMustKnowAboutContentDirectoriesBeneathExcluded() throws IOException {
    VirtualFile root = getTempDir().createVirtualDir();
    assertNotNull(root);
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
    assertFalse(myFileIndex.isInContent(root));
    VirtualFile rootSub = createChildDirectory(root, "rootSub");
    assertFalse(myFileIndex.isInContent(rootSub));
    VirtualFile module = createChildDirectory(root, "myModule");
    VirtualFile src = createChildDirectory(module, "src");
    VirtualFile src1 = createChildDirectory(module, "src1");
    VirtualFile excluded1 = createChildDirectory(src1, "excluded1");
    VirtualFile eTxt = createChildData(excluded1, "e.txt");
    VirtualFile excluded = createChildDirectory(module, "excluded");
    VirtualFile subExcluded = createChildDirectory(excluded, "subExcluded");
    VirtualFile module2 = createChildDirectory(excluded, "myModule2");
    VirtualFile src2 = createChildDirectory(module2, "src2");
    VirtualFile my2Txt = createChildData(src2, "my2.txt");

    Module myModule = createJavaModuleWithContent(getProject(), "myModule", module);
    PsiTestUtil.addSourceRoot(myModule, src);
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    PsiTestUtil.addSourceRoot(myModule, src1);
    PsiTestUtil.addExcludedRoot(myModule, excluded1);

    Module myModule2 = createJavaModuleWithContent(getProject(), "myModule2", module2);
    PsiTestUtil.addSourceRoot(myModule2, src2);

    checkIterate(root, module, src, src1, module2, src2, my2Txt);
    checkIterate(src, src);
    checkIterate(src1, src1);
    checkIterate(excluded1);
    checkIterate(module, module, src, src1, module2, src2, my2Txt);
    checkIterate(eTxt);
    checkIterate(excluded, module2, src2, my2Txt);
    checkIterate(module2, module2, src2, my2Txt);
    checkIterate(subExcluded);

    assertIteratedContent(myFileIndex,
                          root,
                          Arrays.asList(module, src, src1, module2, src2, my2Txt),
                          Arrays.asList(root, excluded1, eTxt, excluded, subExcluded));
  }

  private void checkIterate(@NotNull VirtualFile file, VirtualFile @NotNull ... expectToIterate) {
    final List<VirtualFile> collected = new ArrayList<>();
    myFileIndex.iterateContentUnderDirectory(file, fileOrDir -> collected.add(fileOrDir));
    assertSameElements(collected, expectToIterate);
  }

  public void testDirectoryIndexMustNotGoInsideIgnoredDotGit() throws IOException {
    VirtualFile root = getTempDir().createVirtualDir();
    assertNotNull(root);
    /*
      /root
         /.git
             g1.txt
             g2.txt
         /myModule (module content root)
            /src (source root)
     */
    assertFalse(myFileIndex.isInContent(root));
    File dGit = new File(root.getPath(), ".git");
    assertTrue(dGit.mkdir());
    File g1File = new File(dGit, "g1.txt");
    assertTrue(g1File.createNewFile());
    File g2File = new File(dGit, "g2.txt");
    assertTrue(g2File.createNewFile());
    VirtualFile module = createChildDirectory(root, "myModule");
    VirtualFile src = createChildDirectory(module, "src");

    Module myModule = createJavaModuleWithContent(getProject(), "myModule", module);
    PsiTestUtil.addSourceRoot(myModule, src);

    root.refresh(false, true);

    checkIterate(root, module, src);
    checkIterate(src, src);
    checkIterate(module, module, src);

    Collection<VirtualFile> cachedChildren = ((VirtualFileSystemEntry)root).getCachedChildren();
    VirtualFile dgt = ContainerUtil.find(cachedChildren, v -> v.getName().equals(".git"));
    // null is fine too - it means .git wasn't even loaded
    if (dgt != null) {
      // but no way .git should be entered
      Collection<VirtualFile> dcached = ((VirtualFileSystemEntry)dgt).getCachedChildren();
      assertEmpty(dcached.toString(), dcached);
    }

    VirtualFile dotGit = refreshAndFindFile(dGit);
    VirtualFile g1Txt = refreshAndFindFile(g1File);
    VirtualFile g2Txt = refreshAndFindFile(g2File);
    assertTrue(myFileIndex.isUnderIgnored(dotGit));
    assertTrue(FileTypeRegistry.getInstance().isFileIgnored(dotGit));
    assertFalse(FileTypeRegistry.getInstance().isFileIgnored(g1Txt));
    assertFalse(FileTypeRegistry.getInstance().isFileIgnored(g2Txt));
    assertTrue(myFileIndex.isUnderIgnored(g1Txt));
    assertTrue(myFileIndex.isUnderIgnored(g2Txt));
    checkIterate(dotGit);

    assertIteratedContent(myFileIndex,
                          root,
                          Arrays.asList(module, src),
                          Arrays.asList(root, g1Txt, g2Txt));
  }
}
