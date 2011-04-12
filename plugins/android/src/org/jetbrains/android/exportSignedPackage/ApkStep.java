/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.android.jarutils.SignedJarBuilder;
import com.android.sdklib.SdkConstants;
import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidPackagingCompiler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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

  private TextFieldWithBrowseButton myApkPathField;
  private JPanel myContentPanel;
  private JLabel myApkPathLabel;

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
    myApkPathField.getButton().addActionListener(
      new SaveFileListener(myContentPanel, myApkPathField, AndroidBundle.message("android.extract.package.choose.dest.apk")) {
        @Override
        protected String getDefaultLocation() {
          Module module = myWizard.getFacet().getModule();
          return getContentRootPath(module);
        }
      });
  }

  @Override
  public void _init() {
    if (myInited) return;
    Module module = myWizard.getFacet().getModule();

    PropertiesComponent properties = PropertiesComponent.getInstance(module.getProject());
    String lastModule = properties.getValue(ChooseModuleStep.MODULE_PROPERTY);
    String lastApkPath = properties.getValue(APK_PATH_PROPERTY);
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
    myInited = true;
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  private void createAndAlignApk(final String apkPath) {
    AndroidPlatform platform = myWizard.getFacet().getConfiguration().getAndroidPlatform();
    assert platform != null;
    String sdkPath = platform.getSdk().getLocation();
    String zipAlignPath = sdkPath + File.separatorChar + AndroidUtils.toolPath(SdkConstants.FN_ZIPALIGN);
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
    });
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void createApk(File destFile) throws IOException, GeneralSecurityException {
    FileOutputStream fos = new FileOutputStream(destFile);
    PrivateKey privateKey = myWizard.getPrivateKey();
    assert privateKey != null;
    X509Certificate certificate = myWizard.getCertificate();
    assert certificate != null;
    SignedJarBuilder builder = new SignedJarBuilder(fos, privateKey, certificate);
    Module module = myWizard.getFacet().getModule();
    //String srcApkPath = CompilerPaths.getModuleOutputPath(module, false) + '/' + module.getName() + ".apk";
    String srcApkPath = myWizard.getFacet().getApkPath() + AndroidPackagingCompiler.UNSIGNED_SUFFIX;
    FileInputStream fis = new FileInputStream(new File(FileUtil.toSystemDependentName(srcApkPath)));
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

  private void showErrorInDispatchThread(final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(myWizard.getProject(), "Error: " + message, CommonBundle.getErrorTitle());
      }
    });
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
  protected void commitForNext() throws CommitStepException {
    final String apkPath = myApkPathField.getText().trim();
    if (apkPath.length() == 0) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.specify.apk.path.error"));
    }

    AndroidFacet facet = myWizard.getFacet();
    PropertiesComponent properties = PropertiesComponent.getInstance(myWizard.getProject());
    properties.setValue(ChooseModuleStep.MODULE_PROPERTY, facet != null ? facet.getModule().getName() : "");
    properties.setValue(APK_PATH_PROPERTY, apkPath);

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
}
