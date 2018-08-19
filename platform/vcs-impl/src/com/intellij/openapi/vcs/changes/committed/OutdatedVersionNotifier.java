// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 * todo: use EditorNotifications
 */
public class OutdatedVersionNotifier {
  private static final Logger LOG = Logger.getInstance(OutdatedVersionNotifier.class);

  private final FileEditorManager myFileEditorManager;
  private final CommittedChangesCache myCache;
  private final Project myProject;
  private static final Key<OutdatedRevisionPanel> PANEL_KEY = new Key<>("OutdatedRevisionPanel");
  private volatile boolean myIncomingChangesRequested;

  public OutdatedVersionNotifier(FileEditorManager fileEditorManager,
                                 CommittedChangesCache cache,
                                 MessageBus messageBus, Project project) {
    myFileEditorManager = fileEditorManager;
    myCache = cache;
    myProject = project;
    MessageBusConnection busConnection = messageBus.connect();
    busConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesAdapter() {
      @Override
      public void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges) {
        if (myCache.getCachedIncomingChanges() == null) {
          requestLoadIncomingChanges();
        }
        else {
          updateAllEditorsLater();
        }
      }

      @Override
      public void changesCleared() {
        updateAllEditorsLater();
      }
    });

    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (myCache.getCachedIncomingChanges() == null) {
          requestLoadIncomingChanges();
        }
        else {
          final Pair<CommittedChangeList, Change> pair = myCache.getIncomingChangeList(file);
          if (pair != null) {
            final FileEditor[] fileEditors = source.getEditors(file);
            for(FileEditor editor: fileEditors) {
              initPanel(pair.first, pair.second, editor);
            }
          }
        }
      }
    });
  }

  private void requestLoadIncomingChanges() {
    debug("Requesting load of incoming changes");
    if (!myIncomingChangesRequested) {
      myIncomingChangesRequested = true;
      myCache.loadIncomingChangesAsync(committedChangeLists -> {
        myIncomingChangesRequested = false;
        updateAllEditorsLater();
      }, true);
    }
  }

  private static void debug(@NonNls String message) {
    LOG.debug(message);
  }

  private void updateAllEditorsLater() {
    debug("Queueing update of editors");
    ApplicationManager.getApplication().invokeLater(() -> updateAllEditors(), myProject.getDisposed());
  }

  private void updateAllEditors() {
    if (myCache.getCachedIncomingChanges() == null) {
      requestLoadIncomingChanges();
      return;
    }
    debug("Updating editors");
    final VirtualFile[] files = myFileEditorManager.getOpenFiles();
    for(VirtualFile file: files) {
      final Pair<CommittedChangeList,Change> pair = myCache.getIncomingChangeList(file);
      final FileEditor[] fileEditors = myFileEditorManager.getEditors(file);
      for(FileEditor editor: fileEditors) {
        final OutdatedRevisionPanel oldPanel = editor.getUserData(PANEL_KEY);
        if (pair != null) {
          if (oldPanel != null) {
            oldPanel.setChangeList(pair.first, pair.second);
          }
          else {
            initPanel(pair.first, pair.second, editor);
          }
        }
        else if (oldPanel != null) {
          myFileEditorManager.removeTopComponent(editor, oldPanel);
          editor.putUserData(PANEL_KEY, null);
        }
      }
    }
  }

  private void initPanel(final CommittedChangeList list, final Change c, final FileEditor editor) {
    if (!isIncomingChangesSupported(list)) {
      return;
    }
    final OutdatedRevisionPanel component = new OutdatedRevisionPanel(list, c);
    editor.putUserData(PANEL_KEY, component);
    myFileEditorManager.addTopComponent(editor, component);
  }

  private static class OutdatedRevisionPanel extends EditorNotificationPanel {
    private CommittedChangeList myChangeList;

    public OutdatedRevisionPanel(CommittedChangeList changeList, final Change c) {
      super();
      createActionLabel(VcsBundle.message("outdated.version.show.diff.action"), "Compare.LastVersion");
      createActionLabel(VcsBundle.message("outdated.version.update.project.action"), "Vcs.UpdateProject");
      myChangeList = changeList;
      updateLabelText(c);
    }

    private void updateLabelText(final Change c) {
      String comment = myChangeList.getComment();
      int pos = comment.indexOf("\n");
      if (pos >= 0) {
        comment = comment.substring(0, pos).trim() + "...";
      }
      final String formattedDate = DateFormatUtil.formatPrettyDateTime(myChangeList.getCommitDate());
      final boolean dateIsPretty = ! formattedDate.contains("/");
      final String key = c.getType() == Change.Type.DELETED ? "outdated.version.text.deleted" :
                         (dateIsPretty ? "outdated.version.pretty.date.text" : "outdated.version.text");
      myLabel.setText(VcsBundle.message(key, myChangeList.getCommitterName(), formattedDate, comment));
    }

    public void setChangeList(final CommittedChangeList changeList, final Change c) {
      myChangeList = changeList;
      updateLabelText(c);
    }
  }

  private static boolean isIncomingChangesSupported(@NotNull CommittedChangeList list) {
    CachingCommittedChangesProvider provider = list.getVcs().getCachingCommittedChangesProvider();
    return provider != null && provider.supportsIncomingChanges();
  }
}
