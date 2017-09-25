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
package com.intellij.openapi.vcs.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.vcs.commit.CommitMessageInspectionProfile;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.convertLineSeparators;
import static com.intellij.openapi.util.text.StringUtil.trimTrailing;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;
import static com.intellij.util.containers.ContainerUtil.newHashSet;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;
import static com.intellij.vcs.commit.CommitMessageInspectionProfile.getBodyRightMargin;
import static java.util.Collections.emptyList;
import static javax.swing.BorderFactory.createEmptyBorder;

public class CommitMessage extends JPanel implements Disposable, DataProvider, CommitMessageI {
  public static final Key<CommitMessage> DATA_KEY = Key.create("Vcs.CommitMessage.Panel");
  @NotNull private final EditorTextField myEditorField;
  @Nullable private final TitledSeparator mySeparator;

  @NotNull private List<ChangeList> myChangeLists = emptyList(); // guarded with WriteLock

  public CommitMessage(@NotNull Project project) {
    this(project, true, true, true);
  }

  public CommitMessage(@NotNull Project project, boolean withSeparator, boolean showToolbar, boolean runInspections) {
    super(new BorderLayout());

    myEditorField = createCommitMessageEditor(project, runInspections);
    myEditorField.getDocument().putUserData(DATA_KEY, this);

    add(myEditorField, BorderLayout.CENTER);

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
  }

  @NotNull
  private static JComponent createToolbar(boolean horizontal) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("CommitMessage", getToolbarActions(), horizontal);

    toolbar.updateActionsImmediately();
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setBorder(createEmptyBorder());

    return toolbar.getComponent();
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (VcsDataKeys.COMMIT_MESSAGE_CONTROL.is(dataId)) {
      return this;
    }
    return null;
  }

  public void setSeparatorText(@NotNull String text) {
    if (mySeparator != null) {
      mySeparator.setText(text);
    }
  }

  @Override
  public void setCommitMessage(@Nullable String currentDescription) {
    setText(currentDescription);
  }

  /**
   * Creates a text editor appropriate for creating commit messages.
   * @return a commit message editor
   * @deprecated Use {@link CommitMessage} component.
   */
  @Deprecated
  public static EditorTextField createCommitTextEditor(@NotNull Project project, @SuppressWarnings("unused") boolean forceSpellCheckOn) {
    return createCommitMessageEditor(project, false);
  }

  @NotNull
  private static EditorTextField createCommitMessageEditor(@NotNull Project project, boolean runInspections) {
    Set<EditorCustomization> features = newHashSet();

    VcsConfiguration configuration = VcsConfiguration.getInstance(project);
    if (configuration != null) {
      features.add(new RightMarginEditorCustomization(configuration.USE_COMMIT_MESSAGE_MARGIN, getBodyRightMargin(project)));
      features.add(WrapWhenTypingReachesRightMarginCustomization.getInstance(configuration.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN));
    } else {
      features.add(new RightMarginEditorCustomization(false, -1));
    }

    features.add(SoftWrapsEditorCustomization.ENABLED);
    features.add(AdditionalPageAtBottomEditorCustomization.DISABLED);
    features.add(MonospaceEditorCustomization.getInstance());
    if (runInspections) {
      features.add(ErrorStripeEditorCustomization.ENABLED);
      features.add(new InspectionCustomization(project));
    }
    else {
      addIfNotNull(features, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());
    }

    EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    return service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);
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

  public void setText(@Nullable String initialMessage) {
    myEditorField.setText(initialMessage == null ? "" : convertLineSeparators(initialMessage));
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

  @Override
  public void dispose() {
  }

  @CalledInAwt
  public void setChangeLists(@NotNull List<ChangeList> value) {
    WriteAction.run(() -> myChangeLists = value);
  }

  @NotNull
  @CalledWithReadLock
  public List<ChangeList> getChangeLists() {
    return myChangeLists;
  }

  private static class InspectionCustomization implements EditorCustomization {
    @NotNull private final Project myProject;

    public InspectionCustomization(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void customize(@NotNull EditorEx editor) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

      if (file != null) {
        file.putUserData(InspectionProfileWrapper.CUSTOMIZATION_KEY,
                         profile -> new InspectionProfileWrapper(CommitMessageInspectionProfile.getInstance(myProject)));
      }
      editor.putUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY, false);
      ((EditorMarkupModelImpl)editor.getMarkupModel())
        .setErrorStripeRenderer(new ConditionalTrafficLightRenderer(myProject, editor.getDocument(), file));
    }
  }

  private static class ConditionalTrafficLightRenderer extends TrafficLightRenderer {
    public ConditionalTrafficLightRenderer(@NotNull Project project, @NotNull Document document, @Nullable PsiFile file) {
      super(project, document, file);
    }

    @Override
    protected void refresh(@Nullable EditorMarkupModelImpl editorMarkupModel) {
      super.refresh(editorMarkupModel);
      if (editorMarkupModel != null) {
        editorMarkupModel.setTrafficLightIconVisible(hasHighSeverities(errorCount));
      }
    }

    private boolean hasHighSeverities(@NotNull int[] errorCount) {
      HighlightSeverity minSeverity = notNull(HighlightDisplayLevel.find("TYPO"), HighlightDisplayLevel.DO_NOT_SHOW).getSeverity();

      for (int i = 0; i < errorCount.length; i++) {
        if (errorCount[i] > 0 && getSeverityRegistrar().compare(getSeverityRegistrar().getSeverityByIndex(i), minSeverity) > 0) {
          return true;
        }
      }
      return false;
    }
  }
}
