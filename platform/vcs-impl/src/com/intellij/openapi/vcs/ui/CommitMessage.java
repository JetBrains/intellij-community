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
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldProvider;
import com.intellij.ui.SeparatorFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.EnumSet;
import java.util.Set;

public class CommitMessage extends JPanel implements Disposable {

  public static final Key<DataContext> DATA_CONTEXT_KEY = Key.create("commit message data context");
  private final EditorTextField myEditorField;
  private final Project         myProject;

  public CommitMessage(Project project) {
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
    JComponent separator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myEditorField.getComponent());
    JPanel separatorPanel = new JPanel(new BorderLayout());
    separatorPanel.add(separator, BorderLayout.SOUTH);
    separatorPanel.add(Box.createVerticalGlue(), BorderLayout.NORTH);
    labelPanel.add(separatorPanel, BorderLayout.CENTER);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), true);
    toolbar.updateActionsImmediately();
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setBorder(BorderFactory.createEmptyBorder());
    labelPanel.add(toolbar.getComponent(), BorderLayout.EAST);
    add(labelPanel, BorderLayout.NORTH);

    setBorder(BorderFactory.createEmptyBorder());
  }

  private static EditorTextField createEditorField(final Project project, final boolean checkSpelling) {
    EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    Set<EditorCustomization.Feature> features = EnumSet.of(
      EditorCustomization.Feature.SOFT_WRAP, EditorCustomization.Feature.NO_ADDITIONAL_PAGE_AT_BOTTOM
    );
    if (checkSpelling) {
      features.add(EditorCustomization.Feature.SPELL_CHECK);
    }
    EditorTextField editorField = service.getEditorField(
      FileTypes.PLAIN_TEXT.getLanguage(), project, features.toArray(new EditorCustomization.Feature[features.size()])
    );
    editorField.getDocument().putUserData(DATA_CONTEXT_KEY, DataManager.getInstance().getDataContext(editorField.getComponent()));
    return editorField;
  }

  @Nullable
  public static ActionGroup getToolbarActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Vcs.MessageActionGroup");
  }

  public EditorTextField getEditorField() {
    return myEditorField;
  }

  public void setText(final String initialMessage) {
    myEditorField.setText(initialMessage == null ? "" : initialMessage);
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

  public void setCheckSpelling(boolean check) {
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
}
