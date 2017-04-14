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

import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.vcs.commit.CommitMessageInspectionProfile;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.commit.CommitMessageInspectionProfile.getBodyRightMargin;

public class CommitMessage extends JPanel implements Disposable, DataProvider, CommitMessageI {
  public static final Key<CommitMessage> DATA_KEY = Key.create("Vcs.CommitMessage.Panel");
  private final EditorTextField myEditorField;
  private final TitledSeparator mySeparator;

  @NotNull private List<ChangeList> myChangeLists = Collections.emptyList(); // guarded with WriteLock

  public CommitMessage(@NotNull Project project) {
    this(project, true);
  }

  public CommitMessage(@NotNull Project project, @NotNull CommitMessage commitMessage) {
    this(project);
    myEditorField.setDocument(commitMessage.getEditorField().getDocument());
  }

  public CommitMessage(@NotNull Project project, final boolean withSeparator) {
    super(new BorderLayout());

    myEditorField = createCommitTextEditor(project);
    myEditorField.getDocument().putUserData(DATA_KEY, this);

    add(myEditorField, BorderLayout.CENTER);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), withSeparator);
    toolbar.updateActionsImmediately();
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setBorder(BorderFactory.createEmptyBorder());

    if (withSeparator) {
      mySeparator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myEditorField.getComponent());
      JPanel separatorPanel = new JPanel(new BorderLayout());
      separatorPanel.add(mySeparator, BorderLayout.SOUTH);
      separatorPanel.add(Box.createVerticalGlue(), BorderLayout.NORTH);

      JPanel labelPanel = new JPanel(new BorderLayout());
      labelPanel.setBorder(BorderFactory.createEmptyBorder());
      labelPanel.add(separatorPanel, BorderLayout.CENTER);
      labelPanel.add(toolbar.getComponent(), BorderLayout.EAST);
      add(labelPanel, BorderLayout.NORTH);
    }
    else {
      mySeparator = null;
      add(toolbar.getComponent(), BorderLayout.EAST);
    }

    setBorder(BorderFactory.createEmptyBorder());
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
  public void setCommitMessage(String currentDescription) {
    setText(currentDescription);
  }

  /**
   * Creates a text editor appropriate for creating commit messages.
   * @return a commit message editor
   * @deprecated Use {@link CommitMessage#createCommitTextEditor(Project)}.
   */
  @Deprecated
  public static EditorTextField createCommitTextEditor(@NotNull Project project, @SuppressWarnings("unused") boolean forceSpellCheckOn) {
    return createCommitTextEditor(project);
  }

  @NotNull
  public static EditorTextField createCommitTextEditor(@NotNull Project project) {
    Set<EditorCustomization> features = new HashSet<>();

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
    features.add(ErrorStripeEditorCustomization.ENABLED);
    features.add(new InspectionCustomization(project));

    EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    return service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);
  }

  public static boolean isCommitMessage(@NotNull PsiElement element) {
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    return document != null && document.getUserData(DATA_KEY) != null;
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
    final String text = initialMessage == null ? "" : StringUtil.convertLineSeparators(initialMessage);
    myEditorField.setText(text);
  }

  @NotNull
  public String getComment() {
    final String s = myEditorField.getDocument().getCharsSequence().toString();
    return StringUtil.trimTrailing(s);
  }

  public void requestFocusInMessage() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      IdeFocusManager.getGlobalInstance().requestFocus(myEditorField, true);
    });
    myEditorField.selectAll();
  }

  @Override
  public void dispose() {
  }

  @CalledInAwt
  public void setChangeLists(@NotNull List<ChangeList> value) {
    WriteAction.run(() -> {
      myChangeLists = value;
    });
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
    }
  }
}
