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

import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.copyright.IdeCopyrightManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.pattern.EntityUtil;
import com.maddyhome.idea.copyright.pattern.VelocityHelper;
import org.jetbrains.annotations.Nls;
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
  private boolean myShareProfile;
  private JPanel myWholePanel;

  private final Project myProject;
  private boolean myModified;

  private String myDisplayName;
  private final EditorTextField myEditor;
  private JButton myValidateButton;
  private JTextField myKeywordTf;
  private JTextField myAllowReplaceTextField;
  private JPanel myEditorPanel;

  private final JCheckBox mySharedCheckbox;
  private final JLabel mySharedContextHelp;

  public CopyrightConfigurable(@NotNull Project project, CopyrightProfile copyrightProfile, Runnable updater, boolean shareProfile) {
    super(true, updater);
    myProject = project;
    myCopyrightProfile = copyrightProfile;
    myShareProfile = shareProfile;
    myDisplayName = myCopyrightProfile.getName();
    final Set<EditorCustomization> features = new HashSet<>();
    ContainerUtil.addIfNotNull(features, SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization());
    features.add(SoftWrapsEditorCustomization.ENABLED);
    features.add(AdditionalPageAtBottomEditorCustomization.DISABLED);
    myEditor = EditorTextFieldProvider.getInstance().getEditorField(FileTypes.PLAIN_TEXT.getLanguage(), project, features);
    myEditorPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    mySharedCheckbox = new JCheckBox(CopyrightBundle.message("share.profile.checkbox.title"), shareProfile);
    mySharedCheckbox.addActionListener(e -> {
      updater.run();
    });
    mySharedContextHelp = new JLabel(AllIcons.General.ContextHelp);
    mySharedContextHelp.setToolTipText(CopyrightBundle.message("share.profile.context.help"));
    mySharedContextHelp.setBorder(JBUI.Borders.empty(0, 5));
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

  @Nullable
  @Override
  protected JComponent createTopRightComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(BorderLayout.WEST, mySharedCheckbox);
    panel.add(BorderLayout.EAST, mySharedContextHelp);
    return panel;
  }

  @Override
  public JComponent createOptionsPanel() {
    myValidateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          VelocityHelper.verify(myEditor.getDocument().getText());
          Messages.showInfoMessage(myProject, CopyrightBundle.message("dialog.message.velocity.template.is.valid"),
                                   CopyrightBundle.message("dialog.title.validation"));
        }
        catch (Exception e1) {
          Messages.showInfoMessage(myProject, CopyrightBundle.message("dialog.message.velocity.template.contains.error.0", e1.getMessage()),
                                   CopyrightBundle.message("dialog.title.validation"));
        }
      }
    });
    myWholePanel.setBorder(JBUI.Borders.empty(5, 10, 10, 10));
    return myWholePanel;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return myCopyrightProfile.getName();
  }

  @Override
  public boolean isModified() {
    if (mySharedCheckbox.isSelected() != myShareProfile) return true;
    return myModified ||
           !Comparing.strEqual(EntityUtil.encode(myEditor.getDocument().getText()), myCopyrightProfile.getNotice()) ||
           !Comparing.strEqual(myKeywordTf.getText().trim(), myCopyrightProfile.getKeyword()) ||
           !Comparing.strEqual(myAllowReplaceTextField.getText().trim(), myCopyrightProfile.getAllowReplaceRegexp()) ||
           !Comparing.strEqual(myDisplayName, myCopyrightProfile.getName());
  }

  @Override
  public void apply() throws ConfigurationException {
    myCopyrightProfile.setNotice(EntityUtil.encode(myEditor.getDocument().getText()));
    myCopyrightProfile.setKeyword(validateRegexpAndGet(myKeywordTf.getText().trim(),
                                                       CopyrightBundle.message("detect.copyright.regexp.is.incorrect.configuration.error")));
    myCopyrightProfile.setAllowReplaceRegexp(validateRegexpAndGet(myAllowReplaceTextField.getText().trim(), CopyrightBundle.message("replace.copyright.regexp.is.incorrect.configuration.error")));
    myShareProfile = mySharedCheckbox.isSelected();
    if (myShareProfile) {
      IdeCopyrightManager.getInstance().removeCopyright(myCopyrightProfile);
      CopyrightManager.getInstance(myProject).replaceCopyright(myDisplayName, myCopyrightProfile);
    }
    else {
      CopyrightManager.getInstance(myProject).removeCopyright(myCopyrightProfile);
      IdeCopyrightManager.getInstance().replaceCopyright(myDisplayName, myCopyrightProfile);
    }
    myDisplayName = myCopyrightProfile.getName();
    myModified = false;
  }

  @NotNull
  private static String validateRegexpAndGet(final String regexp, final @NlsContexts.DialogMessage String message) throws ConfigurationException {
    try {
      if (!StringUtil.isEmptyOrSpaces(regexp)) {
        Pattern.compile(regexp);
      }
    }
    catch (PatternSyntaxException e) {
      throw new ConfigurationException(message + " " + e.getMessage());
    }
    return regexp;
  }

  @Override
  public void reset() {
    myDisplayName = myCopyrightProfile.getName();
    mySharedCheckbox.setSelected(myShareProfile);
    DocumentUtil.writeInRunUndoTransparentAction(() -> {
      String notice = myCopyrightProfile.getNotice();
      if (notice != null) {
        myEditor.getDocument().setText(EntityUtil.decode(notice));
        myEditor.setCaretPosition(0);
        myEditor.revalidate();
      }
    });
    myKeywordTf.setText(myCopyrightProfile.getKeyword());
    myAllowReplaceTextField.setText(myCopyrightProfile.getAllowReplaceRegexp());
  }

  public void setModified(boolean modified) {
    myModified = modified;
  }

  public boolean isShareProfile() {
    return mySharedCheckbox.isSelected();
  }
}
