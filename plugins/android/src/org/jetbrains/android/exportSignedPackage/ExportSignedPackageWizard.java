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

import com.android.SdkConstants;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidCommonBundle;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ExportSignedPackageWizard extends AbstractWizard<ExportSignedPackageWizardStep> {
  private final Project myProject;

  private AndroidFacet myFacet;
  private PrivateKey myPrivateKey;
  private X509Certificate myCertificate;

  private boolean mySigned;
  private CompileScope myCompileScope;
  private String myApkPath;

  public ExportSignedPackageWizard(Project project, List<AndroidFacet> facets, boolean signed) {
    super(AndroidBundle.message("android.export.package.wizard.title"), project);
    myProject = project;
    mySigned = signed;
    assert facets.size() > 0;
    if (facets.size() > 1 ||
        SystemInfo.isMac /* wizards with only step are shown incorrectly on mac */) {
      addStep(new ChooseModuleStep(this, facets));
    }
    else {
      myFacet = facets.get(0);
    }
    if (signed) {
      addStep(new KeystoreStep(this));
    }
    addStep(new ApkStep(this));
    init();
  }

  public boolean isSigned() {
    return mySigned;
  }

  @Override
  protected void doOKAction() {
    if (!commitCurrentStep()) return;
    super.doOKAction();

    CompilerManager.getInstance(myProject).make(myCompileScope, new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        if (aborted || errors != 0) {
          return;
        }

        final String title = AndroidBundle.message("android.extract.package.task.title");
        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, true, null) {
          public void run(@NotNull ProgressIndicator indicator) {
            createAndAlignApk(myApkPath);
          }
        });
      }
    });
  }

  @Override
  protected void doNextAction() {
    if (!commitCurrentStep()) return;
    super.doNextAction();
  }

  private boolean commitCurrentStep() {
    try {
      mySteps.get(myCurrentStep).commitForNext();
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  protected int getNextStep(int stepIndex) {
    int result = super.getNextStep(stepIndex);
    if (result != myCurrentStep) {
      mySteps.get(result).setPreviousStepIndex(myCurrentStep);
    }
    return result;
  }

  @Override
  protected int getPreviousStep(int stepIndex) {
    ExportSignedPackageWizardStep step = mySteps.get(stepIndex);
    int prevStepIndex = step.getPreviousStepIndex();
    assert prevStepIndex >= 0;
    return prevStepIndex;
  }

  @Override
  protected void updateStep() {
    final int step = getCurrentStep();
    final ExportSignedPackageWizardStep currentStep = mySteps.get(step);
    getFinishButton().setEnabled(currentStep.canFinish());

    super.updateStep();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        getRootPane().setDefaultButton(getNextStep(step) != step ? getNextButton() : getFinishButton());

        final JComponent component = currentStep.getPreferredFocusedComponent();
        if (component != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              component.requestFocus();
            }
          });
        }
      }
    });
  }

  @Override
  protected String getHelpID() {
    ExportSignedPackageWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  public void setPrivateKey(@NotNull PrivateKey privateKey) {
    myPrivateKey = privateKey;
  }

  public void setCertificate(@NotNull X509Certificate certificate) {
    myCertificate = certificate;
  }

  public PrivateKey getPrivateKey() {
    return myPrivateKey;
  }

  public X509Certificate getCertificate() {
    return myCertificate;
  }

  public void setCompileScope(@NotNull CompileScope compileScope) {
    myCompileScope = compileScope;
  }

  public void setApkPath(@NotNull String apkPath) {
    myApkPath = apkPath;
  }

  private void createAndAlignApk(final String apkPath) {
    AndroidPlatform platform = getFacet().getConfiguration().getAndroidPlatform();
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
      final String message = AndroidCommonUtils.executeZipAlign(zipAlignPath, destFile, realDestFile);
      if (message != null) {
        showErrorInDispatchThread(message);
        return;
      }
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        String title = AndroidBundle.message("android.export.package.wizard.title");
        final Project project = getProject();
        final File apkFile = new File(apkPath);

        final VirtualFile vApkFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(apkFile);
        if (vApkFile != null) {
          vApkFile.refresh(true, false);
        }

        if (!runZipAlign) {
          Messages.showWarningDialog(project, AndroidCommonBundle.message(
            "android.artifact.building.cannot.find.zip.align.error"), title);
        }

        if (ShowFilePathAction.isSupported()) {
          if (Messages.showOkCancelDialog(project, AndroidBundle.message("android.export.package.success.message", apkFile.getName()),
                                          title, RevealFileAction.getActionName(), IdeBundle.message("action.close"),
                                          Messages.getInformationIcon()) == Messages.OK) {
            ShowFilePathAction.openFile(apkFile);
          }
        }
        else {
          Messages.showInfoMessage(project, AndroidBundle.message("android.export.package.success.message", apkFile), title);
        }
      }
    }, ModalityState.NON_MODAL);
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void createApk(File destFile) throws IOException, GeneralSecurityException {
    final String srcApkPath = AndroidCompileUtil.getUnsignedApkPath(getFacet());
    final File srcApk = new File(FileUtil.toSystemDependentName(srcApkPath));

    if (isSigned()) {
      AndroidCommonUtils.signApk(srcApk, destFile, getPrivateKey(), getCertificate());
    }
    else {
      FileUtil.copy(srcApk, destFile);
    }
  }

  private void showErrorInDispatchThread(final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(getProject(), "Error: " + message, CommonBundle.getErrorTitle());
      }
    }, ModalityState.NON_MODAL);
  }
}
