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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.SpellCheckAwareEditorFieldProvider;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CommitMessage extends JPanel implements Disposable {

  private final EditorTextField myEditorField;

  public CommitMessage(Project project) {
    super(new BorderLayout());
    myEditorField = createEditorField(project);
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

  private static EditorTextField createEditorField(final Project project) {
    return ServiceManager.getService(SpellCheckAwareEditorFieldProvider.class).getEditorField(project);
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

  public void dispose() {
  }
}
