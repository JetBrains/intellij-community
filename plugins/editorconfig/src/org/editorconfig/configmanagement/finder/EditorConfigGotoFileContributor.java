// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.finder;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.editorconfig.Utils;
import org.editorconfig.language.psi.EditorConfigPsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EditorConfigGotoFileContributor implements ChooseByNameContributor {
  private final static String[] EDITOR_CONFIG_NAMES = new String[] {Utils.EDITOR_CONFIG_FILE_NAME};

  @NotNull
  @Override
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    return EDITOR_CONFIG_NAMES;
  }

  @NotNull
  @Override
  public NavigationItem[] getItemsByName(String name,
                                         String pattern,
                                         Project project, boolean includeNonProjectItems) {
    if (includeNonProjectItems) {
      NavigationItemFactory itemFactory = new NavigationItemFactory(project);
      //noinspection deprecation
      EditorConfigFinder.searchParentEditorConfigs(project.getBaseDir(), itemFactory);
      return itemFactory.getItems();
    }
    return NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY;
  }

  private static class NavigationItemFactory implements EditorConfigFinder.Callback {
    private final List<NavigationItem> myItems = new ArrayList<>();
    private final Project myProject;

    private NavigationItemFactory(Project project) {
      myProject = project;
    }

    @Override
    public EditorConfigFinder.Callback.Result found(@NotNull VirtualFile editorConfigFile) {
      final EditorConfigPsiFile psiFile = getPsiFile(myProject, editorConfigFile);
      if (psiFile != null) {
        myItems.add(psiFile);
      }
      return Result.Continue;
    }

    private NavigationItem[] getItems() {
      return myItems.isEmpty() ? NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY : myItems.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
    }

    @Override
    public void done() {
    }
  }

  @Nullable
  private static EditorConfigPsiFile getPsiFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile instanceof EditorConfigPsiFile) {
        return (EditorConfigPsiFile)psiFile;
      }
    }
    return null;
  }
}
