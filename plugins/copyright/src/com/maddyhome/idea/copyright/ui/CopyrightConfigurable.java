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

package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.pattern.EntityUtil;
import com.maddyhome.idea.copyright.pattern.VelocityHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CopyrightConfigurable extends NamedConfigurable<CopyrightProfile> {
  private final CopyrightProfile myCopyrightProfile;
  private JPanel myWholePanel;

  private final Project myProject;
  private boolean myModified;

  private String myDisplayName;
  private EditorTextField myEditor;
  private JButton myValidateButton;
  private JTextField myKeywordTf;
  private JTextField myAllowReplaceTextField;
  private JPanel myEditorPanel;

  public CopyrightConfigurable(Project project, CopyrightProfile copyrightProfile, Runnable updater) {
    super(true, updater);
    myProject = project;
    myCopyrightProfile = copyrightProfile;
    myDisplayName = myCopyrightProfile.getName();
    final Set<EditorCustomization> features = new HashSet<>();
    ContainerUtil.addIfNotNull(features, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());
    features.add(SoftWrapsEditorCustomization.ENABLED);
    features.add(AdditionalPageAtBottomEditorCustomization.DISABLED);
    EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    myEditor = service.getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);
    myEditorPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  public void setDisplayName(String s) {
    myCopyrightProfile.setName(s);
  }

  public CopyrightProfile getEditableObject() {
    return myCopyrightProfile;
  }

  public String getBannerSlogan() {
    return myCopyrightProfile.getName();
  }

  public JComponent createOptionsPanel() {
    myValidateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        try {
          VelocityHelper.verify(myEditor.getDocument().getText());
          Messages.showInfoMessage(myProject, "Velocity template is valid.", "Validation");
        }
        catch (Exception e1) {
          Messages.showInfoMessage(myProject, "Velocity template contains error:\n" + e1.getMessage(), "Validation");
        }
      }
    });
    return myWholePanel;
  }

  @Nls
  public String getDisplayName() {
    return myCopyrightProfile.getName();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public boolean isModified() {
    return myModified ||
           !Comparing.strEqual(EntityUtil.encode(myEditor.getDocument().getText()), myCopyrightProfile.getNotice()) ||
           !Comparing.strEqual(myKeywordTf.getText().trim(), myCopyrightProfile.getKeyword()) ||
           !Comparing.strEqual(myAllowReplaceTextField.getText().trim(), myCopyrightProfile.getAllowReplaceKeyword()) ||
           !Comparing.strEqual(myDisplayName, myCopyrightProfile.getName());
  }

  public void apply() throws ConfigurationException {
    myCopyrightProfile.setNotice(EntityUtil.encode(myEditor.getDocument().getText()));
    final String keyword = myKeywordTf.getText().trim();
    try {
      if (!StringUtil.isEmptyOrSpaces(keyword)) {
        Pattern.compile(keyword);
      }
    }
    catch (PatternSyntaxException e) {
      throw new ConfigurationException("Keyword pattern syntax is incorrect: " + e.getMessage());
    }
    myCopyrightProfile.setKeyword(keyword);
    myCopyrightProfile.setAllowReplaceKeyword(myAllowReplaceTextField.getText().trim());
    CopyrightManager.getInstance(myProject).replaceCopyright(myDisplayName, myCopyrightProfile);
    myDisplayName = myCopyrightProfile.getName();
    myModified = false;
  }

  public void reset() {
    myDisplayName = myCopyrightProfile.getName();
    SwingUtilities.invokeLater(() -> DocumentUtil.writeInRunUndoTransparentAction(
      () -> myEditor.getDocument().setText(EntityUtil.decode(myCopyrightProfile.getNotice()))));
    myKeywordTf.setText(myCopyrightProfile.getKeyword());
    myAllowReplaceTextField.setText(myCopyrightProfile.getAllowReplaceKeyword());
  }

  public void disposeUIResources() {
  }

  public void setModified(boolean modified) {
    myModified = modified;
  }
}
