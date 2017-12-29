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

package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightManager;
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
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.pattern.EntityUtil;
import com.maddyhome.idea.copyright.pattern.VelocityHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  public CopyrightConfigurable(@NotNull Project project, CopyrightProfile copyrightProfile, Runnable updater) {
    super(true, updater);
    myProject = project;
    myCopyrightProfile = copyrightProfile;
    myDisplayName = myCopyrightProfile.getName();
    final Set<EditorCustomization> features = new HashSet<>();
    ContainerUtil.addIfNotNull(features, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());
    features.add(SoftWrapsEditorCustomization.ENABLED);
    features.add(AdditionalPageAtBottomEditorCustomization.DISABLED);
    myEditor = EditorTextFieldProvider.getInstance().getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);
    myEditorPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public void setDisplayName(String s) {
    myCopyrightProfile.setName(s);
  }

  @Override
  public CopyrightProfile getEditableObject() {
    return myCopyrightProfile;
  }

  @Override
  public String getBannerSlogan() {
    return myCopyrightProfile.getName();
  }

  @Override
  public JComponent createOptionsPanel() {
    myValidateButton.addActionListener(new ActionListener() {
      @Override
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

  @Override
  @Nls
  public String getDisplayName() {
    return myCopyrightProfile.getName();
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  @Override
  public boolean isModified() {
    return myModified ||
           !Comparing.strEqual(EntityUtil.encode(myEditor.getDocument().getText()), myCopyrightProfile.getNotice()) ||
           !Comparing.strEqual(myKeywordTf.getText().trim(), myCopyrightProfile.getKeyword()) ||
           !Comparing.strEqual(myAllowReplaceTextField.getText().trim(), myCopyrightProfile.getAllowReplaceRegexp()) ||
           !Comparing.strEqual(myDisplayName, myCopyrightProfile.getName());
  }

  @Override
  public void apply() throws ConfigurationException {
    myCopyrightProfile.setNotice(EntityUtil.encode(myEditor.getDocument().getText()));
    myCopyrightProfile.setKeyword(validateRegexpAndGet(myKeywordTf.getText().trim(), "Detect copyright regexp is incorrect: "));
    myCopyrightProfile.setAllowReplaceRegexp(validateRegexpAndGet(myAllowReplaceTextField.getText().trim(), "Replace copyright regexp is incorrect: "));
    CopyrightManager.getInstance(myProject).replaceCopyright(myDisplayName, myCopyrightProfile);
    myDisplayName = myCopyrightProfile.getName();
    myModified = false;
  }

  @NotNull
  private static String validateRegexpAndGet(final String regexp, final String message) throws ConfigurationException {
    try {
      if (!StringUtil.isEmptyOrSpaces(regexp)) {
        //noinspection ResultOfMethodCallIgnored
        Pattern.compile(regexp);
      }
    }
    catch (PatternSyntaxException e) {
      throw new ConfigurationException(message + e.getMessage());
    }
    return regexp;
  }

  @Override
  public void reset() {
    myDisplayName = myCopyrightProfile.getName();
    SwingUtilities.invokeLater(() -> DocumentUtil.writeInRunUndoTransparentAction(
      () -> myEditor.getDocument().setText(EntityUtil.decode(myCopyrightProfile.getNotice()))));
    myKeywordTf.setText(myCopyrightProfile.getKeyword());
    myAllowReplaceTextField.setText(myCopyrightProfile.getAllowReplaceRegexp());
  }

  public void setModified(boolean modified) {
    myModified = modified;
  }
}
