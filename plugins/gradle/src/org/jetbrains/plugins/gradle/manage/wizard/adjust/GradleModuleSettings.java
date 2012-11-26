package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.model.gradle.SourceType;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;

/**
 * Manages settings of {@link GradleModule gradle module} component.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 3:39 PM
 */
public class GradleModuleSettings implements GradleProjectStructureNodeSettings {

  private final JComponent                myComponent;
  private final GradleModule              myModule;
  private final JTextField                myNameControl;
  private final TextFieldWithBrowseButton myModuleFileLocationField;
  private final JRadioButton              myInheritProjectCompileOutputPathButton;
  private final JRadioButton              myUseModuleCompileOutputPathButton;
  private final TextFieldWithBrowseButton myOutputLocationField;
  private final TextFieldWithBrowseButton myTestOutputLocationField;

  public GradleModuleSettings(@NotNull GradleModule module) {
    myModule = module;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    myNameControl = GradleAdjustImportSettingsUtil.configureNameControl(builder, myModule);
    myModuleFileLocationField = setupModuleFileLocation(builder);
    Pair<JRadioButton, JRadioButton> pair = setupCompileOutput(builder);
    myInheritProjectCompileOutputPathButton = pair.first;
    myUseModuleCompileOutputPathButton = pair.second;
    Pair<TextFieldWithBrowseButton, TextFieldWithBrowseButton> locationPair = setupOutputLocation(builder);
    myOutputLocationField = locationPair.first;
    myTestOutputLocationField = locationPair.second;
    myComponent = builder.build();
    refresh();
  }
  
  @NotNull
  private Pair<JRadioButton, JRadioButton> setupCompileOutput(@NotNull GradleProjectSettingsBuilder builder) {
    JRadioButton inheritButton = new JRadioButton(ProjectBundle.message("project.inherit.compile.output.path"));
    JRadioButton moduleButton = new JRadioButton(ProjectBundle.message("project.module.compile.output.path"));
    ButtonGroup group = new ButtonGroup();
    group.add(inheritButton);
    group.add(moduleButton);
    builder.add(inheritButton);
    builder.add(moduleButton, GradleProjectSettingsBuilder.InsetSize.SMALL);
    ItemListener listener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myOutputLocationField.setEditable(myUseModuleCompileOutputPathButton.isSelected());
        myTestOutputLocationField.setEditable(myUseModuleCompileOutputPathButton.isSelected());
      }
    };
    inheritButton.addItemListener(listener);
    moduleButton.addItemListener(listener);
    return new Pair<JRadioButton, JRadioButton>(inheritButton, moduleButton);
  }
  
  @NotNull
  private static TextFieldWithBrowseButton setupModuleFileLocation(@NotNull GradleProjectSettingsBuilder builder) {
    TextFieldWithBrowseButton result = new TextFieldWithBrowseButton();
    String title = GradleBundle.message("gradle.import.structure.settings.title.module.config.location");
    result.addBrowseFolderListener(title, "", null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    builder.add("gradle.import.structure.settings.label.module.config.location", result);
    return result;
  }
  
  @NotNull
  private static Pair<TextFieldWithBrowseButton, TextFieldWithBrowseButton> setupOutputLocation(
    @NotNull GradleProjectSettingsBuilder builder)
  {
    TextFieldWithBrowseButton outputField = new TextFieldWithBrowseButton();
    String title = ProjectBundle.message("module.paths.output.label");
    outputField.addBrowseFolderListener(title, "", null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    builder.add(new JLabel(title), outputField, GradleProjectSettingsBuilder.InsetSize.SMALL);

    TextFieldWithBrowseButton testOutputField = new TextFieldWithBrowseButton();
    title = ProjectBundle.message("module.paths.test.output.label");
    testOutputField.addBrowseFolderListener(title, "", null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    builder.add(new JLabel(title), testOutputField, GradleProjectSettingsBuilder.InsetSize.SMALL); 
    
    return new Pair<TextFieldWithBrowseButton, TextFieldWithBrowseButton>(outputField, testOutputField);
  }

  @Override
  public boolean validate() {
    if (!GradleAdjustImportSettingsUtil.validate(myModule, myNameControl)) {
      return false;
    }
    String moduleFileDir = myModuleFileLocationField.getText();
    if (moduleFileDir == null || StringUtil.isEmpty(moduleFileDir.trim())) {
      GradleUtil.showBalloon(
        myModuleFileLocationField,
        MessageType.ERROR,
        GradleBundle.message("gradle.import.text.error.module.undefined.config.location")
      );
      return false;
    }
    myModule.setModuleFileDirectoryPath(moduleFileDir.trim());
    if (myUseModuleCompileOutputPathButton.isSelected()) {
      String outputLocation = myOutputLocationField.getText();
      if (outputLocation == null || StringUtil.isEmpty(outputLocation.trim())) {
        GradleUtil.showBalloon(
          myOutputLocationField,
          MessageType.ERROR,
          GradleBundle.message("gradle.import.text.error.file.module.compile.output.location")
        );
        return false;
      }
      String testOutputLocation = myTestOutputLocationField.getText();
      if (testOutputLocation == null || StringUtil.isEmpty(testOutputLocation.trim())) {
        GradleUtil.showBalloon(
          myTestOutputLocationField,
          MessageType.ERROR,
          GradleBundle.message("gradle.import.text.error.file.module.test.output.location")
        );
        return false;
      }
      myModule.setCompileOutputPath(SourceType.SOURCE, outputLocation.trim());
      myModule.setCompileOutputPath(SourceType.TEST, testOutputLocation.trim());
    }
    myModule.setInheritProjectCompileOutputPath(myInheritProjectCompileOutputPathButton.isSelected());
    return true;
  }

  @Override
  public void refresh() {
    myNameControl.setText(myModule.getName());
    myModuleFileLocationField.setText(new File(myModule.getModuleFilePath()).getParent());
    if (myModule.isInheritProjectCompileOutputPath()) {
      myInheritProjectCompileOutputPathButton.setSelected(true);
    }
    else {
      myUseModuleCompileOutputPathButton.setSelected(true);
    }
    myOutputLocationField.setText(myModule.getCompileOutputPath(SourceType.SOURCE));
    myTestOutputLocationField.setText(myModule.getCompileOutputPath(SourceType.TEST));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
