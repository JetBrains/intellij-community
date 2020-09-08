// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class BookmarkContextProvider extends WorkingContextProvider {
  @NotNull
  @Override
  public String getId() {
    return "bookmarks";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getDescription() {
    return TaskBundle.message("bookmarks");
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) {
    Element state = BookmarkManager.getInstance(project).getState();
    toElement.addContent(ContainerUtil.map(state.getContent(), content -> content.clone()));
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) {
    BookmarkManager.getInstance(project).loadState(fromElement);
  }

  @Override
  public void clearContext(@NotNull Project project) {
    BookmarkManager.getInstance(project).loadState(new Element(getId()));
  }
}
