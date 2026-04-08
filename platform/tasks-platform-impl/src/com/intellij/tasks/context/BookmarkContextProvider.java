// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.bookmark.BookmarkGroup;
import com.intellij.ide.bookmark.BookmarkProvider;
import com.intellij.ide.bookmark.BookmarkState;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.bookmark.GroupState;
import com.intellij.ide.bookmark.ManagerState;
import com.intellij.ide.bookmark.providers.LineBookmarkProvider;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.tasks.TaskBundle;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

final class BookmarkContextProvider extends WorkingContextProvider {
  private static final Logger LOG = Logger.getInstance(BookmarkContextProvider.class);

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
    ManagerState state = requireNonNull(getComponent(project).getState());
    toElement.addContent(XmlSerializer.serialize(state));
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) {
    ManagerState state = new ManagerState();
    Element element = fromElement.getChild(ManagerState.class.getSimpleName());
    if (element != null) XmlSerializer.deserializeInto(state, element);

    mergeState(project, state);
  }

  @Override
  public void clearContext(@NotNull Project project) {
    if (AdvancedSettings.getBoolean("tasks.enable.bookmark.context.on.branch.switch")) {
      PersistentStateComponent<ManagerState> component = getComponent(project);
      if (component instanceof BookmarksManager) {
        ((BookmarksManager)component).remove();
      }
    }
  }

  private static void mergeState(@NotNull Project project, @NotNull ManagerState state) {
    BookmarksManager manager = BookmarksManager.getInstance(project);
    if (manager == null) return;

    boolean branchContextEnabled = AdvancedSettings.getBoolean("tasks.enable.bookmark.context.on.branch.switch");

    if (LOG.isDebugEnabled()) {
      LOG.debug("=== mergeState START ===");
      LOG.debug("Branch context enabled: " + branchContextEnabled);
      LOG.debug("Groups to merge: " + state.getGroups().size());
    }

    for (GroupState groupState : state.getGroups()) {
      BookmarkGroup group = manager.addGroup(groupState.getName(), groupState.isDefault());
      if (group == null) {
        group = manager.getGroup(groupState.getName());
      }
      if (group == null) continue;

      Map<BookmarkLocation, Bookmark> existingByLocation = buildLocationMap(group.getBookmarks());

      if (LOG.isDebugEnabled()) {
        LOG.debug("Processing group '" + groupState.getName() + "': " + 
                  groupState.getBookmarks().size() + " saved bookmarks, " + 
                  existingByLocation.size() + " existing bookmarks");
      }

      for (BookmarkState bookmarkState : groupState.getBookmarks()) {
        BookmarkProvider provider = findProvider(project, bookmarkState.getProvider());
        if (provider == null) continue;
        
        Bookmark bookmark = provider.createBookmark(bookmarkState.getAttributes());
        if (bookmark == null) continue;

        BookmarkLocation location = getBookmarkLocation(bookmark);
        Bookmark existing = location != null ? existingByLocation.get(location) : null;

        if (existing == null) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("  [ADD] " + bookmark.getClass().getSimpleName() + " at " + location);
          }
          group.add(bookmark, bookmarkState.getType(), bookmarkState.getDescription());
        }
        else if (branchContextEnabled) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("  [REPLACE] " + existing.getClass().getSimpleName() + " -> " + 
                      bookmark.getClass().getSimpleName() + " at " + location);
          }
          group.remove(existing);
          group.add(bookmark, bookmarkState.getType(), bookmarkState.getDescription());
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("  [KEEP] " + existing.getClass().getSimpleName() + " at " + location);
          }
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("=== mergeState END ===");
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

  private static @NotNull Map<BookmarkLocation, Bookmark> buildLocationMap(@NotNull List<Bookmark> bookmarks) {
    Map<BookmarkLocation, Bookmark> map = new HashMap<>();
    for (Bookmark bookmark : bookmarks) {
      BookmarkLocation location = getBookmarkLocation(bookmark);
      if (location != null) {
        map.put(location, bookmark);
      }
    }
    return map;
  }

  private static @Nullable BookmarkLocation getBookmarkLocation(@NotNull Bookmark bookmark) {
    Map<String, String> attrs = bookmark.getAttributes();
    String url = attrs.get("url");
    if (url == null) return null;
    return new BookmarkLocation(url, attrs.get("line"), attrs.get("lineText"));
  }

  @SuppressWarnings("unchecked")
  private static @NotNull PersistentStateComponent<ManagerState> getComponent(@NotNull Project project) {
    return (PersistentStateComponent<ManagerState>)requireNonNull(BookmarksManager.getInstance(project));
  }

  private record BookmarkLocation(@NotNull String url, @Nullable String line, @Nullable String expectedText) {}
}
