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
package com.intellij.openapi.vcs.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.*;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CommitMessage extends AbstractDataProviderPanel implements Disposable, CommitMessageI {

  public static final Key<DataContext> DATA_CONTEXT_KEY = Key.create("commit message data context");
  private final EditorTextField myEditorField;
  private final Project         myProject;
  private Consumer<String> myMessageConsumer;
  private TitledSeparator mySeparator;
  private boolean myCheckSpelling;

  public CommitMessage(Project project) {
    this(project, true);
  }

  public CommitMessage(Project project, final boolean withSeparator) {
    super(new BorderLayout());
    myEditorField = createEditorField(project);
    myProject = project;
    
    // Note that we assume here that editor used for commit message processing uses font family implied by LAF (in contrast,
    // IJ code editor uses monospaced font). Hence, we don't need any special actions here
    // (myEditorField.setFontInheritedFromLAF(true) should be used instead).
    
    add(myEditorField, BorderLayout.CENTER);

    JPanel labelPanel = new JPanel(new BorderLayout());
    labelPanel.setBorder(BorderFactory.createEmptyBorder());
    if (withSeparator) {
      mySeparator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myEditorField.getComponent(), true, true);
      JPanel separatorPanel = new JPanel(new BorderLayout());
      separatorPanel.add(mySeparator, BorderLayout.SOUTH);
      separatorPanel.add(Box.createVerticalGlue(), BorderLayout.NORTH);
      labelPanel.add(separatorPanel, BorderLayout.CENTER);
    }
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), withSeparator);
    toolbar.updateActionsImmediately();
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setBorder(BorderFactory.createEmptyBorder());
    if (withSeparator) {
      labelPanel.add(toolbar.getComponent(), BorderLayout.EAST);
      add(labelPanel, BorderLayout.NORTH);
    } else {
      add(toolbar.getComponent(), BorderLayout.EAST);
    }

    setBorder(BorderFactory.createEmptyBorder());
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (key.is(VcsDataKeys.COMMIT_MESSAGE_CONTROL.getName())) {
      sink.put(VcsDataKeys.COMMIT_MESSAGE_CONTROL, this);
    }
  }

  public void setSeparatorText(final String text) {
    if (mySeparator != null) {
      mySeparator.setText(text);
    }
  }

  @Override
  public void setCommitMessage(String currentDescription) {
    setText(currentDescription);
  }

  private static EditorTextField createEditorField(final Project project) {
    EditorTextField editorField = createCommitTextEditor(project, false);
    editorField.getDocument().putUserData(DATA_CONTEXT_KEY, DataManager.getInstance().getDataContext(editorField.getComponent()));
    return editorField;
  }

  /**
   * Creates a text editor appropriate for creating commit messages.
   *
   * @param project project this commit message editor is intended for
   * @param forceSpellCheckOn if false, {@link com.intellij.openapi.vcs.VcsConfiguration#CHECK_COMMIT_MESSAGE_SPELLING} will control
   *                          whether or not the editor has spell check enabled
   * @return a commit message editor
   */
  public static EditorTextField createCommitTextEditor(final Project project, boolean forceSpellCheckOn) {
    final boolean checkSpelling;
    final boolean useCommitMessageMargin;
    final int commitMessageMarginSize;

    VcsConfiguration configuration = VcsConfiguration.getInstance(project);

    if (configuration != null) {
      checkSpelling = forceSpellCheckOn || configuration.CHECK_COMMIT_MESSAGE_SPELLING;
      useCommitMessageMargin = configuration.USE_COMMIT_MESSAGE_MARGIN;
      commitMessageMarginSize = configuration.COMMIT_MESSAGE_MARGIN_SIZE;
    } else {
      checkSpelling = true;
      useCommitMessageMargin = false;
      commitMessageMarginSize = -1;
    }

    EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    return service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(),
                                  project,
                                  new EditorTextFieldProvider.AdHocEditorCustomizer() {
                                    @Override
                                    public void customize(EditorEx editor) {
                                      toggleEditorSpellchecking(project, editor, checkSpelling);

                                      if (useCommitMessageMargin) {
                                        editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
                                        editor.getSettings().setRightMarginShown(true);
                                        editor.getSettings().setRightMargin(commitMessageMarginSize);
                                      }
                                    }
                                  });
  }

  @Nullable
  public static ActionGroup getToolbarActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Vcs.MessageActionGroup");
  }

  public EditorTextField getEditorField() {
    return myEditorField;
  }

  public void setText(final String initialMessage) {
    final String text = initialMessage == null ? "" : initialMessage;
    myEditorField.setText(text);
    if (myMessageConsumer != null) {
      myMessageConsumer.consume(text);
    }
  }

  public String getComment() {
    final String s = myEditorField.getDocument().getCharsSequence().toString();
    int end = s.length();
    while(end > 0 && Character.isSpaceChar(s.charAt(end-1))) {
      end--;
    }
    return s.substring(0, end);
  }

  public void requestFocusInMessage() {
    myEditorField.requestFocus();
    myEditorField.selectAll();
  }

  @Override
  public boolean isCheckSpelling() {
    return myCheckSpelling;
  }

  public void setCheckSpelling(boolean check) {
    myCheckSpelling = check;
    Editor editor = myEditorField.getEditor();
    if (!(editor instanceof EditorEx)) {
      return;
    }
    EditorEx editorEx = (EditorEx)editor;
    toggleEditorSpellchecking(myProject, editorEx, check);
  }

  private static void toggleEditorSpellchecking(Project project, EditorEx editorEx, boolean spellCheckingEnabled) {
    EditorCustomization[] customizations = Extensions.getExtensions(EditorCustomization.EP_NAME, project);
    EditorCustomization.Feature spellCheckFeature = EditorCustomization.Feature.SPELL_CHECK;
    for (EditorCustomization customization : customizations) {
      if (customization.getSupportedFeatures().contains(spellCheckFeature)) {
        if (spellCheckingEnabled) {
          customization.addCustomization(editorEx, spellCheckFeature);
        }
        else {
          customization.removeCustomization(editorEx, spellCheckFeature);
        }
      }
    }
  }

  public void dispose() {
  }

  public void setMessageConsumer(Consumer<String> messageConsumer) {
    myMessageConsumer = messageConsumer;
  }
}
