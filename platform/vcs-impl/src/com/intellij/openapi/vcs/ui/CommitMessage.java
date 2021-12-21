// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.vcs.commit.CommitMessageUi;
import com.intellij.vcs.commit.message.BodyLimitSettings;
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.openapi.util.text.StringUtil.convertLineSeparators;
import static com.intellij.openapi.util.text.StringUtil.trimTrailing;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;
import static com.intellij.vcs.commit.message.CommitMessageInspectionProfile.getBodyLimitSettings;
import static javax.swing.BorderFactory.createEmptyBorder;

public class CommitMessage extends JPanel implements Disposable, DataProvider, CommitMessageUi, CommitMessageI, LafManagerListener {
  public static final Key<CommitMessage> DATA_KEY = Key.create("Vcs.CommitMessage.Panel");
  public static final Key<Supplier<Iterable<Change>>> CHANGES_SUPPLIER_KEY = Key.create("Vcs.CommitMessage.CompletionContext");

  private final @NotNull JBLoadingPanel myLoadingPanel;

  private final @Nullable @Nls String myMessagePlaceholder;

  private static final @NotNull EditorCustomization COLOR_SCHEME_FOR_CURRENT_UI_THEME_CUSTOMIZATION = editor -> {
    editor.setBackgroundColor(null); // to use background from set color scheme
    editor.setColorsScheme(getCommitMessageColorScheme());
  };

  @NotNull
  private static EditorColorsScheme getCommitMessageColorScheme() {
    boolean isLaFDark = ColorUtil.isDark(UIUtil.getPanelBackground());
    boolean isEditorDark = EditorColorsManager.getInstance().isDarkEditor();
    return isLaFDark == isEditorDark
           ? EditorColorsManager.getInstance().getGlobalScheme()
           : EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
  }

  @NotNull private final EditorTextField myEditorField;
  @Nullable private final TitledSeparator mySeparator;

  public CommitMessage(@NotNull Project project) {
    this(project, true, true, true);
  }

  public CommitMessage(@NotNull Project project,
                       boolean withSeparator,
                       boolean showToolbar,
                       boolean runInspections) {
    this(project, withSeparator, showToolbar, runInspections, null);
  }

  public CommitMessage(@NotNull Project project,
                       boolean withSeparator,
                       boolean showToolbar,
                       boolean runInspections,
                       @Nullable @Nls String messagePlaceholder) {
    super(new BorderLayout());
    myMessagePlaceholder = messagePlaceholder;
    myEditorField = createCommitMessageEditor(project, runInspections);
    myEditorField.getDocument().putUserData(DATA_KEY, this);
    myEditorField.setPlaceholder(myMessagePlaceholder);

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this, 0);
    myLoadingPanel.add(myEditorField, BorderLayout.CENTER);

    add(myLoadingPanel, BorderLayout.CENTER);

    if (withSeparator) {
      mySeparator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myEditorField.getComponent());
      JPanel separatorPanel = simplePanel().addToBottom(mySeparator).addToTop(Box.createVerticalGlue());
      BorderLayoutPanel labelPanel = simplePanel(separatorPanel).withBorder(createEmptyBorder());
      if (showToolbar) {
        labelPanel.addToRight(createToolbar(true));
      }
      add(labelPanel, BorderLayout.NORTH);
    }
    else {
      mySeparator = null;
      if (showToolbar) {
        add(createToolbar(false), BorderLayout.EAST);
      }
    }

    setBorder(createEmptyBorder());

    updateOnInspectionProfileChanged(project);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(LafManagerListener.TOPIC, this);
  }

  @Override
  public void stopLoading() {
    myLoadingPanel.stopLoading();
    myEditorField.setEnabled(true);
    myEditorField.setPlaceholder(myMessagePlaceholder);
  }

  @Override
  public void startLoading() {
    myEditorField.setEnabled(false);
    myEditorField.setPlaceholder(null);
    myLoadingPanel.startLoading();
  }

  private void updateOnInspectionProfileChanged(@NotNull Project project) {
    project.getMessageBus().connect(this).subscribe(CommitMessageInspectionProfile.TOPIC, () -> {
      Editor editor = myEditorField.getEditor();
      if (editor instanceof EditorEx) RightMarginCustomization.customize(project, (EditorEx)editor);
    });
  }

  @Override
  public void lookAndFeelChanged(@NotNull LafManager source) {
    Editor editor = myEditorField.getEditor();
    if (editor instanceof EditorEx) COLOR_SCHEME_FOR_CURRENT_UI_THEME_CUSTOMIZATION.customize((EditorEx)editor);
  }

  @NotNull
  private JComponent createToolbar(boolean horizontal) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("CommitMessage", getToolbarActions(), horizontal);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setBorder(createEmptyBorder());
    toolbar.setTargetComponent(this);

    return toolbar.getComponent();
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (VcsDataKeys.COMMIT_MESSAGE_CONTROL.is(dataId)) {
      return this;
    }
    return null;
  }

  public void setSeparatorText(@NotNull @NlsContexts.Separator String text) {
    if (mySeparator != null) {
      mySeparator.setText(text);
    }
  }

  @Override
  public void setCommitMessage(@Nullable String currentDescription) {
    setText(currentDescription);
  }

  @NotNull
  private static EditorTextField createCommitMessageEditor(@NotNull Project project, boolean runInspections) {
    Set<EditorCustomization> features = new HashSet<>();

    features.add(new RightMarginCustomization(project));
    features.add(SoftWrapsEditorCustomization.ENABLED);
    features.add(AdditionalPageAtBottomEditorCustomization.DISABLED);
    features.add(COLOR_SCHEME_FOR_CURRENT_UI_THEME_CUSTOMIZATION);
    if (runInspections) {
      features.add(ErrorStripeEditorCustomization.ENABLED);
      features.add(new InspectionCustomization(project));
    }
    else {
      addIfNotNull(features, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());
    }

    EditorTextField editorField =
      EditorTextFieldProvider.getInstance().getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);

    // Global editor color scheme is set by EditorTextField logic. We also need to use font from it and not from the current LaF.
    editorField.setFontInheritedFromLAF(false);
    return editorField;
  }

  public static boolean isCommitMessage(@NotNull PsiElement element) {
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    return document != null && document.getUserData(DATA_KEY) != null;
  }

  @Nullable
  public static Editor getEditor(@NotNull Document document) {
    CommitMessage commitMessage = document.getUserData(DATA_KEY);
    return commitMessage != null ? commitMessage.getEditorField().getEditor() : null;
  }

  @NotNull
  private static ActionGroup getToolbarActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Vcs.MessageActionGroup");
  }

  @NotNull
  public EditorTextField getEditorField() {
    return myEditorField;
  }

  @NotNull
  @Override
  public String getText() {
    return getComment();
  }

  @Override
  public void setText(@Nullable String initialMessage) {
    myEditorField.setText(initialMessage == null ? "" : convertLineSeparators(initialMessage));
  }

  @Override
  public void focus() {
    requestFocusInMessage();
  }

  @NotNull
  public String getComment() {
    return trimTrailing(myEditorField.getDocument().getCharsSequence().toString());
  }

  public void requestFocusInMessage() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
      () -> IdeFocusManager.getGlobalInstance().requestFocus(myEditorField, true));
    myEditorField.selectAll();
  }

  @RequiresEdt
  public void setChangesSupplier(@NotNull Supplier<Iterable<Change>> changesSupplier) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myEditorField.getDocument().putUserData(CHANGES_SUPPLIER_KEY, changesSupplier);
  }

  @Override
  public void dispose() {
    removeAll();
    myEditorField.getDocument().putUserData(DATA_KEY, null);
    myEditorField.getDocument().putUserData(CHANGES_SUPPLIER_KEY, null);
  }

  private static final class RightMarginCustomization implements EditorCustomization {
    @NotNull private final Project myProject;

    private RightMarginCustomization(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void customize(@NotNull EditorEx editor) {
      customize(myProject, editor);
    }

    private static void customize(@NotNull Project project, @NotNull EditorEx editor) {
      BodyLimitSettings settings = getBodyLimitSettings(project);

      editor.getSettings().setRightMargin(settings.getRightMargin());
      editor.getSettings().setRightMarginShown(settings.isShowRightMargin());
      editor.getSettings().setWrapWhenTypingReachesRightMargin(settings.isWrapOnTyping());
    }
  }

  private static class InspectionCustomization implements EditorCustomization {
    @NotNull private final Project myProject;

    InspectionCustomization(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void customize(@NotNull EditorEx editor) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

      if (file != null) {
        InspectionProfileWrapper.setCustomInspectionProfileWrapperTemporarily(file,
                         profile -> new InspectionProfileWrapper(CommitMessageInspectionProfile.getInstance(myProject)));
      }
      editor.putUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY, false);
      ((EditorMarkupModelImpl)editor.getMarkupModel())
        .setErrorStripeRenderer(new ConditionalTrafficLightRenderer(myProject, editor.getDocument()));
    }
  }

  private static class ConditionalTrafficLightRenderer extends TrafficLightRenderer {
    ConditionalTrafficLightRenderer(@NotNull Project project, @NotNull Document document) {
      super(project, document);
    }

    @Override
    protected void refresh(@Nullable EditorMarkupModelImpl editorMarkupModel) {
      super.refresh(editorMarkupModel);
      if (editorMarkupModel != null) {
        editorMarkupModel.setTrafficLightIconVisible(hasHighSeverities(getErrorCounts()));
      }
    }

    private boolean hasHighSeverities(int @NotNull [] errorCounts) {
      HighlightSeverity minSeverity = notNull(HighlightDisplayLevel.find("TYPO"), HighlightDisplayLevel.DO_NOT_SHOW).getSeverity();

      for (int i = 0; i < errorCounts.length; i++) {
        if (errorCounts[i] > 0 && getSeverityRegistrar().compare(getSeverityRegistrar().getSeverityByIndex(i), minSeverity) > 0) {
          return true;
        }
      }
      return false;
    }
  }
}
