// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.target.GradleRuntimeTargetUI;
import org.jetbrains.plugins.gradle.execution.target.GradleTargetUtil;
import org.jetbrains.plugins.gradle.execution.target.TargetPathFieldWithBrowseButton;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.execution.BuildLayoutParameters;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static org.jetbrains.plugins.gradle.execution.target.GradleTargetUtil.maybeGetTargetValue;
import static org.jetbrains.plugins.gradle.service.settings.IdeaGradleProjectSettingsControlBuilder.getIDEName;

/**
 * @author Vladislav.Soroka
 */
public class IdeaGradleSystemSettingsControlBuilder implements GradleSystemSettingsControlBuilder {

  private final @NotNull GradleSettings myInitialSettings;

  private boolean dropVmOptions;
  private boolean dropStoreExternallyCheckBox;
  private boolean dropDefaultProjectSettings;

  @SuppressWarnings("FieldCanBeLocal") // Used by reflection at showUi() and disposeUiResources()
  private @Nullable JBLabel myServiceDirectoryLabel;
  @SuppressWarnings("FieldCanBeLocal") // Used by reflection at showUi() and disposeUiResources()
  private @Nullable JBLabel myServiceDirectoryHint;
  private @Nullable TargetPathFieldWithBrowseButton myServiceDirectoryPathField;
  private @Nullable JBTextField myGradleVmOptionsField;
  @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"}) // Used by reflection at showUi() and disposeUiResources()
  private @NotNull List<Component> myGradleVmOptionsComponents = new ArrayList<>();
  private @Nullable JBCheckBox myGenerateImlFilesCheckBox;
  @SuppressWarnings("FieldCanBeLocal") // Used by reflection at showUi() and disposeUiResources()
  private @Nullable JBLabel myGenerateImlFilesHint;

  private final @NotNull GradleSettingsControl myDefaultProjectSettingsControl = new IdeaGradleDefaultProjectSettingsControl();

  public IdeaGradleSystemSettingsControlBuilder(@NotNull GradleSettings initialSettings) {
    myInitialSettings = initialSettings;
  }

  public IdeaGradleSystemSettingsControlBuilder dropStoreExternallyCheckBox() {
    dropStoreExternallyCheckBox = true;
    return this;
  }

  public IdeaGradleSystemSettingsControlBuilder dropVmOptions() {
    dropVmOptions = true;
    return this;
  }

  public IdeaGradleSystemSettingsControlBuilder dropDefaultProjectSettings() {
    dropDefaultProjectSettings = true;
    return this;
  }

  @Override
  public void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel) {
    addServiceDirectoryControl(canvas, indentLevel);
    if (!dropVmOptions) {
      addVMOptionsControl(canvas, indentLevel);
    }
    if (!dropStoreExternallyCheckBox) {
      addStoreExternallyCheckBox(canvas, indentLevel);
    }
    if (!dropDefaultProjectSettings) {
      myDefaultProjectSettingsControl.fillUi(canvas, indentLevel);
    }
  }

  @Override
  public void showUi(boolean show) {
    ExternalSystemUiUtil.showUi(this, show);
    myDefaultProjectSettingsControl.showUi(show);
  }

  @Override
  public void reset() {
    if (myServiceDirectoryPathField != null) {
      BuildLayoutParameters buildLayoutParameters = GradleInstallationManager.defaultBuildLayoutParameters(myInitialSettings.getProject());
      String gradleUserHomeDir = maybeGetTargetValue(buildLayoutParameters.getGradleUserHome()); //NON-NLS
      ((JBTextField)myServiceDirectoryPathField.getTextField()).getEmptyText().setText(gradleUserHomeDir);
      myServiceDirectoryPathField.setLocalPath(myInitialSettings.getServiceDirectoryPath());
    }

    if (myGradleVmOptionsField != null) {
      String vmOptions = trimIfPossible(myInitialSettings.getGradleVmOptions());
      myGradleVmOptionsField.setText(vmOptions);
      myGradleVmOptionsComponents.forEach(it -> {
        boolean showSetting = vmOptions != null || Registry.is("gradle.settings.showDeprecatedSettings", false);
        it.setVisible(showSetting);
      });
    }

    if (myGenerateImlFilesCheckBox != null) {
      myGenerateImlFilesCheckBox.setSelected(!myInitialSettings.getStoreProjectFilesExternally());
    }

    myDefaultProjectSettingsControl.reset();
  }

  @Override
  public boolean isModified() {
    if (myServiceDirectoryPathField != null &&
        !Objects.equals(ExternalSystemApiUtil.normalizePath(myServiceDirectoryPathField.getLocalPath()),
                        ExternalSystemApiUtil.normalizePath(myInitialSettings.getServiceDirectoryPath()))) {
      return true;
    }

    if (myGradleVmOptionsField != null && !Objects.equals(trimIfPossible(myGradleVmOptionsField.getText()), trimIfPossible(
      myInitialSettings.getGradleVmOptions()))) {
      return true;
    }

    if (myGenerateImlFilesCheckBox != null &&
        myGenerateImlFilesCheckBox.isSelected() == myInitialSettings.getStoreProjectFilesExternally()) {
      return true;
    }

    if (myDefaultProjectSettingsControl.isModified()) {
      return true;
    }

    return false;
  }

  @Override
  public void apply(@NotNull GradleSettings settings) {
    if (myServiceDirectoryPathField != null) {
      String serviceDirectoryPath = trimIfPossible(myServiceDirectoryPathField.getLocalPath());
      settings.setServiceDirectoryPath(ExternalSystemApiUtil.normalizePath(serviceDirectoryPath));
    }
    if (myGradleVmOptionsField != null) {
      settings.setGradleVmOptions(trimIfPossible(myGradleVmOptionsField.getText()));
    }
    if (myGenerateImlFilesCheckBox != null) {
      settings.setStoreProjectFilesExternally(!myGenerateImlFilesCheckBox.isSelected());
    }
    myDefaultProjectSettingsControl.apply();
  }

  @Override
  public boolean validate(@NotNull GradleSettings settings) {
    return myDefaultProjectSettingsControl.validate();
  }

  @Override
  public void disposeUIResources() {
    ExternalSystemUiUtil.disposeUi(this);
    myDefaultProjectSettingsControl.disposeUiResources();
  }

  @NotNull
  @Override
  public GradleSettings getInitialSettings() {
    return myInitialSettings;
  }

  private void addServiceDirectoryControl(PaintAwarePanel canvas, int indentLevel) {
    myServiceDirectoryLabel = new JBLabel(GradleBundle.message("gradle.settings.text.user.home"));
    myServiceDirectoryHint = new JBLabel(XmlStringUtil.wrapInHtml(GradleBundle.message("gradle.settings.text.user.home.hint")),
                                         UIUtil.ComponentStyle.SMALL);
    myServiceDirectoryHint.setForeground(UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER));
    myServiceDirectoryPathField = GradleRuntimeTargetUI
      .targetPathFieldWithBrowseButton(myInitialSettings.getProject(), GradleBundle.message("gradle.settings.text.user.home.dialog.title"));
    canvas.add(myServiceDirectoryLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    canvas.add(myServiceDirectoryPathField, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

    canvas.add(Box.createGlue(), ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    GridBag constraints = ExternalSystemUiUtil.getFillLineConstraints(indentLevel);
    constraints.insets.top = 0;
    canvas.add(myServiceDirectoryHint, constraints);

    myServiceDirectoryLabel.setLabelFor(myServiceDirectoryPathField);
  }

  private void addVMOptionsControl(@NotNull PaintAwarePanel canvas, int indentLevel) {
    JBLabel label = new JBLabel(GradleBundle.message("gradle.settings.text.vm.options"));
    canvas.add(label, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    myGradleVmOptionsComponents.add(label);

    myGradleVmOptionsField = new JBTextField();
    canvas.add(myGradleVmOptionsField, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    myGradleVmOptionsComponents.add(myGradleVmOptionsField);

    label.setLabelFor(myGradleVmOptionsField);

    Component glue = Box.createGlue();
    canvas.add(glue, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    myGradleVmOptionsComponents.add(glue);

    HyperlinkLabel fixLabel = new HyperlinkLabel();
    fixLabel.setFontSize(UIUtil.FontSize.SMALL);
    fixLabel.setForeground(UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER));
    fixLabel.setIcon(AllIcons.General.BalloonWarning12);
    label.setVerticalTextPosition(SwingConstants.TOP);
    GridBag constraints = ExternalSystemUiUtil.getFillLineConstraints(indentLevel);
    constraints.insets.top = 0;
    canvas.add(fixLabel, constraints);
    myGradleVmOptionsComponents.add(fixLabel);

    myGradleVmOptionsField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        boolean showMigration = e.getDocument().getLength() > 0;
        fixLabel.setHyperlinkText(
          GradleBundle.message("gradle.settings.text.vm.options.link.tooltip") + " ",
          showMigration ? GradleBundle.message("gradle.settings.text.vm.options.link.text") : "  ", "");
      }
    });
    myGradleVmOptionsField.setText(" "); // trigger listener

    fixLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        String jvmArgs = myGradleVmOptionsField.getText().trim();
        if (jvmArgs.isEmpty()) return;

        if (moveVMOptionsToGradleProperties(jvmArgs, myInitialSettings)) {
          myGradleVmOptionsField.setText(null);
          myGradleVmOptionsField.getEmptyText().setText(GradleBundle.message("gradle.settings.text.vm.options.empty.text"));
        }
      }
    });
  }

  private void addStoreExternallyCheckBox(@NotNull PaintAwarePanel canvas, int indentLevel) {
    myGenerateImlFilesCheckBox = new JBCheckBox(GradleBundle.message("gradle.settings.text.generate.iml.files"));
    canvas.add(myGenerateImlFilesCheckBox, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

    myGenerateImlFilesHint = new JBLabel(
      XmlStringUtil.wrapInHtml(GradleBundle.message("gradle.settings.text.generate.iml.files.hint", getIDEName())),
      UIUtil.ComponentStyle.SMALL);
    myGenerateImlFilesHint.setForeground(UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER));

    GridBag constraints = ExternalSystemUiUtil.getFillLineConstraints(indentLevel);
    constraints.insets.left += UIUtil.getCheckBoxTextHorizontalOffset(myGenerateImlFilesCheckBox);
    constraints.insets.top = 0;
    canvas.add(myGenerateImlFilesHint, constraints);
  }

  @Nullable
  private static String trimIfPossible(@Nullable String s) {
    return StringUtil.nullize(StringUtil.trim(s));
  }

  private boolean moveVMOptionsToGradleProperties(@NotNull String vmOptions, @NotNull GradleSettings settings) {
    File gradleUserHomeDir = null;
    if (myServiceDirectoryPathField != null) {
      String fieldText = trimIfPossible(myServiceDirectoryPathField.getText());
      if (fieldText != null) gradleUserHomeDir = new File(fieldText);
    }
    if (gradleUserHomeDir == null) {
      BuildLayoutParameters buildLayoutParameters = GradleInstallationManager.defaultBuildLayoutParameters(settings.getProject());
      String gradleUserHome = GradleTargetUtil.maybeGetLocalValue(buildLayoutParameters.getGradleUserHome());
      if (gradleUserHome == null) {
        Messages.showErrorDialog(settings.getProject(),
                                 GradleBundle.message("gradle.settings.text.vm.options.migration.error.text",
                                                      GradleBundle.message("gradle.settings.text.user.home.not.found.error.text")),
                                 GradleBundle.message("gradle.settings.text.vm.options.migration.error.title"));
        return false;
      }
      else {
        gradleUserHomeDir = new File(gradleUserHome);
      }
    }

    int result = Messages.showYesNoDialog(
      settings.getProject(),
      GradleBundle.message("gradle.settings.text.vm.options.confirm.text", new File(gradleUserHomeDir, "gradle.properties")),
      GradleBundle.message("gradle.title.gradle.settings"),
      getQuestionIcon());
    if (result != Messages.YES) return false;

    try {
      // get or create project dir
      if (!gradleUserHomeDir.exists()) {
        if (!FileUtil.createDirectory(gradleUserHomeDir)) {
          throw new IOException("Cannot create " + gradleUserHomeDir);
        }
      }

      // get or create project's gradle.properties
      File props = new File(gradleUserHomeDir, "gradle.properties");
      if (props.isDirectory()) throw new IOException(props.getPath() + " is a directory");

      String original = props.exists() ? FileUtil.loadFile(props) : "";
      String updated = updateVMOptions(original, vmOptions);
      if (!original.equals(updated)) {
        FileUtil.writeToFile(props, updated);
      }
    }
    catch (IOException e) {
      Messages.showErrorDialog(settings.getProject(),
                               GradleBundle.message("gradle.settings.text.vm.options.migration.error.text", e.getMessage()),
                               GradleBundle.message("gradle.settings.text.vm.options.migration.error.title"));
      return false;
    }

    return true;
  }

  private static final Pattern VM_OPTIONS_REGEX = Pattern.compile("^(\\s*\"?org\\.gradle\\.jvmargs\"?\\s*[=:]).*?(?<!\\\\)($)",
                                                                  Pattern.MULTILINE | Pattern.DOTALL);

  @NotNull
  public static String updateVMOptions(@NotNull String originalText, @NotNull String vmOptions) {
    Matcher matcher = VM_OPTIONS_REGEX.matcher(originalText);

    StringBuilder result = new StringBuilder(originalText.length() + vmOptions.length());

    String escapedValue = StringUtil.escapeProperty(vmOptions, false);
    if (matcher.find()) {
      matcher.appendReplacement(result, "$1" + Matcher.quoteReplacement(escapedValue) + "$2");
      matcher.appendTail(result);
    }
    else {
      result.append(originalText);
      if (!originalText.isEmpty() && !originalText.endsWith("\n")) result.append("\n");
      result.append("org.gradle.jvmargs=").append(escapedValue).append("\n");
    }
    return result.toString();
  }
}
