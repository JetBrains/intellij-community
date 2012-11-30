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

package org.jetbrains.android.exportSignedPackage;

import com.android.SdkConstants;
import com.android.jarutils.SignedJarBuilder;
import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidPackagingCompiler;
import org.jetbrains.android.compiler.AndroidProguardCompiler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.SafeSignedJarBuilder;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author Eugene.Kudelevsky
 */
class ApkStep extends ExportSignedPackageWizardStep {
  public static final String APK_PATH_PROPERTY = "ExportedApkPath";
  public static final String APK_PATH_PROPERTY_UNSIGNED = "ExportedUnsignedApkPath";
  public static final String RUN_PROGUARD_PROPERTY = "AndroidRunProguardForReleaseBuild";
  public static final String PROGUARD_CFG_PATH_PROPERTY = "AndroidProguardConfigPath";
  public static final String INCLUDE_SYSTEM_PROGUARD_FILE_PROPERTY = "AndroidIncludeSystemProguardFile";

  private TextFieldWithBrowseButton myApkPathField;
  private JPanel myContentPanel;
  private JLabel myApkPathLabel;
  private JCheckBox myProguardCheckBox;
  private JBLabel myProguardConfigFilePathLabel;
  private TextFieldWithBrowseButton myProguardConfigFilePathField;
  private JCheckBox myIncludeSystemProguardFileCheckBox;

  private final ExportSignedPackageWizard myWizard;
  private boolean myInited;

  @Nullable
  private static String getContentRootPath(Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length != 0) {
      VirtualFile contentRoot = contentRoots[0];
      if (contentRoot != null) return contentRoot.getPath();
    }
    return null;
  }

  public ApkStep(ExportSignedPackageWizard wizard) {
    myWizard = wizard;
    myApkPathLabel.setLabelFor(myApkPathField);
    myProguardConfigFilePathLabel.setLabelFor(myProguardConfigFilePathField);
    
    myApkPathField.getButton().addActionListener(
      new SaveFileListener(myContentPanel, myApkPathField, AndroidBundle.message("android.extract.package.choose.dest.apk")) {
        @Override
        protected String getDefaultLocation() {
          Module module = myWizard.getFacet().getModule();
          return getContentRootPath(module);
        }
      });

    myProguardConfigFilePathField.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String path = myProguardConfigFilePathField.getText().trim();
        VirtualFile defaultFile = path != null && path.length() > 0
                                  ? LocalFileSystem.getInstance().findFileByPath(path)
                                  : null;
        final AndroidFacet facet = myWizard.getFacet();

        if (defaultFile == null && facet != null) {
          defaultFile = AndroidRootUtil.getMainContentRoot(facet);
        }
        final VirtualFile file = FileChooser.chooseFile(myContentPanel, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                                        defaultFile);
        if (file != null) {
          myProguardConfigFilePathField.setText(FileUtil.toSystemDependentName(file.getPath()));
        }
      }
    });
    
    myProguardCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean enabled = myProguardCheckBox.isSelected();
        myProguardConfigFilePathLabel.setEnabled(enabled);
        myProguardConfigFilePathField.setEnabled(enabled);
        myIncludeSystemProguardFileCheckBox.setEnabled(enabled);
      }
    });

    myContentPanel.setPreferredSize(new Dimension(myContentPanel.getPreferredSize().width, 250));
  }

  @Override
  public void _init() {
    if (myInited) return;
    final AndroidFacet facet = myWizard.getFacet();
    Module module = facet.getModule();

    PropertiesComponent properties = PropertiesComponent.getInstance(module.getProject());
    String lastModule = properties.getValue(ChooseModuleStep.MODULE_PROPERTY);
    String lastApkPath = properties.getValue(getApkPathPropertyName());
    if (lastApkPath != null && module.getName().equals(lastModule)) {
      myApkPathField.setText(FileUtil.toSystemDependentName(lastApkPath));
    }
    else {
      String contentRootPath = getContentRootPath(module);
      if (contentRootPath != null) {
        String defaultPath = FileUtil.toSystemDependentName(contentRootPath + "/" + module.getName() + ".apk");
        myApkPathField.setText(defaultPath);
      }
    }

    final String runProguardPropValue = properties.getValue(RUN_PROGUARD_PROPERTY);
    boolean selected;

    if (runProguardPropValue != null) {
      selected = Boolean.parseBoolean(runProguardPropValue);
    }
    else {
      selected = facet.getConfiguration().RUN_PROGUARD;
    }
    myProguardCheckBox.setSelected(selected);
    myProguardConfigFilePathLabel.setEnabled(selected);
    myProguardConfigFilePathField.setEnabled(selected);
    myIncludeSystemProguardFileCheckBox.setEnabled(selected);

    final AndroidPlatform platform = AndroidPlatform.getInstance(module);
    final int sdkToolsRevision = platform != null ? platform.getSdkData().getSdkToolsRevision() : -1;
    myIncludeSystemProguardFileCheckBox.setVisible(AndroidCommonUtils.isIncludingInProguardSupported(sdkToolsRevision));

    final String proguardCfgPath = properties.getValue(PROGUARD_CFG_PATH_PROPERTY);
    if (proguardCfgPath != null && 
        LocalFileSystem.getInstance().refreshAndFindFileByPath(proguardCfgPath) != null) {
      myProguardConfigFilePathField.setText(FileUtil.toSystemDependentName(proguardCfgPath));
      final String includeSystemProguardFile = properties.getValue(INCLUDE_SYSTEM_PROGUARD_FILE_PROPERTY);
      myIncludeSystemProguardFileCheckBox.setSelected(Boolean.parseBoolean(includeSystemProguardFile));
    }
    else {
      final AndroidFacetConfiguration configuration = facet.getConfiguration();
      if (configuration.RUN_PROGUARD) {
        final VirtualFile proguardCfgFile = AndroidRootUtil.getProguardCfgFile(facet);
        if (proguardCfgFile != null) {
          myProguardConfigFilePathField.setText(FileUtil.toSystemDependentName(proguardCfgFile.getPath()));
        }
        myIncludeSystemProguardFileCheckBox.setSelected(facet.getConfiguration().isIncludeSystemProguardCfgPath());
      }
      else {
        final Pair<VirtualFile, Boolean> pair = AndroidCompileUtil.getDefaultProguardConfigFile(facet);
        if (pair != null) {
          myProguardConfigFilePathField.setText(FileUtil.toSystemDependentName(pair.getFirst().getPath()));
          myIncludeSystemProguardFileCheckBox.setSelected(pair.getSecond());
        }
        else {
          myIncludeSystemProguardFileCheckBox.setSelected(true);
        }
      }
    }

    myInited = true;
  }

  private String getApkPathPropertyName() {
    return myWizard.isSigned() ? APK_PATH_PROPERTY : APK_PATH_PROPERTY_UNSIGNED;
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  private void createAndAlignApk(final String apkPath) {
    AndroidPlatform platform = myWizard.getFacet().getConfiguration().getAndroidPlatform();
    assert platform != null;
    String sdkPath = platform.getSdkData().getLocation();
    String zipAlignPath = sdkPath + File.separatorChar + AndroidCommonUtils.toolPath(SdkConstants.FN_ZIPALIGN);
    File zipalign = new File(zipAlignPath);
    final boolean runZipAlign = zipalign.isFile();
    File destFile = null;
    try {
      destFile = runZipAlign ? FileUtil.createTempFile("android", ".apk") : new File(apkPath);
      createApk(destFile);
    }
    catch (Exception e) {
      showErrorInDispatchThread(e.getMessage());
    }
    if (destFile == null) return;

    if (runZipAlign) {
      File realDestFile = new File(apkPath);
      final String message = executeZipAlign(zipAlignPath, destFile, realDestFile);
      if (message != null) {
        showErrorInDispatchThread(message);
        return;
      }
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        String title = AndroidBundle.message("android.export.package.wizard.title");
        if (!runZipAlign) {
          Messages.showWarningDialog(myWizard.getProject(), "The zipalign tool was not found in the SDK.\n\n" +
                                                            "Please update to the latest SDK and re-export your application\n" +
                                                            "or run zipalign manually.\n\n" +
                                                            "Aligning applications allows Android to use application resources\n" +
                                                            "more efficiently.", title);
        }
        Messages.showInfoMessage(myWizard.getProject(), AndroidBundle.message("android.export.package.success.message", apkPath), title);
      }
    }, ModalityState.NON_MODAL);
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void createApk(File destFile) throws IOException, GeneralSecurityException {
    final String srcApkPath = AndroidRootUtil.getApkPath(myWizard.getFacet()) + AndroidPackagingCompiler.UNSIGNED_SUFFIX;
    final File srcApk = new File(FileUtil.toSystemDependentName(srcApkPath));

    if (myWizard.isSigned()) {
      FileOutputStream fos = new FileOutputStream(destFile);
      PrivateKey privateKey = myWizard.getPrivateKey();
      assert privateKey != null;
      X509Certificate certificate = myWizard.getCertificate();
      assert certificate != null;
      SignedJarBuilder builder = new SafeSignedJarBuilder(fos, privateKey, certificate, destFile.getPath());
      FileInputStream fis = new FileInputStream(srcApk);
      try {
        builder.writeZip(fis, null);
        builder.close();
      }
      finally {
        try {
          fis.close();
        }
        catch (IOException ignored) {
        }
        finally {
          try {
            fos.close();
          }
          catch (IOException ignored) {
          }
        }
      }
    }
    else {
      FileUtil.copy(srcApk, destFile);
    }
  }

  private void showErrorInDispatchThread(final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(myWizard.getProject(), "Error: " + message, CommonBundle.getErrorTitle());
      }
    }, ModalityState.NON_MODAL);
  }

  @Nullable
  private static String executeZipAlign(String zipAlignPath, File source, File destination) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(zipAlignPath);
    commandLine.addParameters("-f", "4", source.getAbsolutePath(), destination.getAbsolutePath());
    OSProcessHandler handler;
    try {
      handler = new OSProcessHandler(commandLine.createProcess(), "");
    }
    catch (ExecutionException e) {
      return e.getMessage();
    }
    final StringBuilder builder = new StringBuilder();
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        builder.append(event.getText());
      }
    });
    handler.startNotify();
    handler.waitFor();
    int exitCode = handler.getProcess().exitValue();
    return exitCode != 0 ? builder.toString() : null;
  }

  @Override
  protected boolean canFinish() {
    return true;
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.specify.apk.location";
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    final String apkPath = myApkPathField.getText().trim();
    if (apkPath.length() == 0) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.specify.apk.path.error"));
    }

    AndroidFacet facet = myWizard.getFacet();
    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    properties.setValue(ChooseModuleStep.MODULE_PROPERTY, facet != null ? facet.getModule().getName() : "");
    properties.setValue(getApkPathPropertyName(), apkPath);

    File folder = new File(apkPath).getParentFile();
    if (folder == null) {
      throw new CommitStepException(AndroidBundle.message("android.cannot.create.file.error", apkPath));
    }
    try {
      if (!folder.exists()) {
        folder.mkdirs();
      }
    }
    catch (Exception e) {
      throw new CommitStepException(e.getMessage());
    }

    final CompilerManager manager = CompilerManager.getInstance(myWizard.getProject());
    final CompileScope compileScope = manager.createModuleCompileScope(facet.getModule(), true);
    AndroidCompileUtil.setReleaseBuild(compileScope);

    properties.setValue(RUN_PROGUARD_PROPERTY, Boolean.toString(myProguardCheckBox.isSelected()));

    if (myProguardCheckBox.isSelected()) {
      final String proguardCfgPath = myProguardConfigFilePathField.getText().trim();
      if (proguardCfgPath.length() == 0) {
        throw new CommitStepException(AndroidBundle.message("android.extract.package.specify.proguard.cfg.path.error"));
      }
      properties.setValue(PROGUARD_CFG_PATH_PROPERTY, proguardCfgPath);
      properties.setValue(INCLUDE_SYSTEM_PROGUARD_FILE_PROPERTY, Boolean.toString(myIncludeSystemProguardFileCheckBox.isSelected()));

      if (!new File(proguardCfgPath).isFile()) {
        throw new CommitStepException("Cannot find file " + proguardCfgPath);
      }

      compileScope.putUserData(AndroidProguardCompiler.PROGUARD_CFG_PATH_KEY, proguardCfgPath);
      compileScope.putUserData(AndroidProguardCompiler.INCLUDE_SYSTEM_PROGUARD_FILE, myIncludeSystemProguardFileCheckBox.isSelected());
    }

    manager.make(compileScope, new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        if (aborted || errors != 0) {
          return;
        }

        final String title = AndroidBundle.message("android.extract.package.task.title");
        ProgressManager.getInstance().run(new Task.Backgroundable(myWizard.getProject(), title, true, null) {
          public void run(@NotNull ProgressIndicator indicator) {
            createAndAlignApk(apkPath);
          }
        });
      }
    });
  }

  @Override
  protected void commitForNext() throws CommitStepException {
  }
}
