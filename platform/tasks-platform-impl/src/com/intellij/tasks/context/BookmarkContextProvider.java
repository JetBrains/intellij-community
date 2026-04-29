// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.ide.bookmark.BookmarkGroup;
import com.intellij.ide.bookmark.BookmarkProvider;
import com.intellij.ide.bookmark.BookmarkState;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.bookmark.GroupState;
import com.intellij.ide.bookmark.ManagerState;
import com.intellij.ide.bookmark.providers.LineBookmarkProvider;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.tasks.TaskBundle;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

final class BookmarkContextProvider extends WorkingContextProvider {
  @Override
  public @NotNull String getId() {
    return "bookmarks";
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getDescription() {
    return TaskBundle.message("bookmarks");
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) {
    if (!AdvancedSettings.getBoolean("tasks.enable.bookmark.context.on.branch.switch")) return;
    ManagerState state = requireNonNull(getComponent(project).getState());
    toElement.addContent(XmlSerializer.serialize(state));
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) {
    if (!AdvancedSettings.getBoolean("tasks.enable.bookmark.context.on.branch.switch")) return;
    ManagerState state = new ManagerState();
    Element element = fromElement.getChild(ManagerState.class.getSimpleName());
    if (element != null) XmlSerializer.deserializeInto(state, element);

    restoreState(project, state);
  }

  @Override
  public void clearContext(@NotNull Project project) {
    if (!AdvancedSettings.getBoolean("tasks.enable.bookmark.context.on.branch.switch")) return;
    PersistentStateComponent<ManagerState> component = getComponent(project);
    if (component instanceof BookmarksManager) {
      ((BookmarksManager)component).remove();
    }
  }

  private static void restoreState(@NotNull Project project, @NotNull ManagerState state) {
    BookmarksManager manager = BookmarksManager.getInstance(project);
    if (manager == null) return;

    for (GroupState groupState : state.getGroups()) {
      BookmarkGroup group = manager.addGroup(groupState.getName(), groupState.isDefault());
      if (group == null) group = manager.getGroup(groupState.getName());
      if (group == null) continue;

      for (BookmarkState bookmarkState : groupState.getBookmarks()) {
        BookmarkProvider provider = findProvider(project, bookmarkState.getProvider());
        if (provider == null) continue;
        var bookmark = provider.createBookmark(bookmarkState.getAttributes());
        if (bookmark == null) continue;
        group.add(bookmark, bookmarkState.getType(), bookmarkState.getDescription());
      }
    }

    LineBookmarkProvider lineProvider = LineBookmarkProvider.Util.find(project);
    if (lineProvider == null) return;

    if (StartupManager.getInstance(project).postStartupActivityPassed()) {
      lineProvider.requestValidation();
    }
    else {
      StartupManager.getInstance(project).runAfterOpened(() -> lineProvider.requestValidation());
    }
  }

  private static @Nullable BookmarkProvider findProvider(@NotNull Project project, @Nullable String providerClassName) {
    if (providerClassName == null) return null;
    for (BookmarkProvider provider : BookmarkProvider.EP.getExtensions(project)) {
      if (provider.getClass().getName().equals(providerClassName)) {
        return provider;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static @NotNull PersistentStateComponent<ManagerState> getComponent(@NotNull Project project) {
    return (PersistentStateComponent<ManagerState>)requireNonNull(BookmarksManager.getInstance(project));
  }
}
