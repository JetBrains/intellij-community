package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.Options;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CopyrightConfigurable extends NamedConfigurable<CopyrightProfile>{
    private CopyrightProfile myCopyrightProfile;
    private OptionsPanel myOptionsPanel;
    private Project myProject;
    private boolean myModified;

    private String myDisplayName;

    public CopyrightConfigurable(Project project, CopyrightProfile copyrightProfile, Runnable updater) {
        super(true, updater);
        myProject = project;
        myCopyrightProfile = copyrightProfile;
        myOptionsPanel = new OptionsPanel(project, copyrightProfile.getOptions());
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
        return myOptionsPanel.getMainComponent();
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
        return myModified || myOptionsPanel.isModified(myCopyrightProfile.getOptions()) || !Comparing.strEqual(myDisplayName, myCopyrightProfile.getName());
    }

    public void apply() throws ConfigurationException {
        final Options options = myOptionsPanel.getOptions();
        myCopyrightProfile.setOptions(options);
        CopyrightManager.getInstance(myProject).addCopyright(myCopyrightProfile);
        myModified = false;
    }

    public void reset() {
       myOptionsPanel.setOptions(myCopyrightProfile.getOptions());
       myDisplayName = myCopyrightProfile.getName();
    }

    public void disposeUIResources() {
      myOptionsPanel = null;
    }

    public void setModified(boolean modified) {
        myModified = modified;
    }
}
