package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.gradle.importing.model.GradleProject;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages settings of {@link GradleProject gradle project} component.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:58 PM
 */
public class GradleProjectSettings implements GradleProjectStructureNodeSettings {

  private final JComponent                myComponent;
  private final GradleProject             myProject;
  private final JTextField                myNameControl;
  private final JComboBox                 myLanguageLevelComboBox;
  private final DefaultComboBoxModel      mySdkModel;
  private final JRadioButton              myRegisteredSdksButton;
  private final JRadioButton              myAllSdksButton;
  private final TextFieldWithBrowseButton myProjectConfigLocationField;
  private final TextFieldWithBrowseButton myProjectCompileOutputLocationField;
  
  private boolean myUseOnlyRegisteredSdks;

  public GradleProjectSettings(@NotNull GradleProject project) {
    myProject = project;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    myNameControl = GradleAdjustImportSettingsUtil.configureNameControl(builder, project);
    myLanguageLevelComboBox = setupLanguageLevelControls(builder);
    Pair<JRadioButton, JRadioButton> sdkPair = setupSdkControls(mySdkModel = new DefaultComboBoxModel(), builder);
    myRegisteredSdksButton = sdkPair.first;
    myAllSdksButton = sdkPair.second;
    myProjectConfigLocationField = setupProjectConfigLocation(builder);
    myProjectCompileOutputLocationField = setupProjectCompileOutputLocation(builder);
    filterSdksByLanguageLevel();    
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
    myRegisteredSdksButton.setEnabled(!matchedRegisteredSdks.isEmpty());
    
    if (myUseOnlyRegisteredSdks) {
      for (Sdk sdk : matchedRegisteredSdks) {
        mySdkModel.addElement(sdk.getName());
        if (sdk.getName().equals(selectedItem)) {
          restoreSelection = true;
        }
        if (matchedRegisteredSdks.isEmpty()) {
          // Change to 'all' radio button if none of the registered sdks matches target language level.
          myAllSdksButton.setSelected(true);
          myRegisteredSdksButton.setEnabled(false);
          return;
        }
      }
    }
    else {
      for (JavaSdkVersion version : JavaSdkVersion.values()) {
        if (!version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
          continue;
        }
        mySdkModel.addElement(version.getDescription());

        if (version.getDescription().equals(selectedItem)) {
          restoreSelection = true;
        }
      }
    }

    if (restoreSelection) {
      mySdkModel.setSelectedItem(selectedItem);
    } 
  }
  
  @NotNull
  private Pair<JRadioButton, JRadioButton> setupSdkControls(@NotNull ComboBoxModel model, @NotNull GradleProjectSettingsBuilder builder) {
    // Configure SDK combo box with all jdk versions.
    final JComboBox sdkComboBox = new JComboBox(model);
    sdkComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        Object selectedItem = sdkComboBox.getSelectedItem();
        if (selectedItem == null) {
          return;
        }
        if (myUseOnlyRegisteredSdks) {
          Sdk sdk = ProjectJdkTable.getInstance().findJdk(selectedItem.toString());
          if (sdk != null) {
            myProject.setSdk(sdk);
          } 
        }
        else {
          myProject.setJdkVersion(JavaSdkVersion.byDescription(selectedItem.toString()));
        }
      }
    });

    JRadioButton registeredSdksButton = new JRadioButton(GradleBundle.message("gradle.import.structure.settings.label.sdk.configured"));
    JRadioButton allSdksButton = new JRadioButton(GradleBundle.message("gradle.import.structure.settings.label.sdk.all"));
    List<Sdk> javaSdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
    if (javaSdks.isEmpty()) {
      // No jdk instances are configured, just use a simple label then
      builder.add("gradle.import.structure.settings.label.sdk", sdkComboBox);
    }
    else {
      JPanel jdkKeyPanel = new JPanel(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.anchor = GridBagConstraints.WEST;
      jdkKeyPanel.add(new JLabel(GradleBundle.message("gradle.import.structure.settings.label.sdk")), constraints);
      
      ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(allSdksButton);
      buttonGroup.add(registeredSdksButton);
      jdkKeyPanel.add(allSdksButton, constraints);
      constraints.weightx = 1;
      jdkKeyPanel.add(registeredSdksButton, constraints);

      registeredSdksButton.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          myUseOnlyRegisteredSdks = e.getStateChange() == ItemEvent.SELECTED;
          filterSdksByLanguageLevel();
        }
      });
      allSdksButton.setSelected(true);
      builder.add(jdkKeyPanel, sdkComboBox);
    }
    return new Pair<JRadioButton, JRadioButton>(registeredSdksButton, allSdksButton);
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
  
  @Override
  public boolean validate() {
    boolean nameIsValid = GradleAdjustImportSettingsUtil.validate(myProject, myNameControl);
    if (!nameIsValid) {
      return false;
    }
    
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
    myNameControl.setText(myProject.getName());
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
