/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.facet;

import com.android.SdkConstants;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.compiler.AndroidAptCompiler;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.maven.AndroidMavenProvider;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class AndroidFacetEditorTab extends FacetEditorTab {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidFacetEditorTab");

  private final AndroidFacetConfiguration myConfiguration;
  private final FacetEditorContext myContext;
  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myRGenPathField;
  private TextFieldWithBrowseButton myAidlGenPathField;
  private JButton myResetPathsButton;
  private TextFieldWithBrowseButton myResFolderField;
  private TextFieldWithBrowseButton myAssetsFolderField;
  private TextFieldWithBrowseButton myNativeLibsFolder;
  private TextFieldWithBrowseButton myManifestFileField;
  private JRadioButton myUseAptResDirectoryFromPathRadio;
  private JRadioButton myUseCustomSourceDirectoryRadio;
  private TextFieldWithBrowseButton myCustomAptSourceDirField;
  private JCheckBox myIsLibraryProjectCheckbox;
  private JPanel myAaptCompilerPanel;
  private JCheckBox myGenerateUnsignedApk;
  private ComboboxWithBrowseButton myApkPathCombo;
  private JLabel myApkPathLabel;
  private JRadioButton myRunProcessResourcesRadio;
  private JRadioButton myCompileResourcesByIdeRadio;
  private JLabel myManifestFileLabel;
  private JLabel myResFolderLabel;
  private JLabel myAssetsFolderLabel;
  private JLabel myNativeLibsFolderLabel;
  private JLabel myAidlGenPathLabel;
  private JLabel myRGenPathLabel;
  private TextFieldWithBrowseButton myCustomDebugKeystoreField;
  private JBLabel myCustomKeystoreLabel;
  private JCheckBox myIncludeTestCodeAndCheckBox;
  private JBCheckBox myRunProguardCheckBox;
  private JBLabel myProguardConfigFileLabel;
  private TextFieldWithBrowseButton myProguardConfigFileTextField;
  private JCheckBox myIncludeSystemProguardFileCheckBox;
  private JBCheckBox myIncludeAssetsFromLibraries;

  public AndroidFacetEditorTab(FacetEditorContext context, AndroidFacetConfiguration androidFacetConfiguration) {
    final Project project = context.getProject();
    myConfiguration = androidFacetConfiguration;
    myContext = context;

    myManifestFileLabel.setLabelFor(myManifestFileField);
    myResFolderLabel.setLabelFor(myResFolderField);
    myAssetsFolderLabel.setLabelFor(myAssetsFolderField);
    myNativeLibsFolderLabel.setLabelFor(myNativeLibsFolder);
    myAidlGenPathLabel.setLabelFor(myAidlGenPathField);
    myRGenPathLabel.setLabelFor(myRGenPathField);
    myCustomKeystoreLabel.setLabelFor(myCustomDebugKeystoreField);
    myProguardConfigFileLabel.setLabelFor(myProguardConfigFileTextField);

    final AndroidFacet facet = (AndroidFacet)myContext.getFacet();

    myRGenPathField.getButton()
      .addActionListener(new MyGenSourceFieldListener(myRGenPathField, AndroidRootUtil.getAptGenSourceRootPath(facet)));
    myAidlGenPathField.getButton()
      .addActionListener(new MyGenSourceFieldListener(myAidlGenPathField, AndroidRootUtil.getAidlGenSourceRootPath(facet)));

    Module module = myContext.getModule();
    
    myManifestFileField.getButton().addActionListener(
      new MyFolderFieldListener(myManifestFileField, AndroidRootUtil.getManifestFile(facet), true, new MyManifestFilter()));
    
    myResFolderField.getButton().addActionListener(new MyFolderFieldListener(myResFolderField,
                                                                             AndroidRootUtil.getResourceDir(facet), false, null));
    
    myAssetsFolderField.getButton().addActionListener(new MyFolderFieldListener(myAssetsFolderField,
                                                                                AndroidRootUtil.getAssetsDir(facet), false, null));
    
    myNativeLibsFolder.getButton().addActionListener(new MyFolderFieldListener(myNativeLibsFolder,
                                                                               AndroidRootUtil.getLibsDir(facet), false, null));

    myCustomAptSourceDirField.getButton().addActionListener(new MyFolderFieldListener(myCustomAptSourceDirField,
                                                                                      AndroidAptCompiler.getCustomResourceDirForApt(facet),
                                                                                      false, null));

    myProguardConfigFileTextField.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String path = myProguardConfigFileTextField.getText().trim();
        VirtualFile defaultFile = path != null && path.length() > 0
                                  ? LocalFileSystem.getInstance().findFileByPath(path)
                                  : null;

        if (defaultFile == null) {
          defaultFile = AndroidRootUtil.getMainContentRoot(facet);
        }
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        final VirtualFile file = FileChooser.chooseFile(descriptor, myContentPanel, project, defaultFile);
        if (file != null) {
          myProguardConfigFileTextField.setText(FileUtil.toSystemDependentName(file.getPath()));
        }
      }
    });

    myRunProguardCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean enabled = myRunProguardCheckBox.isSelected();
        myProguardConfigFileLabel.setEnabled(enabled);
        myProguardConfigFileTextField.setEnabled(enabled);
        myIncludeSystemProguardFileCheckBox.setEnabled(enabled);
      }
    });
    
    myCustomDebugKeystoreField.getButton().addActionListener(new MyFolderFieldListener(myCustomDebugKeystoreField, null, true, null));

    myResetPathsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidFacetConfiguration configuration = new AndroidFacetConfiguration();
        configuration.setFacet((AndroidFacet)myContext.getFacet());
        Module module = myContext.getModule();
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        if (contentRoots.length == 1) {
          configuration.init(module, contentRoots[0]);
        }
        if (AndroidMavenUtil.isMavenizedModule(module)) {
          AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
          if (mavenProvider != null) {
            mavenProvider.setPathsToDefault(module, configuration);
          }
        }
        resetOptions(configuration);
      }
    });

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCustomAptSourceDirField.setEnabled(myUseCustomSourceDirectoryRadio.isSelected());
      }
    };
    myUseCustomSourceDirectoryRadio.addActionListener(listener);
    myUseAptResDirectoryFromPathRadio.addActionListener(listener);

    myIsLibraryProjectCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean lib = myIsLibraryProjectCheckbox.isSelected();
        myAssetsFolderField.setEnabled(!lib);
      }
    });

    listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateAptPanel();
      }
    };
    myRunProcessResourcesRadio.addActionListener(listener);
    myCompileResourcesByIdeRadio.addActionListener(listener);

    myApkPathLabel.setLabelFor(myApkPathCombo);

    final JComboBox apkPathComboBoxComponent = myApkPathCombo.getComboBox();
    apkPathComboBoxComponent.setEditable(true);
    apkPathComboBoxComponent.setModel(new DefaultComboBoxModel(getDefaultApks(module)));
    apkPathComboBoxComponent.setMinimumSize(new Dimension(10, apkPathComboBoxComponent.getMinimumSize().height));
    apkPathComboBoxComponent.setPreferredSize(new Dimension(10, apkPathComboBoxComponent.getPreferredSize().height));

    myApkPathCombo.addBrowseFolderListener(project, new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || "apk".equals(file.getExtension());
      }
    });
  }

  private void updateAptPanel() {
    boolean enabled = !myRunProcessResourcesRadio.isVisible() || !myRunProcessResourcesRadio.isSelected();
    UIUtil.setEnabled(myAaptCompilerPanel, enabled, true);
  }

  private static String[] getDefaultApks(@NotNull Module module) {
    List<String> result = new ArrayList<String>();
    String path = AndroidCompileUtil.getOutputPackage(module);
    if (path != null) {
      result.add(path);
    }
    AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
    if (mavenProvider != null && mavenProvider.isMavenizedModule(module)) {
      String buildDirectory = mavenProvider.getBuildDirectory(module);
      if (buildDirectory != null) {
        result.add(FileUtil.toSystemDependentName(buildDirectory + '/' + AndroidCompileUtil.getApkName(module)));
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @Nls
  public String getDisplayName() {
    return "Android SDK Settings";
  }

  public JComponent createComponent() {
    return myContentPanel;
  }

  public boolean isModified() {
    //if (myAddAndroidLibrary.isSelected() != myConfiguration.ADD_ANDROID_LIBRARY) return true;
    if (myIsLibraryProjectCheckbox.isSelected() != myConfiguration.LIBRARY_PROJECT) return true;

    if (checkRelativePath(myConfiguration.GEN_FOLDER_RELATIVE_PATH_APT, myRGenPathField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.GEN_FOLDER_RELATIVE_PATH_AIDL, myAidlGenPathField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.MANIFEST_FILE_RELATIVE_PATH, myManifestFileField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.RES_FOLDER_RELATIVE_PATH, myResFolderField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.ASSETS_FOLDER_RELATIVE_PATH, myAssetsFolderField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.LIBS_FOLDER_RELATIVE_PATH, myNativeLibsFolder.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.APK_PATH, (String)myApkPathCombo.getComboBox().getEditor().getItem())) {
      return true;
    }

    if (myUseCustomSourceDirectoryRadio.isSelected() != myConfiguration.USE_CUSTOM_APK_RESOURCE_FOLDER) {
      return true;
    }
    if (checkRelativePath(myConfiguration.CUSTOM_APK_RESOURCE_FOLDER, myCustomAptSourceDirField.getText())) {
      return true;
    }

    if (myRunProcessResourcesRadio.isSelected() != myConfiguration.RUN_PROCESS_RESOURCES_MAVEN_TASK) {
      return true;
    }
    if (myGenerateUnsignedApk.isSelected() != myConfiguration.GENERATE_UNSIGNED_APK) {
      return true;
    }
    if (!myConfiguration.CUSTOM_DEBUG_KEYSTORE_PATH.equals(getSelectedCustomKeystorePath())) {
      return true;
    }
    if (myConfiguration.PACK_TEST_CODE != myIncludeTestCodeAndCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.isIncludeAssetsFromLibraries() != myIncludeAssetsFromLibraries.isSelected()) {
      return true;
    }

    if (checkRelativePath(myConfiguration.PROGUARD_CFG_PATH, myProguardConfigFileTextField.getText())) {
      return true;
    }

    if (myConfiguration.RUN_PROGUARD != myRunProguardCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.isIncludeSystemProguardCfgPath() != myIncludeSystemProguardFileCheckBox.isSelected()) {
      return true;
    }
    return false;
  }

  @NotNull
  private String getSelectedCustomKeystorePath() {
    final String path = myCustomDebugKeystoreField.getText().trim();
    return path.length() > 0 ? VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(path)) : "";
  }

  @NotNull
  private String getSelectedProguardConfigPath() {
    final String path = myProguardConfigFileTextField.getText().trim();
    return path.length() > 0 ? VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(path)) : "";
  }

  private boolean checkRelativePath(String relativePathFromConfig, String absPathFromTextField) {
    String pathFromConfig = relativePathFromConfig;
    if (pathFromConfig != null && pathFromConfig.length() > 0) {
      pathFromConfig = toAbsolutePath(pathFromConfig);
    }
    String pathFromTextField = absPathFromTextField.trim();
    return !FileUtil.pathsEqual(pathFromConfig, pathFromTextField);
  }

  @Nullable
  private String toRelativePath(String absPath) {
    absPath = FileUtil.toSystemIndependentName(absPath);
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(myContext.getModule());
    if (moduleDirPath != null) {
      moduleDirPath = FileUtil.toSystemIndependentName(moduleDirPath);
      return FileUtil.getRelativePath(moduleDirPath, absPath, '/');
    }
    return null;
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.project.modules.android.facet";
  }

  public void apply() throws ConfigurationException {
    if (!isModified()) return;
    String absGenPathR = myRGenPathField.getText().trim();
    String absGenPathAidl = myAidlGenPathField.getText().trim();

    boolean runApt = false;
    boolean runIdl = false;

    if (absGenPathR == null || absGenPathR.length() == 0 || absGenPathAidl == null || absGenPathAidl.length() == 0) {
      throw new ConfigurationException("Please specify source root for autogenerated files");
    }
    else {
      String relativeGenPathR = getAndCheckRelativePath(absGenPathR, false);
      String newAptDestDir = '/' + relativeGenPathR;
      if (!newAptDestDir.equals(myConfiguration.GEN_FOLDER_RELATIVE_PATH_APT)) {
        runApt = true;
      }
      myConfiguration.GEN_FOLDER_RELATIVE_PATH_APT = newAptDestDir;

      String relativeGenPathAidl = getAndCheckRelativePath(absGenPathAidl, false);
      String newIdlDestDir = '/' + relativeGenPathAidl;
      if (!newIdlDestDir.equals(myConfiguration.GEN_FOLDER_RELATIVE_PATH_AIDL)) {
        runIdl = true;
      }
      myConfiguration.GEN_FOLDER_RELATIVE_PATH_AIDL = newIdlDestDir;
    }

    String absManifestPath = myManifestFileField.getText().trim();
    if (absManifestPath.length() == 0) {
      throw new ConfigurationException("Manifest file not specified");
    }
    String manifestRelPath = getAndCheckRelativePath(absManifestPath, true);
    if (!SdkConstants.FN_ANDROID_MANIFEST_XML.equals(AndroidUtils.getSimpleNameByRelativePath(manifestRelPath))) {
      throw new ConfigurationException("Manifest file must have name AndroidManifest.xml");
    }
    myConfiguration.MANIFEST_FILE_RELATIVE_PATH = '/' + manifestRelPath;

    String absResPath = myResFolderField.getText().trim();
    if (absResPath.length() == 0) {
      throw new ConfigurationException("Resources folder not specified");
    }
    myConfiguration.RES_FOLDER_RELATIVE_PATH = '/' + getAndCheckRelativePath(absResPath, false);

    String absAssetsPath = myAssetsFolderField.getText().trim();
    myConfiguration.ASSETS_FOLDER_RELATIVE_PATH = absAssetsPath.length() > 0 ? '/' + getAndCheckRelativePath(absAssetsPath, false) : "";

    String absApkPath = (String)myApkPathCombo.getComboBox().getEditor().getItem();
    if (absApkPath.length() == 0) {
      myConfiguration.APK_PATH = "";
    }
    else {
      myConfiguration.APK_PATH = '/' + getAndCheckRelativePath(absApkPath, false);
    }

    String absLibsPath = myNativeLibsFolder.getText().trim();
    myConfiguration.LIBS_FOLDER_RELATIVE_PATH = absLibsPath.length() > 0 ? '/' + getAndCheckRelativePath(absLibsPath, false) : "";

    myConfiguration.CUSTOM_DEBUG_KEYSTORE_PATH = getSelectedCustomKeystorePath();

    myConfiguration.LIBRARY_PROJECT = myIsLibraryProjectCheckbox.isSelected();

    myConfiguration.RUN_PROCESS_RESOURCES_MAVEN_TASK = myRunProcessResourcesRadio.isSelected();

    myConfiguration.GENERATE_UNSIGNED_APK = myGenerateUnsignedApk.isSelected();
    
    myConfiguration.PACK_TEST_CODE = myIncludeTestCodeAndCheckBox.isSelected();

    myConfiguration.setIncludeAssetsFromLibraries(myIncludeAssetsFromLibraries.isSelected());

    String absProguardPath = myProguardConfigFileTextField.getText().trim();
    if (absProguardPath.length() == 0) {
      if (myRunProguardCheckBox.isSelected()) {
        throw new ConfigurationException("Proguard config file path not specified");
      }
      else {
        myConfiguration.PROGUARD_CFG_PATH = "";
      }
    }
    else {
      myConfiguration.PROGUARD_CFG_PATH = '/' + getAndCheckRelativePath(absProguardPath, false);
    }

    myConfiguration.RUN_PROGUARD = myRunProguardCheckBox.isSelected();
    myConfiguration.setIncludeSystemProguardCfgPath(myIncludeSystemProguardFileCheckBox.isSelected());

    boolean useCustomAptSrc = myUseCustomSourceDirectoryRadio.isSelected();

    myConfiguration.USE_CUSTOM_APK_RESOURCE_FOLDER = useCustomAptSrc;

    String absAptSourcePath = myCustomAptSourceDirField.getText().trim();
    if (useCustomAptSrc) {
      if (absAptSourcePath.length() == 0) {
        throw new ConfigurationException("Resources folder not specified");
      }
      myConfiguration.CUSTOM_APK_RESOURCE_FOLDER = '/' + getAndCheckRelativePath(absAptSourcePath, false);
    }
    else {
      String relPath = toRelativePath(absAptSourcePath);
      myConfiguration.CUSTOM_APK_RESOURCE_FOLDER = relPath != null ? '/' + relPath : "";
    }

    runApt = runApt && AndroidAptCompiler.isToCompileModule(myContext.getModule(), myConfiguration);

    if (runApt || runIdl) {
      final Module module = myContext.getModule();

      if (runApt) {
        AndroidCompileUtil.generate(module, AndroidAutogeneratorMode.AAPT, true);
      }
      if (runIdl) {
        AndroidCompileUtil.generate(module, AndroidAutogeneratorMode.AIDL);
      }
    }
  }

  private String getAndCheckRelativePath(String absPath, boolean checkExists) throws ConfigurationException {
    if (absPath.indexOf('/') < 0 && absPath.indexOf(File.separatorChar) < 0) {
      throw new ConfigurationException(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(absPath)));
    }
    String relativeGenPathR = toRelativePath(absPath);
    if (relativeGenPathR == null || relativeGenPathR.length() == 0) {
      throw new ConfigurationException(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(absPath)));
    }
    if (checkExists && LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(absPath)) == null) {
      throw new ConfigurationException(AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(absPath)));
    }
    return relativeGenPathR;
  }

  public void reset() {
    resetOptions(myConfiguration);
    myIsLibraryProjectCheckbox.setSelected(myConfiguration.LIBRARY_PROJECT);
  }

  private void resetOptions(AndroidFacetConfiguration configuration) {
    String aptGenPath = configuration.GEN_FOLDER_RELATIVE_PATH_APT;
    String aptAbspath = aptGenPath.length() > 0 ? toAbsolutePath(aptGenPath) : "";
    myRGenPathField.setText(aptAbspath != null ? aptAbspath : "");

    String aidlGenPath = configuration.GEN_FOLDER_RELATIVE_PATH_AIDL;
    String aidlAbsPath = aidlGenPath.length() > 0 ? toAbsolutePath(aidlGenPath) : "";
    myAidlGenPathField.setText(aidlAbsPath != null ? aidlAbsPath : "");

    String manifestPath = configuration.MANIFEST_FILE_RELATIVE_PATH;
    String manifestAbsPath = manifestPath.length() > 0 ? toAbsolutePath(manifestPath) : "";
    myManifestFileField.setText(manifestAbsPath != null ? manifestAbsPath : "");

    String resPath = configuration.RES_FOLDER_RELATIVE_PATH;
    String resAbsPath = resPath.length() > 0 ? toAbsolutePath(resPath) : "";
    myResFolderField.setText(resAbsPath != null ? resAbsPath : "");

    String assetsPath = configuration.ASSETS_FOLDER_RELATIVE_PATH;
    String assetsAbsPath = assetsPath.length() > 0 ? toAbsolutePath(assetsPath) : "";
    myAssetsFolderField.setText(assetsAbsPath != null ? assetsAbsPath : "");

    String libsPath = configuration.LIBS_FOLDER_RELATIVE_PATH;
    String libsAbsPath = libsPath.length() > 0 ? toAbsolutePath(libsPath) : "";
    myNativeLibsFolder.setText(libsAbsPath != null ? libsAbsPath : "");

    myCustomDebugKeystoreField.setText(FileUtil.toSystemDependentName(VfsUtil.urlToPath(configuration.CUSTOM_DEBUG_KEYSTORE_PATH)));

    String proguardCfgRelPath = configuration.PROGUARD_CFG_PATH;
    String proguardCfgAbsPath = proguardCfgRelPath.length() > 0 ? toAbsolutePath(proguardCfgRelPath) : "";
    myProguardConfigFileTextField.setText(proguardCfgAbsPath != null ? proguardCfgAbsPath : "");

    final boolean runProguard = configuration.RUN_PROGUARD;
    myRunProguardCheckBox.setSelected(runProguard);
    myProguardConfigFileLabel.setEnabled(runProguard);
    myProguardConfigFileTextField.setEnabled(runProguard);
    myIncludeSystemProguardFileCheckBox.setEnabled(runProguard);

    myIncludeSystemProguardFileCheckBox.setSelected(configuration.isIncludeSystemProguardCfgPath());
    final AndroidPlatform platform = configuration.getAndroidPlatform();
    final int sdkToolsRevision = platform != null ? platform.getSdkData().getSdkToolsRevision() : -1;
    myIncludeSystemProguardFileCheckBox.setVisible(AndroidCommonUtils.isIncludingInProguardSupported(sdkToolsRevision));

    myUseCustomSourceDirectoryRadio.setSelected(configuration.USE_CUSTOM_APK_RESOURCE_FOLDER);
    myUseAptResDirectoryFromPathRadio.setSelected(!configuration.USE_CUSTOM_APK_RESOURCE_FOLDER);

    String aptSourcePath = configuration.CUSTOM_APK_RESOURCE_FOLDER;
    String aptSourceAbsPath = aptSourcePath.length() > 0 ? toAbsolutePath(aptSourcePath) : "";
    myCustomAptSourceDirField.setText(aptSourceAbsPath != null ? aptSourceAbsPath : "");
    myCustomAptSourceDirField.setEnabled(configuration.USE_CUSTOM_APK_RESOURCE_FOLDER);

    String apkPath = configuration.APK_PATH;
    String apkAbsPath = apkPath.length() > 0 ? toAbsolutePath(apkPath) : "";
    myApkPathCombo.getComboBox().getEditor().setItem(apkAbsPath != null ? apkAbsPath : "");

    boolean mavenizedModule = AndroidMavenUtil.isMavenizedModule(myContext.getModule());
    myRunProcessResourcesRadio.setVisible(mavenizedModule);
    myRunProcessResourcesRadio.setSelected(myConfiguration.RUN_PROCESS_RESOURCES_MAVEN_TASK);
    myCompileResourcesByIdeRadio.setVisible(mavenizedModule);
    myCompileResourcesByIdeRadio.setSelected(!myConfiguration.RUN_PROCESS_RESOURCES_MAVEN_TASK);

    myGenerateUnsignedApk.setSelected(myConfiguration.GENERATE_UNSIGNED_APK);
    myIncludeTestCodeAndCheckBox.setSelected(myConfiguration.PACK_TEST_CODE);
    myIncludeAssetsFromLibraries.setSelected(myConfiguration.isIncludeAssetsFromLibraries());

    updateAptPanel();

    final boolean lib = myConfiguration.LIBRARY_PROJECT;
    myIncludeAssetsFromLibraries.setEnabled(!lib);
  }

  @Nullable
  private String toAbsolutePath(String genRelativePath) {
    if (genRelativePath == null) {
      return null;
    }
    if (genRelativePath.length() == 0) {
      return "";
    }
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(myContext.getModule());
    if (moduleDirPath == null) return null;
    final String path = PathUtil.getCanonicalPath(new File(moduleDirPath, genRelativePath).getPath());
    return path != null ? PathUtil.getLocalPath(path) : null;
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
    // TODO: place custom component creation code here
  }

  private class MyGenSourceFieldListener implements ActionListener {
    private final TextFieldWithBrowseButton myTextField;
    private final String myDefaultPath;

    private MyGenSourceFieldListener(TextFieldWithBrowseButton textField, String defaultPath) {
      myTextField = textField;
      myDefaultPath = defaultPath;
    }

    public void actionPerformed(ActionEvent e) {
      VirtualFile initialFile = null;
      String path = myTextField.getText().trim();
      if (path.length() == 0) {
        path = myDefaultPath;
      }
      if (path != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(path);
      }
      if (initialFile == null) {
        Module module = myContext.getModule();
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        VirtualFile[] sourceRoots = manager.getSourceRoots();
        if (sourceRoots.length > 0) {
          initialFile = sourceRoots[0];
        }
        else {
          initialFile = module.getModuleFile();
          if (initialFile == null) {
            String p = AndroidRootUtil.getModuleDirPath(myContext.getModule());
            if (p != null) {
              initialFile = LocalFileSystem.getInstance().findFileByPath(p);
            }
          }
        }
      }
      final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      VirtualFile file = FileChooser.chooseFile(descriptor, myContentPanel, myContext.getProject(), initialFile);
      if (file != null) {
        myTextField.setText(FileUtil.toSystemDependentName(file.getPath()));
      }
    }
  }

  private class MyFolderFieldListener implements ActionListener {
    private final TextFieldWithBrowseButton myTextField;
    private final VirtualFile myDefaultDir;
    private final boolean myChooseFile;
    private final Condition<VirtualFile> myFilter;

    public MyFolderFieldListener(TextFieldWithBrowseButton textField,
                                 VirtualFile defaultDir,
                                 boolean chooseFile,
                                 @Nullable Condition<VirtualFile> filter) {
      myTextField = textField;
      myDefaultDir = defaultDir;
      myChooseFile = chooseFile;
      myFilter = filter;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      VirtualFile initialFile = null;
      String path = myTextField.getText().trim();
      if (path.length() == 0) {
        VirtualFile dir = myDefaultDir;
        path = dir != null ? dir.getPath() : null;
      }
      if (path != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(path);
      }
      VirtualFile[] files = chooserDirsUnderModule(initialFile, myChooseFile, false, myFilter);
      if (files.length > 0) {
        assert files.length == 1;
        myTextField.setText(FileUtil.toSystemDependentName(files[0].getPath()));
      }
    }
  }

  private VirtualFile[] chooserDirsUnderModule(@Nullable VirtualFile initialFile,
                                               final boolean chooseFile,
                                               boolean chooseMultiple,
                                               @Nullable final Condition<VirtualFile> filter) {
    if (initialFile == null) {
      initialFile = myContext.getModule().getModuleFile();
    }
    if (initialFile == null) {
      String p = AndroidRootUtil.getModuleDirPath(myContext.getModule());
      if (p != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(p);
      }
    }
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(chooseFile, !chooseFile, false, false, false, chooseMultiple) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }

        if (!file.isDirectory() && !chooseFile) {
          return false;
        }

        return filter == null || filter.value(file);
      }
    };
    return FileChooser.chooseFiles(descriptor, myContentPanel, myContext.getProject(), initialFile);
  }
  
  private static class MyManifestFilter implements Condition<VirtualFile> {

    @Override
    public boolean value(VirtualFile file) {
      return file.isDirectory() || file.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML);
    }
  }
}
