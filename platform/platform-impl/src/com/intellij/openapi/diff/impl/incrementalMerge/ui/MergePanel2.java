/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.actions.ToggleAutoScrollAction;
import com.intellij.openapi.diff.impl.*;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeCounter;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeList;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl;
import com.intellij.openapi.diff.impl.mergeTool.MergeTool;
import com.intellij.openapi.diff.impl.settings.DiffMergeEditorSetting;
import com.intellij.openapi.diff.impl.settings.DiffMergeSettings;
import com.intellij.openapi.diff.impl.settings.DiffMergeSettingsAction;
import com.intellij.openapi.diff.impl.settings.MergeToolSettings;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.diff.impl.util.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.containers.Convertor;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class MergePanel2 implements DiffViewer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2");
  private final DiffPanelOuterComponent myPanel;
  private DiffRequest myData;
  private MergeList myMergeList;
  private boolean myDuringCreation = false;
  private final SyncScrollSupport myScrollSupport = new SyncScrollSupport();
  private final DiffDivider[] myDividers = {new DiffDivider(FragmentSide.SIDE2), new DiffDivider(FragmentSide.SIDE1)};
  private boolean myScrollToFirstDiff = true;

  private final LabeledComponent[] myEditorsPanels = new LabeledComponent[EDITORS_COUNT];
  public static final int EDITORS_COUNT = 3;
  private final DividersRepainter myDividersRepainter = new DividersRepainter();
  private StatusUpdater myStatusUpdater;
  private final DialogBuilder myBuilder;
  private final MyDataProvider myProvider;

  public MergePanel2(DialogBuilder builder, @NotNull Disposable parent) {
    UsageTrigger.trigger("diff.MergePanel2");

    ArrayList<EditorPlace> editorPlaces = new ArrayList<>();
    EditorPlace.EditorListener placeListener = new EditorPlace.EditorListener() {
      public void onEditorCreated(EditorPlace place) {
        if (myDuringCreation) return;
        disposeMergeList();
        myDuringCreation = true;
        try {
          tryInitView();
        }
        finally {
          myDuringCreation = false;
        }
      }

      public void onEditorReleased(Editor releasedEditor) {
        LOG.assertTrue(!myDuringCreation);
        disposeMergeList();
      }
    };
    for (int i = 0; i < EDITORS_COUNT; i++) {
      EditorPlace editorPlace = new EditorPlace(new DiffEditorState(i), indexToColumn(i), this);
      Disposer.register(parent, editorPlace);
      editorPlaces.add(editorPlace);
      editorPlace.addListener(placeListener);
      myEditorsPanels[i] = new LabeledComponent();
      myEditorsPanels[i].setLabelLocation(BorderLayout.NORTH);
      myEditorsPanels[i].setComponent(editorPlace);
    }
    FontSizeSynchronizer.attachTo(editorPlaces);
    myPanel = new DiffPanelOuterComponent(TextDiffType.MERGE_TYPES, createToolbar());
    myPanel.insertDiffComponent(new ThreePanels(myEditorsPanels, myDividers), new MyScrollingPanel());
    myProvider = new MyDataProvider();
    myPanel.setDataProvider(myProvider);
    myBuilder = builder;
  }

  /**
   * Convert legacy-style editor (or panel) number to the {@link MergePanelColumn}.
   * @param i 0, 1 or 2
   * @return  Left, base or right, respectively.
   */
  private static MergePanelColumn indexToColumn(int i) {
    switch (i) {
      case 0: return MergePanelColumn.LEFT;
      case 1: return MergePanelColumn.BASE;
      case 2: return MergePanelColumn.RIGHT;
      default: throw new IllegalStateException("Incorrect value for a merge column: " + i);
    }
  }

  @NotNull
  private DiffRequest.ToolbarAddons createToolbar() {
    return new DiffRequest.ToolbarAddons() {
      public void customize(DiffToolbar toolbar) {
        toolbar.addAction(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_DIFF));
        toolbar.addAction(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF));
        toolbar.addSeparator();
        toolbar.addAction(new OpenPartialDiffAction(0, 1, AllIcons.Diff.Compare3LeftMiddle));
        toolbar.addAction(new OpenPartialDiffAction(1, 2, AllIcons.Diff.Compare3MiddleRight));
        toolbar.addAction(new OpenPartialDiffAction(0, 2, AllIcons.Diff.Compare3LeftRight));
        toolbar.addSeparator();
        toolbar.addAction(new ApplyNonConflicts(myPanel));
        toolbar.addSeparator();
        toolbar.addAction(new ToggleAutoScrollAction());
        Project project = myData.getProject();
        if (project != null) {
          toolbar.addSeparator();
          toolbar.addAction(new DiffMergeSettingsAction(getEditors(), ServiceManager.getService(project, MergeToolSettings.class)));
        }
      }
    };
  }

  @NotNull
  private Collection<Editor> getEditors() {
    Collection<Editor> editors = new ArrayList<>(3);
    for (EditorPlace place : getEditorPlaces()) {
      editors.add(place.getEditor());
    }
    return editors;
  }

  @NotNull
  private Collection<EditorPlace> getEditorPlaces() {
    Collection<EditorPlace> editorPlaces = new ArrayList<>(3);
    for (LabeledComponent editorsPanel : myEditorsPanels) {
      editorPlaces.add((EditorPlace) editorsPanel.getComponent());
    }
    return editorPlaces;
  }

  public void setScrollToFirstDiff(final boolean scrollToFirstDiff) {
    myScrollToFirstDiff = scrollToFirstDiff;
  }

  /**
   * @deprecated Because it references by index.
   */
  @Nullable
  @Deprecated
  public Editor getEditor(int index) {
    return getEditorPlace(index).getEditor();
  }

  public FileType getContentType() {
    return myData == null ? FileTypes.PLAIN_TEXT : getContentType(myData);
  }

  /**
   * @deprecated Because it references by index.
   */
  @Deprecated
  public String getVersionTitle(int index) {
    return myEditorsPanels[index].getRawText();
  }

  /**
   * @deprecated Because it references by index.
   */
  @Deprecated
  public EditorPlace getEditorPlace(int index) {
    return (EditorPlace)myEditorsPanels[index].getComponent();
  }

  private void createMergeList() {
    if (myData == null) return;
    DiffContent[] contents = myData.getContents();
    for (int i = 0; i < EDITORS_COUNT; i++) {
      EditorPlace editorPlace = getEditorPlace(i);
      editorPlace.setDocument(contents[i].getDocument());
      setHighlighterSettings(null, editorPlace);
    }
    tryInitView();
  }

  private void tryInitView() {
    if (!hasAllEditors()) return;
    if (myMergeList != null) return;
    myMergeList = MergeList.create(myData);
    myMergeList.addListener(myDividersRepainter);
    myStatusUpdater = StatusUpdater.install(myMergeList, myPanel);
    Editor left = getEditor(0);
    Editor base = getEditor(1);
    Editor right = getEditor(2);

    setupHighlighterSettings(left, base, right);

    myMergeList.setMarkups(left, base, right);
    EditingSides[] sides = {getFirstEditingSide(), getSecondEditingSide()};
    myScrollSupport.install(sides);
    for (int i = 0; i < myDividers.length; i++) {
      myDividers[i].listenEditors(sides[i]);
    }
    if (myScrollToFirstDiff) {
      myPanel.requestScrollEditors();
    }
    if (myMergeList.getErrorMessage() != null) {
      myPanel.insertTopComponent(new EditorNotificationPanel() {
        {
          myLabel.setText(myMergeList.getErrorMessage());
        }
      });
    }
  }

  @NotNull
  EditingSides getFirstEditingSide() {
    return new MyEditingSides(FragmentSide.SIDE1);
  }

  @NotNull
  EditingSides getSecondEditingSide() {
    return new MyEditingSides(FragmentSide.SIDE2);
  }

  public void setAutoScrollEnabled(boolean enabled) {
    myScrollSupport.setEnabled(enabled);
  }

  public boolean isAutoScrollEnabled() {
    return myScrollSupport.isEnabled();
  }

  private void setupHighlighterSettings(Editor left, Editor base, Editor right) {
    Editor[] editors = new Editor[]{left, base, right};
    DiffContent[] contents = myData.getContents();
    FileType[] types = DiffUtil.chooseContentTypes(contents);

    VirtualFile fallbackFile = contents[1].getFile();
    FileType fallbackType = contents[1].getContentType();

    for (int i = 0; i < 3; i++) {
      Editor editor = editors[i];
      DiffContent content = contents[i];

      EditorHighlighter highlighter =
        createHighlighter(types[i], content.getFile(), fallbackFile, fallbackType, myData.getProject()).createHighlighter();
      if (highlighter != null) {
        ((EditorEx)editor).setHighlighter(highlighter);
      }
    }
  }

  private static DiffHighlighterFactory createHighlighter(FileType contentType,
                                                          VirtualFile file,
                                                          VirtualFile otherFile,
                                                          FileType otherType,
                                                          Project project) {
    if (file == null) file = otherFile;
    if (contentType == null) contentType = otherType;

    return new DiffHighlighterFactoryImpl(contentType, file, project);
  }

  public void setHighlighterSettings(@Nullable EditorColorsScheme settings) {
    for (EditorPlace place : getEditorPlaces()) {
      setHighlighterSettings(settings, place);
    }
  }

  private void setHighlighterSettings(@Nullable EditorColorsScheme settings, @NotNull EditorPlace place) {
    if (settings == null) {
      settings = EditorColorsManager.getInstance().getGlobalScheme();
    }
    Editor editor = place.getEditor();
    DiffEditorState editorState = place.getState();
    if (editor != null) {
      ((EditorEx)editor).setHighlighter(EditorHighlighterFactory.getInstance().
        createEditorHighlighter(editorState.getFileType(), settings, editorState.getProject()));
    }
  }

  private static void initEditorSettings(@NotNull Editor editor) {
    Project project = editor.getProject();
    DiffMergeSettings settings = project == null ? null : ServiceManager.getService(project, MergeToolSettings.class);
    for (DiffMergeEditorSetting property : DiffMergeEditorSetting.values()) {
      property.apply(editor, settings == null ? property.getDefault() : settings.getPreference(property));
    }
    editor.getSettings().setLineMarkerAreaShown(true);
  }

  private void disposeMergeList() {
    if (myMergeList == null) return;
    if (myStatusUpdater != null) {
      myStatusUpdater.dispose(myMergeList);
      myStatusUpdater = null;
    }
    myMergeList.removeListener(myDividersRepainter);

    myMergeList = null;
    for (DiffDivider myDivider : myDividers) {
      myDivider.stopListenEditors();
    }
  }

  @Override
  public boolean canShowRequest(DiffRequest request) {
    return MergeTool.canShowRequest(request);
  }

  public void setDiffRequest(DiffRequest data) {
    setTitle(data.getWindowTitle());
    disposeMergeList();
    for (int i = 0; i < EDITORS_COUNT; i++) {
      getEditorPlace(i).setDocument(null);
    }
    LOG.assertTrue(!myDuringCreation);
    myDuringCreation = true;
    myProvider.putData(data.getGenericData());
    try {
      myData = data;
      String[] titles = myData.getContentTitles();
      for (int i = 0; i < myEditorsPanels.length; i++) {
        LabeledComponent editorsPanel = myEditorsPanels[i];
        editorsPanel.getLabel().setText(titles[i].isEmpty() ? " " : titles[i]);
      }
      createMergeList();
      data.customizeToolbar(myPanel.resetToolbar());
      myPanel.registerToolbarActions();
      if ( data instanceof MergeRequestImpl && myBuilder != null){
        Convertor<DialogWrapper, Boolean> preOkHook = dialog -> {
          ChangeCounter counter = ChangeCounter.getOrCreate(myMergeList);
          int changes = counter.getChangeCounter();
          int conflicts = counter.getConflictCounter();
          if (changes == 0 && conflicts == 0) return true;
          return Messages.showYesNoDialog(dialog.getRootPane(),
                                          DiffBundle.message("merge.dialog.apply.partially.resolved.changes.confirmation.message", changes, conflicts),
                                          DiffBundle.message("apply.partially.resolved.merge.dialog.title"),
                                          Messages.getQuestionIcon()) == Messages.YES;
        };
        ((MergeRequestImpl)data).setActions(myBuilder, this, preOkHook);
      }
    }
    finally {
      myDuringCreation = false;
    }
  }

  private void setTitle(String windowTitle) {
    JDialog parent = getDialogWrapperParent();
    if (parent == null) return;
    parent.setTitle(windowTitle);
  }

  @Nullable
  private JDialog getDialogWrapperParent() {
    Component panel = myPanel;
    while (panel != null){
      if (panel instanceof JDialog) return (JDialog)panel;
      panel = panel.getParent();
    }
    return null;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return getEditorPlace(1).getContentComponent();
  }

  public int getContentsNumber() {
    return 3;
  }

  @Override
  public boolean acceptsType(DiffViewerType type) {
    return DiffViewerType.merge.equals(type);
  }

  private boolean hasAllEditors() {
    for (int i = 0; i < EDITORS_COUNT; i++) {
      if (getEditor(i) == null) return false;
    }
    return true;
  }

  @Nullable
  public MergeRequestImpl getMergeRequest() {
    return (MergeRequestImpl)(myData instanceof MergeRequestImpl ? myData : null);
  }

  private class MyEditingSides implements EditingSides {
    private final FragmentSide mySide;

    private MyEditingSides(FragmentSide side) {
      mySide = side;
    }

    @Nullable
    public Editor getEditor(FragmentSide side) {
      return MergePanel2.this.getEditor(mySide.getIndex() + side.getIndex());
    }

    public LineBlocks getLineBlocks() {
      return myMergeList.getChanges(mySide).getLineBlocks();
    }
  }

  private class MyScrollingPanel implements DiffPanelOuterComponent.ScrollingPanel {
    public void scrollEditors() {
      Editor centerEditor = getEditor(1);
      JComponent centerComponent = centerEditor.getContentComponent();
      if (centerComponent.isShowing()) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(centerComponent, true);
        });
      }
      int[] toLeft = getPrimaryBeginnings(myDividers[0].getPaint());
      int[] toRight = getPrimaryBeginnings(myDividers[1].getPaint());
      int line;
      if (toLeft.length > 0 && toRight.length > 0) {
        line = Math.min(toLeft[0], toRight[0]);
      }
      else if (toLeft.length > 0) {
        line = toLeft[0];
      }
      else if (toRight.length > 0) {
        line = toRight[0];
      }
      else {
        return;
      }
      SyncScrollSupport.scrollEditor(centerEditor, line);
    }

    private int[] getPrimaryBeginnings(DiffDividerPaint paint) {
      FragmentSide primarySide = paint.getLeftSide();
      LOG.assertTrue(getEditor(1) == paint.getSides().getEditor(primarySide));
      return paint.getSides().getLineBlocks().getBeginnings(primarySide, true);
    }
  }

  class DiffEditorState {
    private final int myIndex;
    private Document myDocument;

    private DiffEditorState(int index) {
      myIndex = index;
    }

    public void setDocument(Document document) {
      myDocument = document;
    }

    public Document getDocument() {
      return myDocument;
    }

    @Nullable
    public EditorEx createEditor() {
      Document document = getDocument();
      if (document == null) return null;
      Project project = myData.getProject();
      EditorEx editor = DiffUtil.createEditor(document, project, myIndex != 1);

      if (editor == null) return editor;
      //FileType type = getFileType();
      //editor.setHighlighter(HighlighterFactory.createHighlighter(project, type));
      if (myIndex == 0) editor.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
      if (myIndex != 1) ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
      editor.getSettings().setFoldingOutlineShown(false);
      editor.getFoldingModel().setFoldingEnabled(false);
      editor.getSettings().setLineMarkerAreaShown(false);
      editor.getSettings().setFoldingOutlineShown(false);
      editor.getGutterComponentEx().setShowDefaultGutterPopup(false);
      initEditorSettings(editor);

      return editor;
    }

    public FileType getFileType() {
      return getContentType();
    }

    @Nullable
    public Project getProject() {
      return myData == null ? null : myData.getProject();
    }
  }

  private static FileType getContentType(DiffRequest diffData) {
    FileType contentType = diffData.getContents()[1].getContentType();
    if (contentType == null) contentType = FileTypes.PLAIN_TEXT;
    return contentType;
  }

  private class MyDataProvider extends GenericDataProvider {
    public Object getData(String dataId) {
      if (FocusDiffSide.DATA_KEY.is(dataId)) {
        int index = getFocusedEditorIndex();
        if (index < 0) return null;
        switch (index) {
          case 0:
            return new BranchFocusedSide(FragmentSide.SIDE1);
          case 1:
            return new MergeFocusedSide();
          case 2:
            return new BranchFocusedSide(FragmentSide.SIDE2);
        }
      }
      else if (PlatformDataKeys.DIFF_VIEWER.is(dataId)) return MergePanel2.this;
      return super.getData(dataId);
    }

    private int getFocusedEditorIndex() {
      for (int i = 0; i < EDITORS_COUNT; i++) {
        Editor editor = getEditor(i);
        if (editor == null) continue;
        if (editor.getContentComponent().isFocusOwner()) return i;
      }
      return -1;
    }
  }

  private class BranchFocusedSide implements FocusDiffSide {
    private final FragmentSide mySide;

    private BranchFocusedSide(FragmentSide side) {
      mySide = side;
    }

    @Nullable
    public Editor getEditor() {
      return MergePanel2.this.getEditor(mySide.getMergeIndex());
    }

    public int[] getFragmentStartingLines() {
      return myMergeList.getChanges(mySide).getNonAppliedLineBlocks().getBeginnings(MergeList.BRANCH_SIDE);
    }
  }

  private class MergeFocusedSide implements FocusDiffSide {
    public Editor getEditor() {
      return MergePanel2.this.getEditor(1);
    }

    public int[] getFragmentStartingLines() {
      TIntHashSet beginnings = new TIntHashSet();
      if (myMergeList != null) {
        for (int i = 0; i < 2; i++) {
          FragmentSide branchSide = FragmentSide.fromIndex(i);
          beginnings.addAll(myMergeList.getChanges(branchSide).getNonAppliedLineBlocks().getBeginnings(MergeList.BASE_SIDE));
        }
      }
      int[] result = beginnings.toArray();
      Arrays.sort(result);
      return result;
    }
  }

  @Nullable
  public static MergePanel2 fromDataContext(DataContext dataContext) {
    DiffViewer diffComponent = PlatformDataKeys.DIFF_VIEWER.getData(dataContext);
    return diffComponent instanceof MergePanel2 ? (MergePanel2)diffComponent : null;
  }

  public MergeList getMergeList() {
    return myMergeList;
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    for (Editor editor : getEditors()) {
      if (editor != null) {
        ((EditorEx)editor).setColorsScheme(scheme);
      }
    }
    myPanel.setColorScheme(scheme);
  }

  private class DividersRepainter implements ChangeList.Listener {

    @Override
    public void onChangeApplied(ChangeList source) {
      FragmentSide side = myMergeList.getSideOf(source);
      myDividers[side.getIndex()].repaint();
    }

    public void onChangeRemoved(ChangeList source) {
      FragmentSide side = myMergeList.getSideOf(source);
      myDividers[side.getIndex()].repaint();
    }
  }

  private static class StatusUpdater implements ChangeCounter.Listener {
    private final DiffPanelOuterComponent myPanel;

    private StatusUpdater(DiffPanelOuterComponent panel) {
      myPanel = panel;
    }

    public void onCountersChanged(ChangeCounter counter) {
      int changes = counter.getChangeCounter();
      int conflicts = counter.getConflictCounter();
      String text;
      if (changes == 0 && conflicts == 0) {
        text = DiffBundle.message("merge.dialog.all.conflicts.resolved.message.text");
      }
      else {
        // The Bundle doesn't support such complex formats. Until that is fixed, constructing manually
        //text = DiffBundle.message("merge.statistics.message", changes, conflicts);
        text = makeCountersText(changes, conflicts);
      }
      myPanel.setStatusBarText(text);
    }

    @NotNull
    private static String makeCountersText(int changes, int conflicts) {
      return makeCounterWord(changes, "change") + ". " + makeCounterWord(conflicts, "conflict");
    }

    @NotNull
    private static String makeCounterWord(int number, @NotNull String word) {
      if (number == 0) {
        return "No " + StringUtil.pluralize(word);
      }
      return number + " " + StringUtil.pluralize(word, number);
    }

    public void dispose(@NotNull MergeList mergeList) {
      ChangeCounter.getOrCreate(mergeList).removeListener(this);
    }

    public static StatusUpdater install(MergeList mergeList, DiffPanelOuterComponent panel) {
      ChangeCounter counters = ChangeCounter.getOrCreate(mergeList);
      StatusUpdater updater = new StatusUpdater(panel);
      counters.addListener(updater);
      updater.onCountersChanged(counters);
      return updater;
    }
  }

  public static class AsComponent extends JPanel{
    private final MergePanel2 myMergePanel;

    public AsComponent(@NotNull Disposable parent) {
      super(new BorderLayout());
      myMergePanel = new MergePanel2(null, parent);
      add(myMergePanel.getComponent(), BorderLayout.CENTER);
    }

    public MergePanel2 getMergePanel() {
      return myMergePanel;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public boolean isToolbarEnabled() {
      return myMergePanel.myPanel.isToolbarEnabled();
    }

    public void setToolbarEnabled(boolean enabled) {
      myMergePanel.myPanel.disableToolbar(!enabled);
    }
  }
}
