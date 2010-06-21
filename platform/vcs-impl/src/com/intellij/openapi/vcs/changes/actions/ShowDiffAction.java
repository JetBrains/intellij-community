/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class ShowDiffAction extends AnAction implements DumbAware {
  private static final String ourText = ActionsBundle.actionText("ChangesView.Diff");

  public ShowDiffAction() {
    super(ourText,
          ActionsBundle.actionDescription("ChangesView.Diff"),
          IconLoader.getIcon("/actions/diff.png"));
  }

  public void update(AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && canShowDiff(changes));
  }

  private static boolean canShowDiff(Change[] changes) {
    if (changes == null || changes.length == 0) return false;
    return !ChangesUtil.getFilePath(changes [0]).isDirectory();
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (project == null || changes == null) return;

    final boolean needsConvertion = checkIfThereAreFakeRevisions(project, changes);
    final List<Change> changesInList = e.getData(VcsDataKeys.CHANGES_IN_LIST_KEY);

    // this trick is essential since we are under some conditions to refresh changes;
    // but we can only rely on callback after refresh
    final Runnable performer = new Runnable() {
      public void run() {
        Change[] convertedChanges;
        if (needsConvertion) {
          convertedChanges = loadFakeRevisions(project, changes);
        } else {
          convertedChanges = changes;
        }

        if (convertedChanges == null || convertedChanges.length == 0) {
          return;
        }

        List<Change> changesInListCopy = changesInList;

        int index = 0;
        if (convertedChanges.length == 1) {
          final Change selectedChange = convertedChanges[0];
          ChangeList changeList = ((ChangeListManagerImpl) ChangeListManager.getInstance(project)).getIdentityChangeList(selectedChange);
          if (changeList != null) {
            if (changesInListCopy == null) {
              changesInListCopy = new ArrayList<Change>(changeList.getChanges());
              Collections.sort(changesInListCopy, new Comparator<Change>() {
                public int compare(final Change o1, final Change o2) {
                  return ChangesUtil.getFilePath(o1).getName().compareToIgnoreCase(ChangesUtil.getFilePath(o2).getName());
                }
              });
            }
            convertedChanges = changesInListCopy.toArray(new Change[changesInListCopy.size()]);
            for(int i=0; i<convertedChanges.length; i++) {
              if (convertedChanges [i] == selectedChange) {
                index = i;
                break;
              }
            }
          }
        }

        showDiffForChange(convertedChanges, index, project);
      }
    };

    if (needsConvertion) {
      ChangeListManager.getInstance(project).invokeAfterUpdate(performer, InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE, ourText, ModalityState.current());
    }  else {
      performer.run();
    }
  }

  public static void showDiffForChange(final Change[] changes, final int index, final Project project) {
    showDiffForChange(changes, index, project, DiffExtendUIFactory.NONE, true);
  }

  private boolean checkIfThereAreFakeRevisions(final Project project, final Change[] changes) {
    boolean needsConvertion = false;
    for(Change change: changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(beforeRevision.getFile());
        needsConvertion = true;
      }
      if (afterRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(afterRevision.getFile());
        needsConvertion = true;
      }
    }
    return needsConvertion;
  }

  @Nullable
  private static Change[] loadFakeRevisions(final Project project, final Change[] changes) {
    List<Change> matchingChanges = new ArrayList<Change>();
    for(Change change: changes) {
      matchingChanges.addAll(ChangeListManager.getInstance(project).getChangesIn(ChangesUtil.getFilePath(change)));
    }
    return matchingChanges.toArray(new Change[matchingChanges.size()]);
  }

  public interface DiffExtendUIFactory {
    DiffExtendUIFactory NONE = new DiffExtendUIFactory() {
      public List<? extends AnAction> createActions(Change change) {
        return Collections.emptyList();
      }

      @Nullable
      public JComponent createBottomComponent() {
        return null;
      }
    };
    List<? extends AnAction> createActions(Change change);

    @Nullable
    JComponent createBottomComponent();
  }

  public static void showDiffForChange(final Iterable<Change> changes, final Condition<Change> selectionChecker,
                                       final Project project, @NotNull DiffExtendUIFactory actionsFactory, final boolean showFrame) {
    int cnt = 0;
    int newIndex = -1;
    final List<Change> changeList = new ArrayList<Change>();
    for (Change change : changes) {
      if (! directoryOrBinary(change)) {
        changeList.add(change);
        if ((newIndex == -1) && selectionChecker.value(change)) {
          newIndex = cnt;
        }
        ++ cnt;
      }
    }
    if (changeList.isEmpty()) {
      return;
    }
    if (newIndex < 0) {
      newIndex = 0;
    }
    
    showDiffImpl(project, ObjectsConvertor.convert(changeList,
            new Convertor<Change, DiffRequestPresentable>() {
              public ChangeDiffRequestPresentable convert(Change o) {
                return new ChangeDiffRequestPresentable(project, o);
              }
            }), newIndex, actionsFactory, showFrame);
  }

  public static void showDiffForChange(Change[] changes, int index, final Project project, @NotNull DiffExtendUIFactory actionsFactory,
                                       final boolean showFrame) {
    Change selectedChange = changes [index];
    final List<Change> changeList = filterDirectoryAndBinaryChanges(changes);
    if (changeList.isEmpty()) {
      return;
    }
    index = 0;
    for (int i = 0; i < changeList.size(); i++) {
      if (changeList.get(i) == selectedChange) {
        index = i;
        break;
      }
    }
    showDiffImpl(project, ObjectsConvertor.convert(changeList,
            new Convertor<Change, DiffRequestPresentable>() {
              public ChangeDiffRequestPresentable convert(Change o) {
                return new ChangeDiffRequestPresentable(project, o);
              }
            }), index, actionsFactory, showFrame);
  }

  public static void showDiffImpl(final Project project, List<DiffRequestPresentable> changeList, int index, DiffExtendUIFactory actionsFactory, boolean showFrame) {
    final ChangeDiffRequest request = new ChangeDiffRequest(project, changeList, actionsFactory, showFrame);
    
    final DiffTool tool = DiffManager.getInstance().getDiffTool();
    if (! request.quickCheckHaveStuff()) return;
    final DiffRequest simpleRequest = request.init(index);
    if (simpleRequest != null) {
      tool.show(simpleRequest);
    }
  }

  private static boolean directoryOrBinary(final Change change) {
    // todo instead for repository tab, filter directories (? ask remotely ? non leaf nodes)
    /*if ((change.getBeforeRevision() instanceof BinaryContentRevision) || (change.getAfterRevision() instanceof BinaryContentRevision)) {
      changesList.remove(i);
      continue;
    }*/
    final FilePath path = ChangesUtil.getFilePath(change);
    if (path.isDirectory()) {
      return true;
    }
    final FileType type = path.getFileType();
    if ((! FileTypes.UNKNOWN.equals(type)) && (type.isBinary())) {
      return true;
    }
    return false;
  }

  private static List<Change> filterDirectoryAndBinaryChanges(final Change[] changes) {
    final ArrayList<Change> changesList = new ArrayList<Change>();
    Collections.addAll(changesList, changes);
    for(int i=changesList.size()-1; i >= 0; i--) {
      final Change change = changesList.get(i);
      if (directoryOrBinary(change)) {
        changesList.remove(i);
      }
    }
    return changesList;
  }

  private static boolean checkNotifyBinaryDiff(final Change selectedChange) {
    final ContentRevision beforeRevision = selectedChange.getBeforeRevision();
    final ContentRevision afterRevision = selectedChange.getAfterRevision();
    if (beforeRevision instanceof BinaryContentRevision &&
        afterRevision instanceof BinaryContentRevision) {
      try {
        byte[] beforeContent = ((BinaryContentRevision)beforeRevision).getBinaryContent();
        byte[] afterContent = ((BinaryContentRevision)afterRevision).getBinaryContent();
        if (Arrays.equals(beforeContent, afterContent)) {
          Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.identical"), VcsBundle.message("message.title.diff"));
        } else {
          Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.different"), VcsBundle.message("message.title.diff"));
        }
      }
      catch (VcsException e) {
        Messages.showInfoMessage(e.getMessage(), VcsBundle.message("message.title.diff"));
      }
      return true;
    }
    return false;
  }
}
