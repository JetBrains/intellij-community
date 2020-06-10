// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.idea.IdeaLogger;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.DocumentReferenceManagerImpl;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.TooManyProjectLeakedException;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.intellij.testFramework.RunAll.runAll;

/**
 * Base class for heavy tests.
 * <p/>
 * NOTE: Because of the performance difference, we recommend plugin developers to write light tests whenever possible.
 * <p/>
 * Please see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/testing_plugins.html">Testing Plugins</a> in IntelliJ Platform SDK DevGuide.
 *
 * @author yole
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public abstract class HeavyPlatformTestCase extends UsefulTestCase implements DataProvider {
  private static boolean ourReportedLeakedProjects;
  protected Project myProject;
  protected Module myModule;

  protected final Collection<File> myFilesToDelete = new HashSet<>();
  private final TempFiles myTempFiles = new TempFiles(myFilesToDelete);

  protected boolean myAssertionsInTestDetected;
  private static TestCase ourTestCase;
  private EditorListenerTracker myEditorListenerTracker;
  private ThreadTracker myThreadTracker;

  private static boolean ourPlatformPrefixInitialized;
  private static Set<VirtualFile> ourEternallyLivingFilesCache;
  private SdkLeakTracker myOldSdks;
  private VirtualFilePointerTracker myVirtualFilePointerTracker;
  private @Nullable CodeStyleSettingsTracker myCodeStyleSettingsTracker;

  public @NotNull TempFiles getTempDir() {
    return myTempFiles;
  }

  protected final @NotNull VirtualFile createTestProjectStructure() throws IOException {
    return PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
  }

  protected final @NotNull VirtualFile createTestProjectStructure(String rootPath) throws Exception {
    return PsiTestUtil.createTestProjectStructure(myProject, myModule, rootPath, myFilesToDelete);
  }

  /**
   * If a temp directory is reused from some previous test run, there might be cached children in its VFS.
   * Ensure they're removed
   */
  public static void synchronizeTempDirVfs(@NotNull VirtualFile tempDir) {
    tempDir.getChildren();
    tempDir.refresh(false, true);
  }

  public static VirtualFile synchronizeTempDirVfs(@NotNull Path tempDir) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(tempDir.toString()));
    // null is ok, because Path can be only generated, but not created
    if (virtualFile != null) {
      synchronizeTempDirVfs(virtualFile);
    }
    return virtualFile;
  }

  protected void initApplication() throws Exception {
    TestApplicationManager testAppManager = TestApplicationManager.getInstance();
    testAppManager.setDataProvider(this);
    // try to remember old sdks as soon as possible after the app instantiation
    myOldSdks = new SdkLeakTracker();
  }

  private static final String[] PREFIX_CANDIDATES = {
    "Rider", "GoLand", "CLion",
    null,
    "AppCode", "SwiftTests", "CidrCommonTests",
    "DataGrip",
    "Python", "PyCharmCore",
    "Ruby",
    "PhpStorm",
    "UltimateLangXml", "Idea", "PlatformLangXml"};

  public static void doAutodetectPlatformPrefix() {
    if (ourPlatformPrefixInitialized) {
      return;
    }

    if (System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) != null) {
      ourPlatformPrefixInitialized = true;
      return;
    }

    for (String candidate : PREFIX_CANDIDATES) {
      String markerPath = candidate == null ? "idea/ApplicationInfo.xml" : "META-INF/" + candidate + "Plugin.xml";
      URL resource = HeavyPlatformTestCase.class.getClassLoader().getResource(markerPath);
      if (resource != null) {
        if (candidate != null) {
          setPlatformPrefix(candidate);
        }
        break;
      }
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFilesToDelete.add(new File(FileUtilRt.getTempDirectory()));

    if (ourTestCase != null) {
      String message = "Previous test " + ourTestCase + " hasn't called tearDown(). Probably overridden without super call.";
      ourTestCase = null;
      fail(message);
    }
    IdeaLogger.ourErrorsOccurred = null;

    LOG.debug(getClass().getName() + ".setUp()");

    initApplication();
    if (myOldSdks == null) { // some bastard's overridden initApplication completely
      myOldSdks = new SdkLeakTracker();
    }

    setUpProject();
    myEditorListenerTracker = new EditorListenerTracker();
    myThreadTracker = new ThreadTracker();

    boolean isTrackCodeStyleChanges = !(isStressTest() ||
                                        ApplicationManager.getApplication() == null ||
                                        ApplicationManager.getApplication() instanceof MockApplication);

    myCodeStyleSettingsTracker = isTrackCodeStyleChanges ? new CodeStyleSettingsTracker(() -> CodeStyle.getDefaultSettings()) : null;
    ourTestCase = this;
    if (myProject != null) {
      CodeStyle.setTemporarySettings(myProject, CodeStyle.createTestSettings());
      InjectedLanguageManagerImpl.pushInjectors(myProject);
      ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject)).clearUncommittedDocuments();
    }

    UIUtil.dispatchAllInvocationEvents();
    myVirtualFilePointerTracker = new VirtualFilePointerTracker();
  }

  public final Project getProject() {
    return myProject;
  }

  public final @NotNull PsiManager getPsiManager() {
    return PsiManager.getInstance(myProject);
  }

  public Module getModule() {
    return myModule;
  }

  protected void setUpProject() throws Exception {
    myProject = doCreateProject(getProjectDirOrFile());
    PlatformTestUtil.openProject(myProject);
    LocalFileSystem.getInstance().refreshIoFiles(myFilesToDelete);

    WriteAction.run(() ->
      ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
        setUpModule();
        setUpJdk();
      })
    );

    LightPlatformTestCase.clearUncommittedDocuments(getProject());

    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
  }

  protected @NotNull Project doCreateProject(@NotNull Path projectFile) throws Exception {
    return createProject(projectFile);
  }

  public static @NotNull Project createProject(@NotNull Path file) {
    try {
      return Objects.requireNonNull(ProjectManagerEx.getInstanceEx().newProject(file, FixtureRuleKt.createTestOpenProjectOptions()));
    }
    catch (TooManyProjectLeakedException e) {
      if (ourReportedLeakedProjects) {
        fail("Too many projects leaked, again.");
        return null;
      }
      ourReportedLeakedProjects = true;

      reportLeakedProjects(e);
      return null;
    }
  }

  public static @NotNull String publishHeapDump(@NotNull String fileNamePrefix) {
    String fileName = fileNamePrefix + ".hprof.zip";
    File dumpFile = new File(System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir")), fileName);
    try {
      FileUtil.delete(dumpFile);
      MemoryDumpHelper.captureMemoryDumpZipped(dumpFile);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    String dumpPath = dumpFile.getAbsolutePath();
    System.out.println("##teamcity[publishArtifacts '" + dumpPath + "']");
    return dumpPath;
  }

  @Contract("_ -> fail")
  public static void reportLeakedProjects(@NotNull TooManyProjectLeakedException e) {
    IntSet hashCodes = new IntOpenHashSet();
    for (Project project : e.getLeakedProjects()) {
      hashCodes.add(System.identityHashCode(project));
    }

    String dumpPath = publishHeapDump("leakedProjects");

    StringBuilder leakers = new StringBuilder();
    leakers.append("Too many projects leaked: \n");
    LeakHunter
      .processLeaks(LeakHunter.allRoots(), ProjectImpl.class, p -> hashCodes.contains(System.identityHashCode(p)), (leaked, backLink) -> {
        int hashCode = System.identityHashCode(leaked);
        leakers.append("Leaked project found:").append(leaked).append("; hash: ").append(hashCode).append("; place: ")
          .append(ProjectRule.getCreationPlace(leaked)).append("\n");
        leakers.append(backLink).append("\n");
        leakers.append(";-----\n");

        hashCodes.remove(hashCode);
        return !hashCodes.isEmpty();
      });

    fail(leakers + "\nPlease see '" + dumpPath + "' for a memory dump");
  }

  protected @NotNull Path getProjectDirOrFile() {
    return getProjectDirOrFile(false);
  }

  protected boolean isCreateProjectFileExplicitly() {
    return true;
  }

  protected final @NotNull Path getProjectDirOrFile(boolean isDirectoryBasedProject) {
    if (!isDirectoryBasedProject && isCreateProjectFileExplicitly()) {
      try {
        File tempFile = FileUtil.createTempFile(getName(), ProjectFileType.DOT_DEFAULT_EXTENSION);
        myFilesToDelete.add(tempFile);
        return tempFile.toPath();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    Path tempFile = TemporaryDirectory.generateTemporaryPath(FileUtil.sanitizeFileName(getName(), false) + (isDirectoryBasedProject ? "" : ProjectFileType.DOT_DEFAULT_EXTENSION));
    myFilesToDelete.add(tempFile.toFile());
    return tempFile;
  }

  protected void setUpModule() {
    try {
      WriteCommandAction.writeCommandAction(getProject()).run(() -> myModule = createMainModule());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected @NotNull Module createMainModule() throws IOException {
    return createModule(myProject.getName());
  }

  protected @NotNull Module createModule(@NonNls @NotNull String moduleName) {
    return doCreateRealModule(moduleName);
  }

  protected @NotNull Module doCreateRealModule(@NotNull String moduleName) {
    return doCreateRealModuleIn(moduleName, myProject, getModuleType());
  }

  protected @NotNull Module doCreateRealModuleIn(@NotNull String moduleName, @NotNull Project project, @NotNull ModuleType<?> moduleType) {
    return createModuleAt(moduleName, project, moduleType, Objects.requireNonNull(project.getBasePath()));
  }

  protected @NotNull Module createModuleAt(@NotNull String moduleName,
                                           @NotNull Project project,
                                           @NotNull ModuleType<?> moduleType,
                                           @NotNull String path) {
    if (isCreateProjectFileExplicitly()) {
      File moduleFile = new File(FileUtil.toSystemDependentName(path), moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      FileUtil.createIfDoesntExist(moduleFile);
      myFilesToDelete.add(moduleFile);
      return WriteAction.computeAndWait(() -> {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
        assertNotNull(virtualFile);
        Module module = ModuleManager.getInstance(project).newModule(virtualFile.getPath(), moduleType.getId());
        module.getModuleFile();
        return module;
      });
    }

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return WriteAction.computeAndWait(
      () -> moduleManager.newModule(path + File.separatorChar + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION, moduleType.getId()));
  }

  protected @NotNull ModuleType getModuleType() {
    return EmptyModuleType.getInstance();
  }

  public static void cleanupApplicationCaches(@Nullable Project project) {
    Application app = ApplicationManager.getApplication();
    if (app == null) {
      return;
    }

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

    UndoManagerImpl globalInstance = (UndoManagerImpl)UndoManager.getGlobalInstance();
    if (globalInstance != null) {
      globalInstance.dropHistoryInTests();
    }

    if (project != null && !project.isDisposed()) {
      ((UndoManagerImpl)UndoManager.getInstance(project)).dropHistoryInTests();
      ((DocumentReferenceManagerImpl)DocumentReferenceManager.getInstance()).cleanupForNextTest();

      ((PsiManagerImpl)PsiManager.getInstance(project)).cleanupForNextTest();
    }

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceExIfCreated();
    if (projectManager != null && projectManager.isDefaultProjectInitialized()) {
      Project defaultProject = projectManager.getDefaultProject();
      ((PsiManagerImpl)PsiManager.getInstance(defaultProject)).cleanupForNextTest();
    }

    FileBasedIndex fileBasedIndex = app.getServiceIfCreated(FileBasedIndex.class);
    if (fileBasedIndex != null) {
      ((FileBasedIndexImpl)fileBasedIndex).cleanupForNextTest();
    }

    if (app.getServiceIfCreated(VirtualFileManager.class) != null) {
      LocalFileSystemImpl localFileSystem = (LocalFileSystemImpl)LocalFileSystem.getInstance();
      if (localFileSystem != null) {
        localFileSystem.cleanupForNextTest();
      }
    }
  }

  private static @NotNull Set<VirtualFile> eternallyLivingFiles() {
    if (ourEternallyLivingFilesCache != null) {
      return ourEternallyLivingFilesCache;
    }

    Set<VirtualFile> survivors = new HashSet<>();

    for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensions()) {
      for (VirtualFile file : IndexableSetContributor.getRootsToIndex(contributor)) {
        registerSurvivor(survivors, file);
      }
    }

    ourEternallyLivingFilesCache = survivors;
    return survivors;
  }

  public static void addSurvivingFiles(@NotNull Collection<? extends VirtualFile> files) {
    for (VirtualFile each : files) {
      registerSurvivor(eternallyLivingFiles(), each);
    }
  }

  private static void registerSurvivor(@NotNull Set<? super VirtualFile> survivors, @NotNull VirtualFile file) {
    addSubTree(file, survivors);
    while (file != null && survivors.add(file)) {
      file = file.getParent();
    }
  }

  private static void addSubTree(@NotNull VirtualFile root, @NotNull Set<? super VirtualFile> to) {
    if (root instanceof VirtualDirectoryImpl) {
      for (VirtualFile child : ((VirtualDirectoryImpl)root).getCachedChildren()) {
        if (child instanceof VirtualDirectoryImpl) {
          to.add(child);
          addSubTree(child, to);
        }
      }
    }
  }

  @Override
  protected void tearDown() throws Exception {
    Project project = myProject;
    if (project != null && !project.isDisposed()) {
      TestApplicationManagerKt.waitForProjectLeakingThreads(project);
    }

    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    runAll(
      () -> disposeRootDisposable(),
      () -> {
        if (myProject != null) {
          TestApplicationManagerKt.tearDownProjectAndApp(myProject);
          myProject = null;
        }
      },
      () -> {
        if (myProject != null) {
          PlatformTestUtil.closeAndDisposeProjectAndCheckThatNoOpenProjects(myProject);
          myProject = null;
        }
      },
      () -> UIUtil.dispatchAllInvocationEvents(),
      () -> {
        if (myCodeStyleSettingsTracker != null) {
          myCodeStyleSettingsTracker.checkForSettingsDamage();
        }
      },
      () -> {
        if (project != null) {
          InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);
        }
      },
      () -> {
        StartMarkAction.checkCleared(project);
        InplaceRefactoring.checkCleared();
      },
      () -> {
        JarFileSystemImpl.cleanupForNextTest();

        getTempDir().deleteAll();
        LocalFileSystem.getInstance().refreshIoFiles(myFilesToDelete);
        LaterInvocator.dispatchPendingFlushes();
      },
      () -> {
        if (!myAssertionsInTestDetected) {
          if (IdeaLogger.ourErrorsOccurred != null) {
            throw IdeaLogger.ourErrorsOccurred;
          }
        }
      },
      () -> super.tearDown(),
      () -> {
        if (myEditorListenerTracker != null) {
          myEditorListenerTracker.checkListenersLeak();
        }
      },
      () -> {
        if (myThreadTracker != null) {
          myThreadTracker.checkLeak();
        }
      },
      () -> LightPlatformTestCase.checkEditorsReleased(),
      () -> myOldSdks.checkForJdkTableLeaks(),
      () -> myVirtualFilePointerTracker.assertPointersAreDisposed(),
      () -> {
        myModule = null;
        myFilesToDelete.clear();
        myEditorListenerTracker = null;
        myThreadTracker = null;
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourTestCase = null;
      }
    );
  }

  protected void resetAllFields() {
    resetClassFields(getClass());
  }

  @Override
  protected final @NotNull <T extends Disposable> T disposeOnTearDown(@NotNull T disposable) {
    Disposer.register(myProject, disposable);
    return disposable;
  }

  private void resetClassFields(@NotNull Class<?> aClass) {
    try {
      clearDeclaredFields(this, aClass);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }

    if (aClass == HeavyPlatformTestCase.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  protected void registerTestProjectJdk(Sdk jdk) {
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();

    for (Sdk existingSdk : jdkTable.getAllJdks()) {
      if (existingSdk == jdk) return;
    }

    WriteAction.runAndWait(()-> jdkTable.addJdk(jdk, myProject));
  }

  protected void setUpJdk() {
    final Sdk jdk = getTestProjectJdk();

    if (jdk != null) {
      registerTestProjectJdk(jdk);
    }

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ModuleRootModificationUtil.setModuleSdk(module, jdk);
    }
  }

  protected @Nullable Sdk getTestProjectJdk() {
    return null;
  }

  @Override
  public void runBare() throws Throwable {
    if (!shouldRunTest()) return;

    TestRunnerUtil.replaceIdeEventQueueSafely();
    try {
      runBareImpl();
    }
    finally {
      try {
        EdtTestUtil.runInEdtAndWait(() -> {
          cleanupApplicationCaches(getProject());
          resetAllFields();
        });
      }
      catch (Throwable ignored) {
      }
    }
  }

  private void runBareImpl() throws Throwable {
    ThrowableRunnable<Throwable> runnable = () -> {
      try {
        myAssertionsInTestDetected = true;
        setUp();
        myAssertionsInTestDetected = false;
      }
      catch (Throwable e) {
        try {
          tearDown();
        }
        catch (Throwable ignored) {
        }

        throw e;
      }

      Throwable exception = null;
      try {
        myAssertionsInTestDetected = true;
        runTest();
        myAssertionsInTestDetected = false;
      }
      catch (Throwable e) {
        exception = e;
      }
      finally {
        try {
          tearDown();
        }
        catch (Throwable e) {
          if (exception == null) {
            exception = e;
          }
        }
      }

      if (exception != null) {
        throw exception;
      }
    };

    runBareRunnable(runnable);

    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }

    // just to make sure all deferred Runnable's to finish
    waitForAllLaters();
    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }
  }

  private static void waitForAllLaters() throws InterruptedException, InvocationTargetException {
    for (int i = 0; i < 3; i++) {
      SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());
    }
  }

  protected void runBareRunnable(@NotNull ThrowableRunnable<Throwable> runnable) throws Throwable {
    if (runInDispatchThread()) {
      EdtTestUtil.runInEdtAndWait(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected boolean isRunInWriteAction() {
    return false;
  }

  @Override
  protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
    Ref<Exception> e = new Ref<>();
    Runnable runnable1 = () -> {
      try {
        if (ApplicationManager.getApplication().isDispatchThread() && isRunInWriteAction()) {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
        else {
          runnable.run();
        }
      }
      catch (Exception e1) {
        e.set(e1);
      }
    };

    if (annotatedWith(WrapInCommand.class)) {
      CommandProcessor.getInstance().executeCommand(myProject, runnable1, "", null);
    }
    else {
      runnable1.run();
    }

    if (!e.isNull()) {
      throw e.get();
    }
  }

  @Override
  public Object getData(@NotNull String dataId) {
    return myProject == null || myProject.isDisposed() ? null : new TestDataProvider(myProject).getData(dataId);
  }

  public @NotNull File createTempDir(@NonNls @NotNull String prefix) throws IOException {
    return createTempDir(prefix, true);
  }

  public @NotNull File createTempDir(@NonNls @NotNull String prefix, final boolean refresh) throws IOException {
    final File tempDirectory = FileUtilRt.createTempDirectory("idea_test_" + prefix, null, false);
    myFilesToDelete.add(tempDirectory);
    if (refresh) {
      getVirtualFile(tempDirectory);
    }
    return tempDirectory;
  }

  protected static VirtualFile getVirtualFile(@NotNull File file) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  protected @NotNull File createTempDirectory() throws IOException {
    return createTempDir("");
  }

  protected @NotNull File createTempDirectory(final boolean refresh) throws IOException {
    return createTempDir("", refresh);
  }

  protected @NotNull File createTempFile(@NotNull String name, @Nullable String text) throws IOException {
    File directory = createTempDirectory();
    File file = new File(directory, name);
    if (!file.createNewFile()) {
      throw new IOException("Can't create " + file);
    }
    if (text != null) {
      FileUtil.writeToFile(file, text);
    }
    return file;
  }

  public static void setContentOnDisk(@NotNull File file, byte @Nullable [] bom, @NotNull String content, @NotNull Charset charset)
    throws IOException {
    FileOutputStream stream = new FileOutputStream(file);
    if (bom != null) {
      stream.write(bom);
    }
    try (OutputStreamWriter writer = new OutputStreamWriter(stream, charset)) {
      writer.write(content);
    }
  }

  public @NotNull VirtualFile createTempFile(@NonNls @NotNull String ext,
                                             byte @Nullable [] bom,
                                             @NonNls @NotNull String content,
                                             @NotNull Charset charset) throws IOException {
    File temp = FileUtil.createTempFile("copy", "." + ext);
    setContentOnDisk(temp, bom, content, charset);

    myFilesToDelete.add(temp);
    final VirtualFile file = getVirtualFile(temp);
    assert file != null : temp;
    return file;
  }

  protected @Nullable PsiFile getPsiFile(@NotNull Document document) {
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
  }

  private static void setPlatformPrefix(@NotNull String prefix) {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, prefix);
    ourPlatformPrefixInitialized = true;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface WrapInCommand {
  }

  protected static @NotNull VirtualFile createChildData(final @NotNull VirtualFile dir, @NonNls final @NotNull String name) {
    try {
      return WriteAction.computeAndWait(() -> dir.createChildData(null, name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static @NotNull VirtualFile createChildDirectory(final @NotNull VirtualFile dir, @NonNls final @NotNull String name) {
    try {
      return WriteAction.computeAndWait(() -> dir.createChildDirectory(null, name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void rename(final @NotNull VirtualFile vFile1, final @NotNull String newName) {
    try {
      WriteCommandAction.writeCommandAction(null).run(() -> vFile1.rename(vFile1, newName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void delete(final @NotNull VirtualFile vFile1) {
    VfsTestUtil.deleteFile(vFile1);
  }

  public static void move(final @NotNull VirtualFile vFile1, final @NotNull VirtualFile newFile) {
    try {
      WriteCommandAction.writeCommandAction(null).run(() -> vFile1.move(vFile1, newFile));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static @NotNull VirtualFile copy(final @NotNull VirtualFile file, final @NotNull VirtualFile newParent, final @NotNull String copyName) {
    final VirtualFile[] copy = new VirtualFile[1];

    try {
      WriteCommandAction.writeCommandAction(null).run(() -> copy[0] = file.copy(file, newParent, copyName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return copy[0];
  }

  public static void copyDirContentsTo(final @NotNull VirtualFile vTestRoot, final @NotNull VirtualFile toDir) {
    try {
      WriteCommandAction.writeCommandAction(null).run(() -> {
        for (VirtualFile file : vTestRoot.getChildren()) {
          VfsUtil.copy(file, file, toDir);
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setFileText(final @NotNull VirtualFile file, final @NotNull String text) {
    try {
      WriteAction.runAndWait(() -> VfsUtil.saveText(file, text));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setBinaryContent(final @NotNull VirtualFile file, final byte @NotNull [] content) {
    try {
      WriteAction.runAndWait(() -> file.setBinaryContent(content));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setBinaryContent(final @NotNull VirtualFile file,
                                      final byte @NotNull [] content,
                                      final long newModificationStamp,
                                      final long newTimeStamp,
                                      final Object requestor) {
    try {
      WriteAction.runAndWait(() -> file.setBinaryContent(content, newModificationStamp, newTimeStamp, requestor));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected @NotNull VirtualFile getOrCreateProjectBaseDir() {
    String basePath = myProject.getBasePath();
    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(Objects.requireNonNull(basePath));
    if (baseDir == null) {
      try {
        Files.createDirectories(Paths.get(basePath));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath));
    }
    return baseDir;
  }

  protected static @NotNull VirtualFile getOrCreateModuleDir(@NotNull Module module) throws IOException {
    File moduleDir = new File(PathUtil.getParentPath(module.getModuleFilePath()));
    FileUtil.ensureExists(moduleDir);
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleDir));
  }
}
