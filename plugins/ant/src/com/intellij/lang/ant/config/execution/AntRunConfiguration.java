/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.lang.ant.config.impl.TargetChooserDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AntRunConfiguration extends LocatableConfigurationBase implements RunProfileWithCompileBeforeLaunchOption{
  private AntSettings mySettings = new AntSettings();

  public AntRunConfiguration(Project project,
                             ConfigurationFactory factory,
                             String name) {
    super(project, factory, name);
  }

  @Override
  public RunConfiguration clone() {
    AntRunConfiguration configuration = (AntRunConfiguration)super.clone();
    configuration.mySettings = mySettings.clone();
    return configuration;
  }

  @NotNull
  @Override
  public Module[] getModules() {
    return new Module[0];
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new SettingsEditor<RunConfiguration>() {
      private String myFileUrl = null;
      private String myTargetName = null;
      private final JTextField myTextField = new JTextField();
      private ActionListener myActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          AntBuildTarget buildTarget = getTarget();
          final TargetChooserDialog dlg = new TargetChooserDialog(getProject(), buildTarget);
          if (dlg.showAndGet()) {
            myFileUrl = null;
            myTargetName = null;
            buildTarget = dlg.getSelectedTarget();
            if (buildTarget != null) {
              final VirtualFile vFile = buildTarget.getModel().getBuildFile().getVirtualFile();
              if (vFile != null) {
                myFileUrl = vFile.getUrl();
                myTargetName = buildTarget.getName();
              }
            }
            updateTextField();
          }
        }
      };

      private void updateTextField() {
        myTextField.setText("");
        if (myFileUrl != null && myTargetName != null) {
          myTextField.setText(myTargetName);
        }
        fireEditorStateChanged();
      }

      @Override
      protected void resetEditorFrom(RunConfiguration s) {
        AntRunConfiguration configuration = (AntRunConfiguration)s;
        myFileUrl = configuration.mySettings.myFileUrl;
        myTargetName = configuration.mySettings.myTargetName;
        updateTextField();
      }

      @Override
      protected void applyEditorTo(RunConfiguration s) throws ConfigurationException {
        AntRunConfiguration configuration = (AntRunConfiguration)s;
        configuration.mySettings.myFileUrl = myFileUrl;
        configuration.mySettings.myTargetName = myTargetName;
      }

      @NotNull
      @Override
      protected JComponent createEditor() {
        myTextField.setEditable(false);
        return new TextFieldWithBrowseButton(myTextField, myActionListener);
      }
    };
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (!AntConfiguration.getInstance(getProject()).isInitialized()) {
      throw new RuntimeConfigurationException("Ant Configuration still haven't been initialized");
    }
    if (getTarget() == null)
      throw new RuntimeConfigurationException("Target is not specified", "Missing parameters");
  }

  @Override
  public String suggestedName() {
    AntBuildTarget target = getTarget();
    return target != null ? target.getDisplayName() : "";
  }


  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return new AntRunProfileState(env);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    mySettings.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    mySettings.writeExternal(element);
  }

  public AntBuildTarget getTarget() {
    return GlobalAntConfiguration.getInstance().findTarget(getProject(), mySettings.myFileUrl, mySettings.myTargetName);
  }

  public boolean acceptSettings(AntBuildTarget target) {
    VirtualFile virtualFile = target.getModel().getBuildFile().getVirtualFile();
    if (virtualFile == null) return false;
    mySettings.myFileUrl = virtualFile.getUrl();
    mySettings.myTargetName = target.getName();
    return true;
  }

  public static class AntSettings implements Cloneable, JDOMExternalizable {
    private static final String SETTINGS = "antsettings";
    private static final String FILE = "antfile";
    private static final String TARGET = "target";
    private String myFileUrl = null;
    private String myTargetName = null;

    public AntSettings() {
    }

    public AntSettings(String fileUrl, String targetName) {
      myFileUrl = fileUrl;
      myTargetName = targetName;
    }

    @Override
    public String toString() {
      return myTargetName + "@" + myFileUrl;
    }

    @Override
    protected AntSettings clone() {
      return new AntSettings(myFileUrl, myTargetName);
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      element = element.getChild(SETTINGS);
      if (element != null) {
        myFileUrl = element.getAttributeValue(FILE);
        myTargetName = element.getAttributeValue(TARGET);
      }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      if (myFileUrl != null && myTargetName != null) {
        Element child = new Element(SETTINGS);
        child.setAttribute(FILE, myFileUrl);
        child.setAttribute(TARGET, myTargetName);
        element.addContent(child);
      }
    }
  }
}
