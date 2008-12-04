package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.util.VelocityHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CopyrightConfigurable extends NamedConfigurable<CopyrightProfile> {
  private CopyrightProfile myCopyrightProfile;
  private JPanel myWholePanel;

  private Project myProject;
  private boolean myModified;

  private String myDisplayName;
  private JEditorPane myCopyrightPane;
  private JButton myValidateButton;
  private JTextField myKeywordTf;

  public CopyrightConfigurable(Project project, CopyrightProfile copyrightProfile, Runnable updater) {
    super(true, updater);
    myProject = project;
    myCopyrightProfile = copyrightProfile;
    myDisplayName = myCopyrightProfile.getName();
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
          VelocityHelper.verify(myCopyrightPane.getText());
          Messages.showInfoMessage(myProject, "Velocity template valid.", "Validation");
        }
        catch (Exception e1) {
          Messages.showInfoMessage(myProject, "Velocity template error:\n" + e1.getMessage(), "Validation");
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
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public boolean isModified() {
    return myModified ||
           !Comparing.strEqual(myCopyrightPane.getText().trim(), myCopyrightProfile.getNotice()) ||
           !Comparing.strEqual(myKeywordTf.getText().trim(), myCopyrightProfile.getKeyword()) ||
           !Comparing.strEqual(myDisplayName, myCopyrightProfile.getName());
  }

  public void apply() throws ConfigurationException {
    myCopyrightProfile.setNotice(myCopyrightPane.getText());
    myCopyrightProfile.setKeyword(myKeywordTf.getText());
    CopyrightManager.getInstance(myProject).addCopyright(myCopyrightProfile);
    myModified = false;
  }

  public void reset() {
    myDisplayName = myCopyrightProfile.getName();
    myCopyrightPane.setText(myCopyrightProfile.getNotice());
    myKeywordTf.setText(myCopyrightProfile.getKeyword());
  }

  public void disposeUIResources() {
  }

  public void setModified(boolean modified) {
    myModified = modified;
  }
}
