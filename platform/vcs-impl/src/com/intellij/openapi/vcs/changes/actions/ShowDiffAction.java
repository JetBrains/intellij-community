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

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.external.BinaryDiffTool;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BeforeAfter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public class ShowDiffAction extends AnAction implements DumbAware {
  private static final String ourText = ActionsBundle.actionText("ChangesView.Diff");

  public ShowDiffAction() {
    super(ourText,
          ActionsBundle.actionDescription("ChangesView.Diff"),
          AllIcons.Actions.Diff);
  }

  public void update(AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && canShowDiff(changes));
  }

  protected static boolean canShowDiff(Change[] changes) {
    if (changes == null || changes.length == 0) return false;
    return !ChangesUtil.getFilePath(changes [0]).isDirectory() || changes[0].hasOtherLayers();
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes == null) return;

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
    showDiffForChange(changes, index, project, new ShowDiffUIContext(true));
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

  public static void showDiffForChange(final Iterable<Change> changes, final Condition<Change> selectionChecker,
                                       final Project project, @NotNull ShowDiffUIContext context) {
    int cnt = 0;
    int newIndex = -1;
    final List<Change> changeList = new ArrayList<Change>();
    for (Change change : changes) {
      if (! directoryOrBinary(change)) {    //todo
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

    showDiffImpl(project, ObjectsConvertor.convert(changeList, new ChangeForDiffConvertor(project, true), ObjectsConvertor.NOT_NULL), newIndex, context);
  }

  public static void showDiffForChange(final Change[] changes, int index, final Project project, @NotNull ShowDiffUIContext context) {
    final Change selected = index >= 0 ? changes[index] : null;
    /*if (isBinaryDiff(project, changes, index)) {
      showBinaryDiff(project, changes[index]);
      return;
    }*/
    showDiffForChange(Arrays.asList(changes), new Condition<Change>() {
                        @Override
                        public boolean value(final Change change) {
                          return selected == null ? false : selected.equals(change);
                        }
                      }, project, context);
  }

  private static FileContent createBinaryFileContent(final Project project, final ContentRevision contentRevision, final String fileName)
    throws VcsException, IOException {
    final FileContent fileContent;
    if (contentRevision == null) {
      fileContent = FileContent.createFromTempFile(project,
                                              fileName,
                                              fileName,
                                              ArrayUtil.EMPTY_BYTE_ARRAY);
    } else {
      byte[] content = ((BinaryContentRevision)contentRevision).getBinaryContent();
      fileContent = FileContent.createFromTempFile(project,
                                              contentRevision.getFile().getName(),
                                              contentRevision.getFile().getName(),
                                              content == null ? ArrayUtil.EMPTY_BYTE_ARRAY : content);
    }
    return fileContent;
  }

  private static SimpleDiffRequest createBinaryDiffRequest(final Project project, final Change change) throws VcsException {
    final FilePath filePath = ChangesUtil.getFilePath(change);
    final SimpleDiffRequest request = new SimpleDiffRequest(project, filePath.getPath());
    try {
      request.setContents(createBinaryFileContent(project, change.getBeforeRevision(), filePath.getName()),
                          createBinaryFileContent(project, change.getAfterRevision(), filePath.getName()));
      return request;
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  public static BeforeAfter<DiffContent> createBinaryDiffContents(final Project project, final Change change) throws VcsException {
    final FilePath filePath = ChangesUtil.getFilePath(change);
    try {
      return new BeforeAfter<DiffContent>(createBinaryFileContent(project, change.getBeforeRevision(), filePath.getName()),
                          createBinaryFileContent(project, change.getAfterRevision(), filePath.getName()));
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private static void showBinaryDiff(Project project, Change change) {
    try {
      final SimpleDiffRequest request = createBinaryDiffRequest(project, change);
      if (DiffManager.getInstance().getDiffTool().canShow(request)) {
        DiffManager.getInstance().getDiffTool().show(request);
      }
    }
    catch (VcsException e) {
      Messages.showWarningDialog(e.getMessage(), "Show Diff");
    }
  }

  private static boolean isBinaryDiff(Project project, Change[] changes, int index) {
    if (index >= 0 && index < changes.length) {
      final Change change = changes[index];
      return isBinaryChangeAndCanShow(project, change);
    }
    return false;
  }

  public static boolean isBinaryChange(Change change) {
    if (change.hasOtherLayers()) return false;  // +-
    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    return (aRev == null || aRev instanceof BinaryContentRevision)
           && (bRev == null || bRev instanceof BinaryContentRevision);
  }

  public static boolean isBinaryChangeAndCanShow(Project project, Change change) {
    // todo bug here would appear when there would be another diff providers for bimary revisions
    return isBinaryChange(change) && (change.getAfterRevision() == null || BinaryDiffTool.canShow(project, change.getVirtualFile()));
  }

  public static void showDiffImpl(final Project project, @NotNull List<DiffRequestPresentable> changeList, int index,
                                  @NotNull final ShowDiffUIContext context) {
    final ChangeDiffRequest request = new ChangeDiffRequest(project, changeList, context.getActionsFactory(), context.isShowFrame());
    final DiffTool tool = DiffManager.getInstance().getDiffTool();
    final DiffRequest simpleRequest;
    try {
      request.quickCheckHaveStuff();
      simpleRequest = request.init(index);
    }
    catch (VcsException e) {
      Messages.showWarningDialog(e.getMessage(), "Show Diff");
      return;
    }

    if (simpleRequest != null) {
      final DiffNavigationContext navigationContext = context.getDiffNavigationContext();
      if (navigationContext != null) {
        simpleRequest.passForDataContext(DiffTool.SCROLL_TO_LINE, navigationContext);
      }
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
      return ! change.hasOtherLayers();
    }
    /*final FileType type = path.getFileType();
    if ((! FileTypes.UNKNOWN.equals(type)) && (type.isBinary())) {
      return true;
    }*/
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
