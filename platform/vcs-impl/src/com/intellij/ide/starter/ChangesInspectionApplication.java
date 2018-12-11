// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CodeAnalysisBeforeCheckinShowOnlyNew;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author max
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
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
    Map<Integer, ArrayList<CommitInfoFormat>> map = parseJSON();

    long openingProjectTime = System.currentTimeMillis();
    initRepository(map);

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

    ArrayList<ArrayList<CommitInfoFormat>> allCommitGroups = new ArrayList<>(map.values());
    allCommitGroups.sort((x, y) -> {
      Optional<CommitInfoFormat> minx = x.stream().min(ChangesInspectionApplication::compareCommitInfoFormat);
      Optional<CommitInfoFormat> miny = y.stream().min(ChangesInspectionApplication::compareCommitInfoFormat);
      assert minx.isPresent() && miny.isPresent();
      return compareCommitInfoFormat(minx.get(), miny.get());
    });

    logMessageLn(0, "Ready");
    application.executeOnPooledThread(() -> {
      for (List<CommitInfoFormat> commitGroup : allCommitGroups) {
        Map<String, List<CommitInfoFormat>> commitGroupedByRepo = StreamEx.of(commitGroup).groupingBy(it -> it.repo_name);
        for (Map.Entry<String, List<CommitInfoFormat>> entry : commitGroupedByRepo.entrySet()) {
          Optional<CommitInfoFormat> firstCommit = entry.getValue().stream().min(ChangesInspectionApplication::compareCommitInfoFormat);
          Optional<CommitInfoFormat> lastCommit = entry.getValue().stream().max(ChangesInspectionApplication::compareCommitInfoFormat);
          assert firstCommit.isPresent() && lastCommit.isPresent();
          executeGitProcess("--hard", lastCommit.get().hash, lastCommit.get().repo_name);
          executeGitProcess("--soft", firstCommit.get().hash + "^", lastCommit.get().repo_name);
        }

        StringBuilder descriptionBuilder = new StringBuilder();
        for (CommitInfoFormat commit : commitGroup) {
          String message = commit.hash + " | " + commit.author + " | " + commit.time + " | " + commit.repo_name;
          descriptionBuilder.append(message).append("\n");
          logMessageLn(0, message);
        }

        long time = System.currentTimeMillis();
        VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getAllVersionedRoots();
        VfsUtil.markDirtyAndRefresh(false, true, true, roots);
        application.invokeAndWait(() -> application.runWriteAction(() -> PsiDocumentManager.getInstance(myProject).commitAllDocuments()));
        DumbService.getInstance(myProject).waitForSmartMode();
        for (VirtualFile root : roots) {
          VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(root);
        }
        FutureResult<Void> future = new FutureResult<>();
        ChangeListManager.getInstance(myProject).invokeAfterUpdate(() -> future.set(null), InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, "my title", ModalityState.NON_MODAL);

        try {
          future.get();
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
        catch (ExecutionException e) {
          e.printStackTrace();
        }

        logMessageLn(0, "running analysis");
        try {
          runAnalysis(codeSmells);
          ReadAction.run(() -> printInfo(codeSmells.get(), descriptionBuilder.toString(), commitGroup.get(0).grouping_id.toString()));
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        logMessageLn(0, "Analysis time: " + (System.currentTimeMillis() - time));

        for (Map.Entry<String, List<CommitInfoFormat>> entry : commitGroupedByRepo.entrySet()) {
          Optional<CommitInfoFormat> lastCommit = entry.getValue().stream().max(ChangesInspectionApplication::compareCommitInfoFormat);
          assert lastCommit.isPresent();
          executeGitProcess("--hard", lastCommit.get().hash, lastCommit.get().repo_name);
        }
      }
    });
  }

  private void initRepository(@NotNull Map<Integer, ArrayList<CommitInfoFormat>> map) {
    List<CommitInfoFormat> allCommits = map.values().stream().flatMap(List::stream).collect(Collectors.toList());
    Map<String, List<CommitInfoFormat>> groupingByRepo = StreamEx.of(allCommits).groupingBy(it -> it.repo_name);
    List<Optional<CommitInfoFormat>> firstCommits =
      ContainerUtil.map(groupingByRepo.values(), repo -> repo.stream().min(ChangesInspectionApplication::compareCommitInfoFormat));

    for (Optional<CommitInfoFormat> firstCommit : firstCommits) {
      assert firstCommit.isPresent();
      CommitInfoFormat format = ObjectUtils.notNull(firstCommit.get());
      executeGitProcess("--hard", format.hash + "^", format.repo_name);
    }
  }

  private static int compareCommitInfoFormat(@NotNull CommitInfoFormat x, @NotNull CommitInfoFormat y) {
    DateFormat instance = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    try {
      Date xDate = instance.parse(x.time);
      Date yDate = instance.parse(y.time);
      return xDate.compareTo(yDate);
    }
    catch (ParseException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  private synchronized void runAnalysis(Ref<List<CodeSmellInfo>> codeSmells) {
    List<VirtualFile> files = ChangeListManager.getInstance(myProject).getAffectedFiles();
    for (VirtualFile file : files) {
      logMessageLn(0, "modified file" + file.getPath());
    }
    ProgressManager.getInstance().run(new Task.Modal(myProject, VcsBundle.message("checking.code.smells.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          assert myProject != null;
          indicator.setIndeterminate(true);
          codeSmells.set(CodeAnalysisBeforeCheckinShowOnlyNew.runAnalysis(myProject, files, indicator));
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

  private void printInfo(List<CodeSmellInfo> infos,
                         String description,
                         String id) {
    if (infos.isEmpty()) {
      logMessageLn(0, "Nothing found");
      return;
    }
    StringBuilder builder = new StringBuilder();
    builder.append(description).append("\n");
    for (CodeSmellInfo info : infos) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(info.getDocument());
      if (file == null) {
        continue;
      }
      String format = String
        .format("[%s]%s:%d,%d %s", info.getSeverity(), file.getVirtualFile().getPath(), info.getStartLine(), info.getStartColumn(),
                info.getDescription());
      builder.append(format).append("\n");
      logMessageLn(0, format);
    }

    try {
      FileUtil.writeToFile(Paths.get(myProjectPath, "reports", id + ".txt").toFile(), builder.toString());
    }
    catch (IOException e) {
      e.printStackTrace();
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

  public Map<Integer, ArrayList<CommitInfoFormat>>  parseJSON() {
    Type token = new TypeToken<Map<Integer, ArrayList<CommitInfoFormat>>>() {}.getType();
    try {
      return new Gson().fromJson(FileUtil.loadFile(Paths.get(myProjectPath, "/changes.json").toFile()), token);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void executeGitProcess(@NotNull String kind, @NotNull String revision, @NotNull String directory) {
    Process p = null;
    try {
       p = new ProcessBuilder().command("git", "reset", kind, revision).directory(Paths.get(myProjectPath, directory).toFile()).start();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    if (p != null) {
      try {
        p.waitFor();
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private static class CommitInfoFormat {
    public String hash;
    public String time;
    public String author;
    public String repo_name;
    public Integer grouping_id;
  }

}
