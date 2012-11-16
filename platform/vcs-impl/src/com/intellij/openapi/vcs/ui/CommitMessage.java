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
import java.util.EnumSet;
import java.util.Set;

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
    boolean checkSpelling = true;
    VcsConfiguration configuration = VcsConfiguration.getInstance(project);
    if (configuration != null) {
      checkSpelling = configuration.CHECK_COMMIT_MESSAGE_SPELLING;
    }
    myEditorField = createEditorField(project, checkSpelling);
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

  private static EditorTextField createEditorField(final Project project, final boolean checkSpelling) {
    EditorTextField editorField = createCommitTextEditor(project, checkSpelling);
    editorField.getDocument().putUserData(DATA_CONTEXT_KEY, DataManager.getInstance().getDataContext(editorField.getComponent()));
    return editorField;
  }

  public static EditorTextField createCommitTextEditor(Project project, boolean checkSpelling) {
    EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    Set<EditorCustomization.Feature> enabledFeatures = EnumSet.of(EditorCustomization.Feature.SOFT_WRAP);
    Set<EditorCustomization.Feature> disabledFeatures = EnumSet.of(EditorCustomization.Feature.ADDITIONAL_PAGE_AT_BOTTOM);
    if (checkSpelling) {
      enabledFeatures.add(EditorCustomization.Feature.SPELL_CHECK);
    }
    else {
      disabledFeatures.add(EditorCustomization.Feature.SPELL_CHECK);
    }

    return service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, enabledFeatures, disabledFeatures);
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
    EditorCustomization[] customizations = Extensions.getExtensions(EditorCustomization.EP_NAME, myProject);
    EditorCustomization.Feature feature = EditorCustomization.Feature.SPELL_CHECK;
    for (EditorCustomization customization : customizations) {
      if (customization.getSupportedFeatures().contains(feature)) {
        if (check) {
          customization.addCustomization(editorEx, feature);
        }
        else {
          customization.removeCustomization(editorEx, feature);
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
