// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.finder;

import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EditorConfigGotoFileContributor implements ChooseByNameContributor {
  private static final String[] EDITOR_CONFIG_NAMES = new String[] {Utils.EDITOR_CONFIG_FILE_NAME};

  @Override
  public String @NotNull [] getNames(Project project, boolean includeNonProjectItems) {
    return EDITOR_CONFIG_NAMES;
  }

  @Override
  public NavigationItem @NotNull [] getItemsByName(String name,
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

  private static final class NavigationItemFactory implements EditorConfigFinder.Callback {
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

  private static @Nullable EditorConfigPsiFile getPsiFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
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
