// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.VcsBundle.message;

public class OutdatedVersionNotifier extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> PANEL_KEY = new Key<>("OutdatedVersionNotifier");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return PANEL_KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    CommittedChangesCache cache = CommittedChangesCache.getInstance(project);
    Pair<CommittedChangeList, Change> incomingData = cache.getIncomingChangeList(file);
    if (incomingData == null) return null;

    CommittedChangeList incomingChangeList = incomingData.first;
    Change incomingChange = incomingData.second;
    if (!isIncomingChangesSupported(incomingChangeList)) return null;

    return createOutdatedVersionPanel(incomingChangeList, incomingChange);
  }

  @NotNull
  private static EditorNotificationPanel createOutdatedVersionPanel(@NotNull CommittedChangeList changeList, @NotNull Change change) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.createActionLabel(message("outdated.version.show.diff.action"), "Compare.LastVersion");
    panel.createActionLabel(message("outdated.version.update.project.action"), "Vcs.UpdateProject");
    panel.setText(getOutdatedVersionText(changeList, change));
    return panel;
  }

  @NotNull
  private static String getOutdatedVersionText(@NotNull CommittedChangeList changeList, @NotNull Change change) {
    String comment = changeList.getComment();
    int pos = comment.indexOf("\n");
    if (pos >= 0) {
      comment = comment.substring(0, pos).trim() + "...";
    }
    final String formattedDate = DateFormatUtil.formatPrettyDateTime(changeList.getCommitDate());
    final boolean dateIsPretty = !formattedDate.contains("/");
    final String key = change.getType() == Change.Type.DELETED ? "outdated.version.text.deleted" :
                       (dateIsPretty ? "outdated.version.pretty.date.text" : "outdated.version.text");
    return message(key, changeList.getCommitterName(), formattedDate, comment);
  }

  private static boolean isIncomingChangesSupported(@NotNull CommittedChangeList list) {
    CachingCommittedChangesProvider provider = list.getVcs().getCachingCommittedChangesProvider();
    return provider != null && provider.supportsIncomingChanges();
  }

  public static class IncomingChangesListener implements CommittedChangesListener {
    @NotNull private final Project myProject;

    public IncomingChangesListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void incomingChangesUpdated(@Nullable List<CommittedChangeList> receivedChanges) {
      CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);

      if (cache.getCachedIncomingChanges() != null) {
        EditorNotifications.getInstance(myProject).updateAllNotifications();
      }
      else {
        cache.hasCachesForAnyRoot(hasCaches -> {
          if (!hasCaches) return;

          // we do not use `consumer` as `incomingChangesUpdated` will be fired again after incoming changes loading
          cache.loadIncomingChangesAsync(null, true);
        });
      }
    }

    @Override
    public void changesCleared() {
      EditorNotifications.getInstance(myProject).updateAllNotifications();
    }
  }
}
