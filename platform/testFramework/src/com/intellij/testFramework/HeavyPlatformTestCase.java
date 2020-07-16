// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.idea.IdeaLogger;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.AccessToken;
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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.project.ProjectKt;
import com.intellij.project.TestProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  protected Project myProject;
  protected Module myModule;

  private final TemporaryDirectory temporaryDirectory = new TemporaryDirectory();

  protected boolean myAssertionsInTestDetected;
  private static TestCase ourTestCase;
  private EditorListenerTracker myEditorListenerTracker;
  private ThreadTracker myThreadTracker;

  private static boolean ourPlatformPrefixInitialized;
  private static Set<VirtualFile> ourEternallyLivingFilesCache;
  private SdkLeakTracker myOldSdks;
  private VirtualFilePointerTracker myVirtualFilePointerTracker;
  private @Nullable CodeStyleSettingsTracker myCodeStyleSettingsTracker;

  private AccessToken projectTracker;

  protected final @NotNull TemporaryDirectory getTempDir() {
    return temporaryDirectory;
  }

  protected final @NotNull VirtualFile createTestProjectStructure() {
    return createTestProjectStructure(null, true);
  }

  protected final @NotNull VirtualFile createTestProjectStructure(@Nullable String rootPath) {
    return createTestProjectStructure(rootPath, true);
  }

  protected final @NotNull VirtualFile createTestProjectStructure(@Nullable String rootPath, boolean addProjectRoots) {
    Path dir = temporaryDirectory.newPath();
    try {
      Files.createDirectories(dir);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    VirtualFile result = HeavyTestHelper.createTestProjectStructure(myModule, rootPath, dir, addProjectRoots);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    return result;
  }

  public static @NotNull VirtualFile createTestProjectStructure(@Nullable Module module, @Nullable String rootPath, boolean addProjectRoots, @NotNull TemporaryDirectory temporaryDirectory) {
    Path dir = temporaryDirectory.newPath();
    try {
      Files.createDirectories(dir);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return HeavyTestHelper.createTestProjectStructure(module, rootPath, dir, addProjectRoots);
  }

  protected @NotNull VirtualFile createTestProjectStructure(@NotNull Project project, @Nullable Module module, @Nullable String rootPath, boolean addProjectRoots) {
    VirtualFile file = createTestProjectStructure(module, rootPath, addProjectRoots, getTempDir());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    return file;
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
  final @NotNull Path createGlobalTempDirectory() {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    String testName = policy == null ? null : policy.getPerTestTempDirName();
    if (testName == null) {
      testName = TEMP_DIR_MARKER + TemporaryDirectory.testNameToFileName(getName());
    }

    Path result = TemporaryDirectory.generateTemporaryPath(testName);
    //noinspection deprecation
    temporaryDirectory.scheduleDelete(result);
    FileUtil.resetCanonicalTempPathCache(result.toString());

    // no need to use common prefix as for now in any case global temp directory is set for each test
    temporaryDirectory.init("", result);
    return result;
  }

  @Override
  final void removeGlobalTempDirectory(@NotNull Path dir) {
    temporaryDirectory.after();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    if (ourTestCase != null) {
      String message = "Previous test " + ourTestCase + " hasn't called tearDown(). Probably overridden without super call.";
      ourTestCase = null;
      fail(message);
    }
    IdeaLogger.ourErrorsOccurred = null;

    LOG.debug(getClass().getName() + ".setUp()");

    initApplication();

    projectTracker = ((TestProjectManager)ProjectManager.getInstance()).startTracking();

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
    myProject = doCreateAndOpenProject();

    WriteAction.run(() ->
      ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
        setUpModule();
        setUpJdk();
      })
    );

    LightPlatformTestCase.clearUncommittedDocuments(getProject());

    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
  }

  protected @NotNull OpenProjectTaskBuilder getOpenProjectOptions() {
    // doCreateRealModule uses myProject.getName() as module name - use constant project name because projectFile here unique temp file
    return new OpenProjectTaskBuilder().projectName(getProjectFilename());
  }

  protected @NotNull Project doCreateAndOpenProject() {
    OpenProjectTaskBuilder optionBuilder = getOpenProjectOptions();
    Path projectFile = getProjectDirOrFile(isCreateDirectoryBasedProject());
    return Objects.requireNonNull(ProjectManagerEx.getInstanceEx().openProject(projectFile, optionBuilder.build()));
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

  protected boolean isCreateDirectoryBasedProject() {
    return false;
  }

  protected final @NotNull Path getProjectDirOrFile() {
    return getProjectDirOrFile(isCreateDirectoryBasedProject());
  }

  protected @NotNull Path getProjectDirOrFile(boolean isDirectoryBasedProject) {
    return temporaryDirectory.newPath(getProjectFilename() + (isDirectoryBasedProject ? "" : ProjectFileType.DOT_DEFAULT_EXTENSION));
  }

  private @Nullable String getProjectFilename() {
    String testName = getName();
    return testName == null ? null : FileUtil.sanitizeFileName(testName, false);
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

  @SuppressWarnings("MethodMayBeStatic")
  protected final @NotNull Module doCreateRealModuleIn(@NotNull String moduleName, @NotNull Project project, @NotNull ModuleType<?> moduleType) {
    return HeavyTestHelper.createModuleAt(moduleName, project, moduleType, ProjectKt.getStateStore(project).getProjectBasePath());
  }

  protected final @NotNull Module createModuleAt(@NotNull String moduleName,
                                                 @NotNull Project project,
                                                 @NotNull ModuleType<?> moduleType,
                                                 @NotNull Path path) {
    return HeavyTestHelper.createModuleAt(moduleName, project, moduleType, path);
  }

  protected @NotNull ModuleType<?> getModuleType() {
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
        if (project != null) {
          TestApplicationManagerKt.tearDownProjectAndApp(project);
        }
        // must be set to null only after dispose (maybe used by tests during dispose)
        myProject = null;
      },
      () -> {
        AccessToken projectTracker = this.projectTracker;
        if (projectTracker != null) {
          this.projectTracker = null;
          projectTracker.finish();
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
  protected void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    TestRunnerUtil.replaceIdeEventQueueSafely();
    try {
      runBareImpl(testRunnable);
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

  private void runBareImpl(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
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
        runTestRunnable(testRunnable);
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
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    boolean runInCommand = annotatedWith(WrapInCommand.class);
    boolean runInWriteAction = isRunInWriteAction();

    if (runInCommand && runInWriteAction) {
      WriteCommandAction.writeCommandAction(getProject()).run(() -> super.runTestRunnable(testRunnable));
    }
    else if (runInCommand) {
      Ref<Throwable> e = new Ref<>();
      CommandProcessor.getInstance().executeCommand(getProject(), () -> {
        try {
          super.runTestRunnable(testRunnable);
        }
        catch (Throwable throwable) {
          e.set(throwable);
        }
      }, null, null);
      if (!e.isNull()) {
        throw e.get();
      }
    }
    else if (runInWriteAction) {
      WriteAction.runAndWait(() -> super.runTestRunnable(testRunnable));
    }
    else {
      super.runTestRunnable(testRunnable);
    }
  }

  @Override
  public Object getData(@NotNull String dataId) {
    return myProject == null || myProject.isDisposed() ? null : new TestDataProvider(myProject).getData(dataId);
  }

  protected final @NotNull File createTempDir(@NotNull String prefix) throws IOException {
    Path dir = temporaryDirectory.newPath(prefix, true);
    Files.createDirectories(dir);
    return dir.toFile();
  }

  protected static VirtualFile getVirtualFile(@NotNull File file) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  protected final @NotNull File createTempDirectory() throws IOException {
    return createTempDir("");
  }

  protected final @NotNull File createTempFile(@NotNull String name, @Nullable String text) throws IOException {
    Path dir = temporaryDirectory.newPath("", true);
    Path file = dir.resolve(name);
    if (text == null) {
      Files.createDirectories(dir);
      Files.createFile(file);
    }
    else {
      PathKt.write(file, text);
    }
    return file.toFile();
  }

  protected final @NotNull VirtualFile createVirtualFileWithBom(@NotNull String ext, @NotNull String content) throws IOException {
    VirtualFile file = temporaryDirectory.createVirtualFile('.' + ext);
    WriteAction.runAndWait(() -> {
      try (OutputStream stream = file.getOutputStream(HeavyPlatformTestCase.class)) {
        stream.write(CharsetToolkit.UTF8_BOM);
        try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
          writer.write(content);
        }
      }
    });
    return file;
  }

  protected final @NotNull VirtualFile createVirtualFileWithEncodingUsingNio(@NotNull String ext,
                                                                             byte @Nullable [] bom,
                                                                             @NotNull String content,
                                                                             @NotNull Charset charset) {
    return HeavyTestHelper.createVirtualFileWithEncodingUsingNio(ext, bom, content, charset, getTempDir());
  }

  protected final @Nullable PsiFile getPsiFile(@NotNull Document document) {
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

  protected static @NotNull VirtualFile createChildData(@NotNull VirtualFile dir, @NotNull String name) {
    try {
      return WriteAction.computeAndWait(() -> dir.createChildData(null, name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static @NotNull VirtualFile createChildDirectory(@NotNull VirtualFile dir, @NotNull String name) {
    return HeavyTestHelper.createChildDirectory(dir, name);
  }

  protected static void rename(@NotNull VirtualFile vFile1, @NotNull String newName) {
    try {
      WriteCommandAction.writeCommandAction(null).run(() -> vFile1.rename(vFile1, newName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void delete(@NotNull VirtualFile vFile1) {
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

  protected final @NotNull VirtualFile getOrCreateProjectBaseDir() {
    return HeavyTestHelper.getOrCreateProjectBaseDir(myProject);
  }

  protected final @NotNull Path createTempDirectoryWithSuffix(@Nullable String suffix) throws IOException {
    // heavy test sets canonical temp path per test and deletes it on the end - no need to add created directory to myFilesToDelete
    return FileUtilRt.createTempDirectory(getTestName(true), suffix, false).toPath();
  }

  protected static @NotNull VirtualFile getOrCreateModuleDir(@NotNull Module module) throws IOException {
    Path moduleDir = module.getModuleNioFile().getParent();
    Files.createDirectories(moduleDir);
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(moduleDir));
  }
}
