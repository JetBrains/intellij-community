// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DirectoryIndexBeneathTest extends DirectoryIndexTestCase {
  public void testDirectoryInfoMustKnowAboutContentDirectoriesBeneathExcluded() throws IOException {
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
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

    Module myModule = newModuleWithContent("myModule", module);
    PsiTestUtil.addSourceRoot(myModule, src);
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    PsiTestUtil.addSourceRoot(myModule, src1);
    PsiTestUtil.addExcludedRoot(myModule, excluded1);

    Module myModule2 = newModuleWithContent("myModule2", module2);
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

  private void checkIterate(@NotNull VirtualFile file, @NotNull VirtualFile... expectToIterate) {
    final List<VirtualFile> collected = new ArrayList<>();
    myFileIndex.iterateContentUnderDirectory(file, fileOrDir -> collected.add(fileOrDir));
    assertSameElements(collected, expectToIterate);
  }

  @NotNull
  private Module newModuleWithContent(@NotNull String name, @NotNull VirtualFile contentRoot) {
    ModuleType type = ModuleTypeManager.getInstance().findByID(ModuleTypeId.JAVA_MODULE);
    return WriteCommandAction.writeCommandAction(getProject()).compute(() -> {
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      String moduleName = moduleModel.newModule(contentRoot.getPath() + "/" + name + ".iml", type.getId()).getName();
      moduleModel.commit();
      Module module = ModuleManager.getInstance(getProject()).findModuleByName(moduleName);
      assertNotNull(moduleName, module);
      PsiTestUtil.addContentRoot(module, contentRoot);

      return module;
    });
  }
}
