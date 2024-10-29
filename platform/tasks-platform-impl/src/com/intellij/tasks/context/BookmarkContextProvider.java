// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.bookmark.ManagerState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
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
    ManagerState state = requireNonNull(getComponent(project).getState());
    toElement.addContent(XmlSerializer.serialize(state));
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) {
    ManagerState state = new ManagerState();
    Element element = fromElement.getChild(ManagerState.class.getSimpleName());
    if (element != null) XmlSerializer.deserializeInto(state, element);
    getComponent(project).loadState(state);
  }

  @Override
  public void clearContext(@NotNull Project project) {
    getComponent(project).loadState(new ManagerState());
  }

  @SuppressWarnings("unchecked")
  private static @NotNull PersistentStateComponent<ManagerState> getComponent(@NotNull Project project) {
    return (PersistentStateComponent<ManagerState>)requireNonNull(BookmarksManager.getInstance(project));
  }
}
