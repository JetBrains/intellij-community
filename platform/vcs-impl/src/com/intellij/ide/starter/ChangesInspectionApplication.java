// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starter;

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionToolCmdlineOptionHelpProvider;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.conversion.ConversionListener;
import com.intellij.conversion.ConversionService;
import com.intellij.ide.impl.PatchProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.checkin.CodeAnalysisBeforeCheckinShowOnlyNew;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.Invoker;
import com.sun.awt.AWTUtilities;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author max
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ChangesInspectionApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ChangesInspectionApplication");

  public InspectionToolCmdlineOptionHelpProvider myHelpProvider;
  public String myProjectPath;
  private Project myProject;
  private int myVerboseLevel = 3;

  public boolean myErrorCodeRequired = false;

  @NonNls public static final String PROFILE = "profile";

  public void startup() {
    if (myProjectPath == null) {
      logError("Project to inspect is not defined");
      printHelp();
    }

    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    long openingProjectTime = System.currentTimeMillis();
    application.runReadAction(() -> {
      try {
        final ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
        logMessage(1, InspectionsBundle.message("inspection.application.starting.up",
                                                appInfo.getFullApplicationName() + " (build " + appInfo.getBuild().asString() + ")"));
        logMessageLn(1, InspectionsBundle.message("inspection.done"));
        openProject();
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        if (myErrorCodeRequired) application.exit(true, true);
      }
    });
    System.out.println(System.currentTimeMillis() - openingProjectTime);

    Ref<List<CodeSmellInfo>> codeSmells = Ref.create();

    application.executeOnPooledThread(() -> {
      runAnalysis(codeSmells);
      ReadAction.run(() -> printInfo(codeSmells.get()));
      application.invokeLater(() -> closeProject());
      System.exit(0);
    });
  }

  private void runAnalysis(Ref<List<CodeSmellInfo>> codeSmells) {
    ProgressManager.getInstance().run(new Task.Modal(myProject, VcsBundle.message("checking.code.smells.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          assert myProject != null;
          indicator.setIndeterminate(true);
          codeSmells.set(CodeAnalysisBeforeCheckinShowOnlyNew
                           .runAnalysis(myProject, ChangeListManager.getInstance(myProject).getAffectedFiles(), indicator));
        }
        catch (ProcessCanceledException e) {
          LOG.info("Code analysis canceled", e);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  private void openProject() {
    try {
      myProjectPath = myProjectPath.replace(File.separatorChar, '/');
      VirtualFile vfsProject = LocalFileSystem.getInstance().findFileByPath(myProjectPath);
      if (vfsProject == null) {
        logError(InspectionsBundle.message("inspection.application.file.cannot.be.found", myProjectPath));
        printHelp();
      }

      logMessage(1, InspectionsBundle.message("inspection.application.opening.project"));
      final ConversionService conversionService = ConversionService.getInstance();
      if (conversionService.convertSilently(myProjectPath, createConversionListener()).openingIsCanceled()) {
        gracefulExit();
        return;
      }
      myProject = ProjectUtil.openOrImport(myProjectPath, null, false);

      if (myProject == null) {
        logError("Unable to open project");
        gracefulExit();
        return;
      }

      ApplicationManager.getApplication().runWriteAction(() -> VirtualFileManager.getInstance().refreshWithoutFileWatcher(false));

      PatchProjectUtil.patchProject(myProject);

      logMessageLn(1, InspectionsBundle.message("inspection.done"));
      logMessage(1, InspectionsBundle.message("inspection.application.initializing.project"));

      InspectionProfileImpl inspectionProfile = loadInspectionProfile();
      if (inspectionProfile == null) return;

      final InspectionManagerEx im = (InspectionManagerEx)InspectionManager.getInstance(myProject);

      im.createNewGlobalContext(true).setExternalProfile(inspectionProfile);
      im.setProfile(inspectionProfile.getName());
    }
    catch (Throwable e) {
      LOG.error(e);
      logError(e.getMessage());
      gracefulExit();
    }
  }

  private void printHelp() {
    assert myHelpProvider != null;

    myHelpProvider.printHelpAndExit();
  }

  private void gracefulExit() {
    if (myErrorCodeRequired) {
      System.exit(1);
    }
    else {
      closeProject();
      throw new RuntimeException("Failed to proceed");
    }
  }

  private void closeProject() {
    if (myProject != null && !myProject.isDisposed()) {
      ProjectUtil.closeAndDispose(myProject);
      myProject = null;
    }
  }

  @Nullable
  private InspectionProfileImpl loadInspectionProfile() {
    return InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
  }

  private ConversionListener createConversionListener() {
    return new ConversionListener() {
      @Override
      public void conversionNeeded() {
        logMessageLn(1, InspectionsBundle.message("inspection.application.project.has.older.format.and.will.be.converted"));
      }

      @Override
      public void successfullyConverted(@NotNull final File backupDir) {
        logMessageLn(1, InspectionsBundle.message(
          "inspection.application.project.was.succesfully.converted.old.project.files.were.saved.to.0",
                                                  backupDir.getAbsolutePath()));
      }

      @Override
      public void error(@NotNull final String message) {
        logError(InspectionsBundle.message("inspection.application.cannot.convert.project.0", message));
      }

      @Override
      public void cannotWriteToFiles(@NotNull final List<? extends File> readonlyFiles) {
        StringBuilder files = new StringBuilder();
        for (File file : readonlyFiles) {
          files.append(file.getAbsolutePath()).append("; ");
        }
        logError(InspectionsBundle.message("inspection.application.cannot.convert.the.project.the.following.files.are.read.only.0", files.toString()));
      }
    };
  }

  @Nullable
  private static String getPrefix(final String text) {
    //noinspection HardCodedStringLiteral
    int idx = text.indexOf(" in ");
    if (idx == -1) {
      //noinspection HardCodedStringLiteral
      idx = text.indexOf(" of ");
    }

    return idx == -1 ? null : text.substring(0, idx);
  }

  public void setVerboseLevel(int verboseLevel) {
    myVerboseLevel = verboseLevel;
  }

  private void logMessage(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.print(message);
    }
  }

  private static void logError(String message) {
    System.err.println(message);
  }

  private void logMessageLn(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }

  private void printInfo(List<CodeSmellInfo> infos) {
    for (CodeSmellInfo info : infos) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(info.getDocument());
      if (file == null) {
        continue;
      }
      logMessageLn(0, String
        .format("[%s]%s:%d,%d %s", info.getSeverity(), file.getVirtualFile().getPath(), info.getStartLine(), info.getStartColumn(),
                info.getDescription()));
    }
  }


  private class MyProgressIndicatorBase extends ProgressIndicatorBase implements ProgressIndicatorEx {
    private String lastPrefix = "";
    private int myLastPercent = -1;

    @Override
    public void setText(String text) {
      if (myVerboseLevel == 0) return;

      if (myVerboseLevel == 1) {
        String prefix = getPrefix(text);
        if (prefix == null) return;
        if (prefix.equals(lastPrefix)) {
          logMessage(1, ".");
          return;
        }
        lastPrefix = prefix;
        logMessageLn(1, "");
        logMessageLn(1, prefix);
        return;
      }

      if (myVerboseLevel == 3) {
        if (!isIndeterminate() && getFraction() > 0) {
          final int percent = (int)(getFraction() * 100);
          if (myLastPercent == percent) return;
          String prefix = getPrefix(text);
          myLastPercent = percent;
          String msg = (prefix != null ? prefix : InspectionsBundle.message("inspection.display.name")) + " " + percent + "%";
          logMessageLn(2, msg);
        }
        return;
      }

      logMessageLn(2, text);
    }
  }
}
