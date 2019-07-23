// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst;

import com.intellij.diff.DiffContext;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.simple.SimpleDiffChange;
import com.intellij.diff.tools.simple.SimpleDiffChangeUi;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffGutterRenderer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LocalTrackerChange;
import com.intellij.openapi.vcs.ex.ExclusionState;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimpleLocalChangeListDiffViewer extends SimpleDiffViewer {
  @NotNull private final LocalChangeListDiffRequest myLocalRequest;
  @NotNull private final String myChangelistId;
  @NotNull private final String myChangelistName;

  private final boolean myAllowExcludeChangesFromCommit;

  private final LocalTrackerDiffUtil.LocalTrackerActionProvider myTrackerActionProvider;
  private ExcludeAllCheckboxPanel myExcludeAllCheckboxPanel;


  public SimpleLocalChangeListDiffViewer(@NotNull DiffContext context,
                                         @NotNull LocalChangeListDiffRequest localRequest) {
    super(context, localRequest.getRequest());
    myLocalRequest = localRequest;
    myChangelistId = localRequest.getChangelistId();
    myChangelistName = localRequest.getChangelistName();

    myAllowExcludeChangesFromCommit = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context);
    myTrackerActionProvider = new MyLocalTrackerActionProvider(this, localRequest, myAllowExcludeChangesFromCommit);

    LocalTrackerDiffUtil.installTrackerListener(this, myLocalRequest);
  }

  @NotNull
  @Override
  public Project getProject() {
    //noinspection ConstantConditions
    return super.getProject();
  }

  @Nullable
  public PartialLocalLineStatusTracker getPartialTracker() {
    return ObjectUtils.tryCast(myLocalRequest.getLineStatusTracker(), PartialLocalLineStatusTracker.class);
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    List<JComponent> titles = DiffUtil.createTextTitles(myRequest, getEditors());
    assert titles.size() == 2;

    myExcludeAllCheckboxPanel = new ExcludeAllCheckboxPanel();
    JPanel titleWithCheckbox = JBUI.Panels.simplePanel(titles.get(1)).addToLeft(myExcludeAllCheckboxPanel);

    return DiffUtil.createSyncHeightComponents(Arrays.asList(titles.get(0), titleWithCheckbox));
  }

  @NotNull
  @Override
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>(super.createEditorPopupActions());
    group.addAll(LocalTrackerDiffUtil.createTrackerActions(myTrackerActionProvider));
    return group;
  }

  @NotNull
  @Override
  protected SimpleDiffChangeUi createUi(@NotNull SimpleDiffChange change) {
    if (change instanceof MySimpleDiffChange) return new MySimpleDiffChangeUi(this, (MySimpleDiffChange)change);
    return super.createUi(change);
  }

  @NotNull
  private Runnable superComputeDifferences(@NotNull ProgressIndicator indicator) {
    return super.computeDifferences(indicator);
  }

  @Override
  @NotNull
  protected Runnable computeDifferences(@NotNull ProgressIndicator indicator) {
    return LocalTrackerDiffUtil.computeDifferences(
      myLocalRequest.getLineStatusTracker(),
      getContent1().getDocument(),
      getContent2().getDocument(),
      myChangelistId,
      myTextDiffProvider,
      indicator,
      new MyLocalTrackerDiffHandler(indicator)
    );
  }

  private class MyLocalTrackerDiffHandler implements LocalTrackerDiffUtil.LocalTrackerDiffHandler {
    @NotNull private final ProgressIndicator myIndicator;

    private MyLocalTrackerDiffHandler(@NotNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @NotNull
    @Override
    public Runnable done(boolean isContentsEqual,
                         @NotNull CharSequence[] texts,
                         @NotNull List<? extends LineFragment> fragments,
                         @NotNull List<LocalTrackerDiffUtil.LineFragmentData> fragmentsData) {
      List<SimpleDiffChange> changes = new ArrayList<>();

      for (int i = 0; i < fragments.size(); i++) {
        LineFragment fragment = fragments.get(i);
        LocalTrackerDiffUtil.LineFragmentData data = fragmentsData.get(i);

        boolean isExcludedFromCommit = data.isExcludedFromCommit();
        boolean isFromActiveChangelist = data.isFromActiveChangelist();
        boolean isSkipped = data.isSkipped();
        boolean isExcluded = data.isExcluded(myAllowExcludeChangesFromCommit);

        changes.add(new MySimpleDiffChange(changes.size(), fragment, isExcluded, isSkipped,
                                           data.getChangelistId(), isFromActiveChangelist,
                                           isExcludedFromCommit));
      }

      return apply(changes, isContentsEqual);
    }

    @NotNull
    @Override
    public Runnable retryLater() {
      scheduleRediff();
      throw new ProcessCanceledException();
    }

    @NotNull
    @Override
    public Runnable fallback() {
      return superComputeDifferences(myIndicator);
    }

    @NotNull
    @Override
    public Runnable fallbackWithProgress() {
      Runnable callback = superComputeDifferences(myIndicator);
      return () -> {
        callback.run();
        getStatusPanel().setBusy(true);
      };
    }

    @NotNull
    @Override
    public Runnable error() {
      return applyNotification(DiffNotifications.createError());
    }
  }

  @Override
  protected void onAfterRediff() {
    super.onAfterRediff();
    myExcludeAllCheckboxPanel.refresh();
  }

  private static class MySimpleDiffChange extends SimpleDiffChange {
    @NotNull private final String myChangelistId;
    private final boolean myIsFromActiveChangelist;
    private final boolean myIsExcludedFromCommit;

    MySimpleDiffChange(int index,
                       @NotNull LineFragment fragment,
                       boolean isExcluded,
                       boolean isSkipped,
                       @NotNull String changelistId,
                       boolean isFromActiveChangelist,
                       boolean isExcludedFromCommit) {
      super(index, fragment, isExcluded, isSkipped);
      myChangelistId = changelistId;
      myIsFromActiveChangelist = isFromActiveChangelist;
      myIsExcludedFromCommit = isExcludedFromCommit;
    }

    @NotNull
    public String getChangelistId() {
      return myChangelistId;
    }

    public boolean isFromActiveChangelist() {
      return myIsFromActiveChangelist;
    }

    public boolean isExcludedFromCommit() {
      return myIsExcludedFromCommit;
    }
  }

  private static class MySimpleDiffChangeUi extends SimpleDiffChangeUi {
    private MySimpleDiffChangeUi(@NotNull SimpleLocalChangeListDiffViewer viewer, @NotNull MySimpleDiffChange change) {
      super(viewer, change);
    }

    @NotNull
    private SimpleLocalChangeListDiffViewer getViewer() {
      return (SimpleLocalChangeListDiffViewer)myViewer;
    }

    @NotNull
    private MySimpleDiffChange getChange() {
      return ((MySimpleDiffChange)myChange);
    }

    @Override
    protected void doInstallActionHighlighters() {
      super.doInstallActionHighlighters();
      if (getViewer().myAllowExcludeChangesFromCommit && getChange().isFromActiveChangelist()) {
        myOperations.add(new ExcludeGutterOperation());
      }
    }

    private class ExcludeGutterOperation extends GutterOperation {
      ExcludeGutterOperation() {
        super(Side.RIGHT);
      }

      @Override
      public GutterIconRenderer createRenderer() {
        if (!getChange().isFromActiveChangelist()) return null;

        final boolean isExcludedFromCommit = getChange().isExcludedFromCommit();
        Icon icon = isExcludedFromCommit ? AllIcons.Diff.GutterCheckBox : AllIcons.Diff.GutterCheckBoxSelected;
        return new DiffGutterRenderer(icon, "Include into commit") {
          @Override
          protected void handleMouseClick() {
            if (!myChange.isValid()) return;

            int line = myChange.getStartLine(Side.RIGHT);
            LocalTrackerDiffUtil.toggleRangeAtLine(getViewer().myTrackerActionProvider, line, isExcludedFromCommit);
          }
        };
      }
    }
  }


  private static class MyLocalTrackerActionProvider extends LocalTrackerDiffUtil.LocalTrackerActionProvider {
    @NotNull private final SimpleLocalChangeListDiffViewer myViewer;

    private MyLocalTrackerActionProvider(@NotNull SimpleLocalChangeListDiffViewer viewer,
                                         @NotNull LocalChangeListDiffRequest localRequest,
                                         boolean allowExcludeChangesFromCommit) {
      super(viewer, localRequest, allowExcludeChangesFromCommit);
      myViewer = viewer;
    }

    @Nullable
    @Override
    public List<LocalTrackerChange> getSelectedTrackerChanges(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      Side side = Side.fromValue(myViewer.getEditors(), editor);
      if (side == null) return null;

      return StreamEx.of(myViewer.getSelectedChanges(side))
        .select(MySimpleDiffChange.class)
        .map(it -> new LocalTrackerChange(it.getStartLine(Side.RIGHT),
                                          it.getEndLine(Side.RIGHT),
                                          it.myChangelistId,
                                          it.myIsExcludedFromCommit))
        .toList();
    }
  }

  private class ExcludeAllCheckboxPanel extends JPanel {
    private final InplaceButton myCheckbox;

    private ExcludeAllCheckboxPanel() {
      myCheckbox = new InplaceButton(null, AllIcons.Diff.GutterCheckBox, e -> toggleState());
      myCheckbox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myCheckbox.setVisible(false);
      add(myCheckbox);

      getEditor2().getGutterComponentEx().addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          ApplicationManager.getApplication().invokeLater(() -> updateLayout(), ModalityState.any());
        }
      });
    }

    @Override
    public void doLayout() {
      Dimension size = myCheckbox.getPreferredSize();
      EditorGutterComponentEx gutter = getEditor2().getGutterComponentEx();
      int y = (getHeight() - size.height) / 2;
      int x = gutter.getIconAreaOffset() + 2; // "+2" from EditorGutterComponentImpl.processIconsRow
      myCheckbox.setBounds(Math.min(getWidth() - AllIcons.Diff.GutterCheckBox.getIconWidth(), x), Math.max(0, y), size.width, size.height);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = myCheckbox.getPreferredSize();
      EditorGutterComponentEx gutter = getEditor2().getGutterComponentEx();
      int gutterWidth = gutter.getLineMarkerFreePaintersAreaOffset();
      return new Dimension(Math.max(gutterWidth + JBUIScale.scale(2), size.width), size.height);
    }

    private void updateLayout() {
      invalidate();
      myPanel.validate();
      myPanel.repaint();
    }

    private void toggleState() {
      PartialLocalLineStatusTracker tracker = getPartialTracker();
      if (tracker != null && tracker.isValid()) {
        ExclusionState exclusionState = tracker.getExcludedFromCommitState(myChangelistId);
        getPartialTracker().setExcludedFromCommit(myChangelistId, exclusionState == ExclusionState.ALL_INCLUDED);
        refresh();
        rediff();
      }
    }

    public void refresh() {
      Icon icon = getIcon();
      if (icon != null) {
        myCheckbox.setIcon(icon);
        myCheckbox.setVisible(true);
      }
      else {
        myCheckbox.setVisible(false);
      }
    }

    @Nullable
    private Icon getIcon() {
      if (!myAllowExcludeChangesFromCommit) return null;

      PartialLocalLineStatusTracker tracker = getPartialTracker();
      if (tracker == null || !tracker.isValid()) return null;

      ExclusionState exclusionState = tracker.getExcludedFromCommitState(myChangelistId);
      switch (exclusionState) {
        case ALL_INCLUDED:
          return AllIcons.Diff.GutterCheckBoxSelected;
        case ALL_EXCLUDED:
          return AllIcons.Diff.GutterCheckBox;
        case PARTIALLY:
          return AllIcons.Diff.GutterCheckBoxIndeterminate;
        case NO_CHANGES:
        default:
          return null;
      }
    }
  }
}
