/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.model.settings.LocationSettingType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;

/**
 * Manages gradle settings not specific to particular project (e.g. 'use wrapper' is project-level setting but 'gradle user home' is
 * a global one).
 * 
 * @author Denis Zhdanov
 * @since 4/28/13 11:06 AM
 */
public class GradleSystemSettingsControl implements ExternalSystemSettingsControl<GradleSettings> {

  @NotNull private final GradleSettings myInitialSettings;

  @SuppressWarnings("FieldCanBeLocal") // Used by reflection at showUi() and disposeUiResources()
  private JBLabel                   myServiceDirectoryLabel;
  private TextFieldWithBrowseButton myServiceDirectoryPathField;
  @SuppressWarnings("FieldCanBeLocal")  // Used by reflection at showUi() and disposeUiResources()
  private JBLabel                   myGradleVmOptionsLabel;
  private JBTextField               myGradleVmOptionsField;
  private boolean                   myServiceDirectoryPathModifiedByUser;

  public GradleSystemSettingsControl(@NotNull GradleSettings settings) {
    myInitialSettings = settings;
  }

  @Override
  public void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel) {
    myServiceDirectoryLabel = new JBLabel(GradleBundle.message("gradle.settings.text.service.dir.path"));
    preparePathControl();
    canvas.add(myServiceDirectoryLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    canvas.add(myServiceDirectoryPathField, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

    myGradleVmOptionsLabel = new JBLabel(GradleBundle.message("gradle.settings.text.vm.options"));
    canvas.add(myGradleVmOptionsLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    myGradleVmOptionsField = new JBTextField();
    canvas.add(myGradleVmOptionsField, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
  }

  @Override
  public void showUi(boolean show) {
    ExternalSystemUiUtil.showUi(this, show);
  }

  private void preparePathControl() {
    myServiceDirectoryPathField = new TextFieldWithBrowseButton();
    myServiceDirectoryPathField.addBrowseFolderListener("",
                                                        GradleBundle.message("gradle.settings.title.service.dir.path"),
                                                        null,
                                                        new FileChooserDescriptor(false, true, false, false, false, false),
                                                        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                                        false);
    myServiceDirectoryPathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myServiceDirectoryPathModifiedByUser = true;
        myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myServiceDirectoryPathModifiedByUser = true;
        myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    });
  }

  @Override
  public void reset() {
    myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
    myServiceDirectoryPathField.setText("");
    String path = myInitialSettings.getServiceDirectoryPath();
    if (StringUtil.isEmpty(path)) {
      deduceServiceDirectoryIfPossible();
    }
    else {
      myServiceDirectoryPathField.setText(path);
    }
    
    myGradleVmOptionsField.setText(trimIfPossible(myInitialSettings.getGradleVmOptions()));
  }

  private void deduceServiceDirectoryIfPossible() {
    String path = System.getenv().get(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY);
    if (StringUtil.isEmpty(path)) {
      path = new File(System.getProperty("user.home"), ".gradle").getAbsolutePath();
    }
    myServiceDirectoryPathField.setText(path);
    myServiceDirectoryPathField.getTextField().setForeground(LocationSettingType.DEDUCED.getColor());
    myServiceDirectoryPathModifiedByUser = false;
  }

  @Override
  public boolean isModified() {
    return (myServiceDirectoryPathModifiedByUser
           && !Comparing.equal(ExternalSystemApiUtil.normalizePath(myServiceDirectoryPathField.getText()),
                               ExternalSystemApiUtil.normalizePath(myInitialSettings.getServiceDirectoryPath())))
           || !Comparing.equal(trimIfPossible(myGradleVmOptionsField.getText()), trimIfPossible(myInitialSettings.getGradleVmOptions()));
  }

  @Nullable
  private static String trimIfPossible(@Nullable String s) {
    if (s == null) {
      return null;
    }
    String result = s.trim();
    return result.isEmpty() ? null : result;
  }

  @Override
  public void apply(@NotNull GradleSettings settings) {
    if (myServiceDirectoryPathModifiedByUser) {
      settings.setServiceDirectoryPath(ExternalSystemApiUtil.normalizePath(myServiceDirectoryPathField.getText()));
    }
    settings.setGradleVmOptions(trimIfPossible(myGradleVmOptionsField.getText()));
  }

  @Override
  public boolean validate(@NotNull GradleSettings settings) throws ConfigurationException {
    return true;
  }

  @Override
  public void disposeUIResources() {
    ExternalSystemUiUtil.disposeUi(this);
  }
}
