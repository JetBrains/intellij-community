/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestFileStructure {
  private int myLevel;
  @NotNull private Project myProject;
  @NotNull private Module myModule;
  @NotNull private PsiDirectory myCurrentLevelDirectory;
  private List<List<PsiFile>> myFilesForLevel = new ArrayList<>();

  public TestFileStructure(@NotNull Module module, @NotNull PsiDirectory root) {
    myProject = module.getProject();
    myModule = module;
    myCurrentLevelDirectory = root;
    myFilesForLevel.add(new ArrayList<>());
    myLevel = 0;
  }

  @NotNull
  public PsiFile addTestFile(@NotNull String name, @NotNull String content) throws IOException {
    PsiFile createdFile = createFile(myModule, myCurrentLevelDirectory.getVirtualFile(), name, content);
    getCurrentDirectoryFiles().add(createdFile);
    return createdFile;
  }

  @NotNull
  public List<PsiFile> getCurrentDirectoryFiles() {
    return myFilesForLevel.get(myLevel);
  }

  @NotNull
  public PsiDirectory getCurrentDirectory() {
    return myCurrentLevelDirectory;
  }

  @NotNull
  public PsiDirectory createDirectoryAndMakeItCurrent(String name) throws IOException {
    myLevel++;
    myFilesForLevel.add(new ArrayList<>());
    myCurrentLevelDirectory = createDirectory(myProject, myCurrentLevelDirectory.getVirtualFile(), name);
    return myCurrentLevelDirectory;
  }

  public List<PsiFile> getFilesAtLevel(int level) {
    assert (myLevel >= level);
    return myFilesForLevel.get(level);
  }

  private PsiFile createFile(final Module module, final VirtualFile vDir, final String fileName, final String text) {
    return new WriteAction<PsiFile>() {
      @Override
      protected void run(@NotNull Result<PsiFile> result) throws Throwable {
        if (!ModuleRootManager.getInstance(module).getFileIndex().isInSourceContent(vDir)) {
          PsiTestUtil.addSourceContentToRoots(module, vDir);
        }

        final VirtualFile vFile = vDir.createChildData(vDir, fileName);
        VfsUtil.saveText(vFile, text);
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        final PsiFile file = PsiManager.getInstance(myProject).findFile(vFile);
        assert (file != null);
        result.setResult(file);
      }
    }.execute().getResultObject();
  }

  public static PsiDirectory createDirectory(@NotNull Project project, @NotNull final VirtualFile parent, @NotNull final String name) throws IOException {
    final VirtualFile[] dir = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        dir[0] = parent.createChildDirectory(null, name);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });
    return PsiDirectoryFactory.getInstance(project).createDirectory(dir[0]);
  }

  public static void delete(@NotNull final VirtualFile file) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        file.delete(null);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });
  }
}
