package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Manages settings of {@link GradleProject gradle project} component.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:58 PM
 */
public class GradleProjectSettings implements GradleProjectStructureNodeSettings {
  
  private static final String KEEP_IML_AT_MODULE_DIR_PROPERTY_KEY = "gradle.iml.at.module.dir";
  
  @NotNull private final JComponent                myComponent;
  @NotNull private final GradleProject             myProject;
  @NotNull private final JComboBox                 myLanguageLevelComboBox;
  @NotNull private final DefaultComboBoxModel      mySdkModel;
  @NotNull private final TextFieldWithBrowseButton myProjectConfigLocationField;
  @NotNull private final TextFieldWithBrowseButton myProjectCompileOutputLocationField;
  @NotNull private final JRadioButton              myImlAtModuleContentRootsButton;
  @NotNull private final JRadioButton              myImlAtProjectDirButton;

  public GradleProjectSettings(@NotNull GradleProject project) {
    myProject = project;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    myLanguageLevelComboBox = setupLanguageLevelControls(builder);
    setupSdkControls(mySdkModel = new DefaultComboBoxModel(), builder);
    myProjectConfigLocationField = setupProjectConfigLocation(builder);
    myProjectCompileOutputLocationField = setupProjectCompileOutputLocation(builder);
    filterSdksByLanguageLevel();
    myImlAtModuleContentRootsButton = new JBRadioButton(GradleBundle.message("gradle.import.structure.settings.label.iml.location.per.module"));
    myImlAtProjectDirButton = new JBRadioButton(GradleBundle.message("gradle.import.structure.settings.label.iml.location.project.dir"));
    setModuleFilesLocationControl(builder);
    myComponent = builder.build();
    refresh();
  }

  private JComboBox setupLanguageLevelControls(@NotNull GradleProjectSettingsBuilder builder) {
    JComboBox result = new JComboBox();
    final Map<Object, LanguageLevel> levels = new HashMap<Object, LanguageLevel>();
    for (LanguageLevel level : LanguageLevel.values()) {
      levels.put(level.getPresentableText(), level);
      result.addItem(level.getPresentableText());
    }
    result.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myProject.setLanguageLevel(levels.get(e.getItem()));
        filterSdksByLanguageLevel();
      }
    });
    builder.add("gradle.import.structure.settings.label.language.level", result);
    return result;
  }
  
  private void filterSdksByLanguageLevel() {
    Object selectedItem = mySdkModel.getSelectedItem();
    mySdkModel.removeAllElements();
    LanguageLevel languageLevel = myProject.getLanguageLevel();
    boolean restoreSelection = false;
    List<Sdk> matchedRegisteredSdks = new ArrayList<Sdk>();
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> javaSdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    for (Sdk sdk : javaSdks) {
      JavaSdkVersion version = javaSdk.getVersion(sdk);
      if (version == null || !version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
        continue;
      }
      matchedRegisteredSdks.add(sdk);
    }

    if (matchedRegisteredSdks.isEmpty()) {
      mySdkModel.addElement(GradleBundle.message("gradle.import.structure.settings.no.sdk.for.language.level.text"));
    }

    for (Sdk sdk : matchedRegisteredSdks) {
      mySdkModel.addElement(sdk.getName());
      if (sdk.getName().equals(selectedItem)) {
        restoreSelection = true;
      }
    }

    if (restoreSelection) {
      mySdkModel.setSelectedItem(selectedItem);
    }
  }
  
  private void setupSdkControls(@NotNull ComboBoxModel model, @NotNull GradleProjectSettingsBuilder builder) {
    // Configure SDK combo box with all jdk versions.
    final JComboBox sdkComboBox = new JComboBox(model);
    sdkComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        Object selectedItem = sdkComboBox.getSelectedItem();
        if (selectedItem == null) {
          return;
        }
        Sdk sdk = ProjectJdkTable.getInstance().findJdk(selectedItem.toString());
        if (sdk != null) {
          myProject.setSdk(sdk);
        }
      }
    });
    builder.add("gradle.import.structure.settings.label.sdk", sdkComboBox);
  }
  
  @NotNull
  private TextFieldWithBrowseButton setupProjectConfigLocation(@NotNull GradleProjectSettingsBuilder builder) {
    TextFieldWithBrowseButton result = new TextFieldWithBrowseButton();
    String title = GradleBundle.message("gradle.import.structure.settings.title.project.config.location");
    result.addBrowseFolderListener(title, "", null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    result.setText(myProject.getProjectFileDirectoryPath());
    builder.add("gradle.import.structure.settings.label.project.config.location", result);
    return result;
  }

  @NotNull
  private TextFieldWithBrowseButton setupProjectCompileOutputLocation(@NotNull GradleProjectSettingsBuilder builder) {
    TextFieldWithBrowseButton result = new TextFieldWithBrowseButton();
    String title = GradleBundle.message("gradle.import.structure.settings.title.project.compile.output.location");
    result.addBrowseFolderListener(title, "", null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    result.setText(myProject.getCompileOutputPath());
    builder.add("gradle.import.structure.settings.label.project.compile.output.location", result);
    return result;
  }

  private void setModuleFilesLocationControl(@NotNull GradleProjectSettingsBuilder builder) {
    ButtonGroup group = new ButtonGroup();
    group.add(myImlAtModuleContentRootsButton);
    group.add(myImlAtProjectDirButton);
    JPanel panel = new JPanel(new GridLayout(2, 1));
    panel.add(myImlAtModuleContentRootsButton);
    panel.add(myImlAtProjectDirButton);
    myImlAtModuleContentRootsButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) {
          return;
        }
        for (GradleModule module : myProject.getModules()) {
          Collection<GradleContentRoot> contentRoots = module.getContentRoots();
          if (contentRoots.isEmpty()) {
            continue;
          }
          module.setModuleFileDirectoryPath(contentRoots.iterator().next().getRootPath());
        }
        PropertiesComponent.getInstance().setValue(KEEP_IML_AT_MODULE_DIR_PROPERTY_KEY, String.valueOf(true));
      }
    });
    myImlAtProjectDirButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) {
          return;
        }
        String dirPath = myProjectConfigLocationField.getText();
        if (StringUtil.isEmpty(dirPath)) {
          return;
        }
        boolean goodDir = FileUtilRt.createDirectory(new File(dirPath));
        if (!goodDir) {
          return;
        }
        for (GradleModule module : myProject.getModules()) {
          module.setModuleFileDirectoryPath(dirPath);
        }
        PropertiesComponent.getInstance().setValue(KEEP_IML_AT_MODULE_DIR_PROPERTY_KEY, String.valueOf(false));
      }
    });
    boolean imlAtModuleDir = PropertiesComponent.getInstance().getBoolean(KEEP_IML_AT_MODULE_DIR_PROPERTY_KEY, true);
    if (imlAtModuleDir) {
      myImlAtModuleContentRootsButton.setSelected(true);
    }
    else {
      myImlAtProjectDirButton.setSelected(true);
    }
    builder.add("gradle.import.structure.settings.label.iml.location", panel);
  }
  
  @Override
  public boolean validate() {
    if (!validateDirLocation(myProjectConfigLocationField, "gradle.import.text.error.project.undefined.config.location",
                             "gradle.import.text.error.file.config.location"))
    {
      return false;
    }
    myProject.setProjectFileDirectoryPath(myProjectConfigLocationField.getText());
    
    if (!validateDirLocation(myProjectCompileOutputLocationField, "gradle.import.text.error.undefined.project.compile.output.location",
                             "gradle.import.text.error.file.project.compile.output.location"))
    {
      return false;
    }
    myProject.setCompileOutputPath(myProjectCompileOutputLocationField.getText());
    
    return true;
  }

  @Override
  public void refresh() {
    myProjectConfigLocationField.setText(myProject.getProjectFileDirectoryPath());
    myProjectCompileOutputLocationField.setText(myProject.getCompileOutputPath());
    myLanguageLevelComboBox.setSelectedItem(myProject.getLanguageLevel().getPresentableText());
  }

  private static boolean validateDirLocation(
    @NotNull TextFieldWithBrowseButton dataHolder,
    @NotNull @PropertyKey(resourceBundle = GradleBundle.PATH_TO_BUNDLE)String undefinedPathMessageKey,
    @NotNull @PropertyKey(resourceBundle = GradleBundle.PATH_TO_BUNDLE) String filePathMessageKey)
  {
    String path = dataHolder.getText();
    if (path == null || path.trim().isEmpty()) {
      GradleUtil.showBalloon(dataHolder, MessageType.ERROR, GradleBundle.message(undefinedPathMessageKey));
      return false;
    }
    else if (new File(path).isFile()) {
      GradleUtil.showBalloon(dataHolder, MessageType.ERROR, GradleBundle.message(filePathMessageKey));
      return false;
    }
    return true;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
