// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ProjectTopics;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.idea.IdeaLogger;
import com.intellij.lang.Language;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeProjectLifecycleListener;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static com.intellij.testFramework.RunAll.runAll;

/**
 * @author yole
 */
public abstract class LightPlatformTestCase extends UsefulTestCase implements DataProvider {
  private static Project ourProject;
  private static Module ourModule;
  private static PsiManager ourPsiManager;
  private static boolean ourAssertionsInTestDetected;
  private static VirtualFile ourSourceRoot;
  private static TestCase ourTestCase;
  private static LightProjectDescriptor ourProjectDescriptor;
  private static SdkLeakTracker myOldSdks;

  private ThreadTracker myThreadTracker;

  static {
    PlatformTestUtil.registerProjectCleanup(LightPlatformTestCase::closeAndDeleteProject);
  }

  private VirtualFilePointerTracker myVirtualFilePointerTracker;
  private CodeStyleSettingsTracker myCodeStyleSettingsTracker;

  /**
   * @return Project to be used in tests for example for project components retrieval.
   */
  @SuppressWarnings("MethodMayBeStatic")
  protected Project getProject() {
    return ourProject;
  }

  /**
   * @return Module to be used in tests for example for module components retrieval.
   */
  @SuppressWarnings("MethodMayBeStatic")
  protected Module getModule() {
    return ourModule;
  }

  /**
   * Shortcut to PsiManager.getInstance(getProject())
   */
  @NotNull
  protected PsiManager getPsiManager() {
    if (ourPsiManager == null) {
      ourPsiManager = PsiManager.getInstance(getProject());
    }
    return ourPsiManager;
  }

  @NotNull
  public static TestApplicationManager initApplication() {
    return TestApplicationManager.getInstance();
  }

  public static TestApplicationManager getApplication() {
    return TestApplicationManager.getInstanceIfCreated();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void reportTestExecutionStatistics() {
    System.out.println("----- TEST STATISTICS -----");
    UsefulTestCase.logSetupTeardownCosts();
    System.out.printf("##teamcity[buildStatisticValue key='ideaTests.appInstancesCreated' value='%d']%n",
                      MockApplication.INSTANCES_CREATED);
    System.out.printf("##teamcity[buildStatisticValue key='ideaTests.projectInstancesCreated' value='%d']%n",
                      ProjectManagerImpl.TEST_PROJECTS_CREATED);
    long totalGcTime = 0;
    for (GarbageCollectorMXBean mxBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      totalGcTime += mxBean.getCollectionTime();
    }
    System.out.printf("##teamcity[buildStatisticValue key='ideaTests.gcTimeMs' value='%d']%n", totalGcTime);
    System.out.printf("##teamcity[buildStatisticValue key='ideaTests.classesLoaded' value='%d']%n",
                      ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount());
  }

  protected void resetAllFields() {
    resetClassFields(getClass());
  }

  private void resetClassFields(@NotNull Class<?> aClass) {
    try {
      UsefulTestCase.clearDeclaredFields(this, aClass);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    if (aClass == LightPlatformTestCase.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  private static void cleanPersistedVFSContent() {
    ((PersistentFSImpl)PersistentFS.getInstance()).cleanPersistedContents();
  }

  private static void initProject(@NotNull final LightProjectDescriptor descriptor) {
    ourProjectDescriptor = descriptor;

    if (ourProject != null) {
      closeAndDeleteProject();
    }
    ApplicationManager.getApplication().runWriteAction(LightPlatformTestCase::cleanPersistedVFSContent);

    Path tempDirectory = TemporaryDirectory.generateTemporaryPath(ProjectImpl.LIGHT_PROJECT_NAME + ProjectFileType.DOT_DEFAULT_EXTENSION);
    HeavyPlatformTestCase.synchronizeTempDirVfs(tempDirectory);
    setProject(HeavyPlatformTestCase.createProject(tempDirectory));
    ourPathToKeep = tempDirectory;
    ourPsiManager = null;

    try {
      ourProjectDescriptor.setUpProject(ourProject, new LightProjectDescriptor.SetupHandler() {
        @Override
        public void moduleCreated(@NotNull Module module) {
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourModule = module;
        }

        @Override
        public void sourceRootCreated(@NotNull VirtualFile sourceRoot) {
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourSourceRoot = sourceRoot;
        }
      });
    }
    catch (Throwable e) {
      try {
        closeAndDeleteProject();
      }
      catch (Throwable suppressed) {
        e.addSuppressed(suppressed);
      }
      throw new RuntimeException(e);
    }
  }

  /**
   * @return The only source root
   */
  public static VirtualFile getSourceRoot() {
    return ourSourceRoot;
  }

  @Override
  protected void setUp() throws Exception {
    if (isPerformanceTest()) {
      Timings.getStatistics();
    }

    TestApplicationManager testAppManager = initApplication();

    EdtTestUtil.runInEdtAndWait(() -> {
      super.setUp();

      testAppManager.setDataProvider(this);
      LightProjectDescriptor descriptor = getProjectDescriptor();
      doSetup(descriptor, configureLocalInspectionTools(), getTestRootDisposable());
      InjectedLanguageManagerImpl.pushInjectors(getProject());

      myCodeStyleSettingsTracker = new CodeStyleSettingsTracker(
        () -> isStressTest() ||
              ApplicationManager.getApplication() == null ||
              ApplicationManager.getApplication() instanceof MockApplication ? null : CodeStyle.getDefaultSettings());

      myThreadTracker = new ThreadTracker();
      ModuleRootManager.getInstance(ourModule).orderEntries().getAllLibrariesAndSdkClassesRoots();
      myVirtualFilePointerTracker = new VirtualFilePointerTracker();
    });
  }

  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return new SimpleLightProjectDescriptor(getModuleTypeId(), getProjectJDK());
  }

  @NotNull
  public static Pair.NonNull<Project, Module> doSetup(@NotNull LightProjectDescriptor descriptor,
                                                      LocalInspectionTool @NotNull [] localInspectionTools,
                                                      @NotNull Disposable parentDisposable) {
    assertNull("Previous test " + ourTestCase + " hasn't called tearDown(). Probably overridden without super call.", ourTestCase);
    IdeaLogger.ourErrorsOccurred = null;
    ApplicationManager.getApplication().assertIsDispatchThread();

    myOldSdks = new SdkLeakTracker();

    boolean reusedProject = true;
    if (ourProject == null || ourProjectDescriptor == null || !ourProjectDescriptor.equals(descriptor)) {
      initProject(descriptor);
      reusedProject = false;
    }

    descriptor.registerSdk(parentDisposable);

    ProjectManagerEx projectManagerEx = ProjectManagerEx.getInstanceEx();
    Project project = ourProject;
    try {
      projectManagerEx.openTestProject(project);
    }
    catch (Throwable e) {
      setProject(null);
      throw e;
    }
    if (reusedProject) {
      // clear all caches, reindex
      WriteAction.run(() -> ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true));
    }

    MessageBusConnection connection = project.getMessageBus().connect(parentDisposable);
    connection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void moduleAdded(@NotNull Project project, @NotNull Module module) {
        fail("Adding modules is not permitted in light tests.");
      }
    });

    clearUncommittedDocuments(project);

    InspectionsKt.configureInspections(localInspectionTools, project, parentDisposable);

    assertFalse(PsiManager.getInstance(project).isDisposed());
    Boolean passed = null;
    try {
      passed = StartupManagerEx.getInstanceEx(project).startupActivityPassed();
    }
    catch (Exception ignored) {

    }
    assertTrue("open: " + project.isOpen() +
               "; disposed:" + project.isDisposed() +
               "; startup passed:" + passed +
               "; all open projects: " + Arrays.asList(ProjectManager.getInstance().getOpenProjects()), project.isInitialized());

    CodeStyle.setTemporarySettings(project, CodeStyle.createTestSettings());

    final FileDocumentManager manager = FileDocumentManager.getInstance();
    if (manager instanceof FileDocumentManagerImpl) {
      Document[] unsavedDocuments = manager.getUnsavedDocuments();
      manager.saveAllDocuments();
      ApplicationManager.getApplication().runWriteAction(((FileDocumentManagerImpl)manager)::dropAllUnsavedDocuments);

      assertEmpty("There are unsaved documents", Arrays.asList(unsavedDocuments));
    }
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
    return Pair.createNonNull(project, ourModule);
  }

  protected void enableInspectionTools(InspectionProfileEntry @NotNull ... tools) {
    InspectionsKt.enableInspectionTools(getProject(), getTestRootDisposable(), tools);
  }

  protected void enableInspectionTool(@NotNull InspectionToolWrapper<?,?> toolWrapper) {
    InspectionsKt.enableInspectionTool(getProject(), toolWrapper, getTestRootDisposable());
  }

  protected void enableInspectionTool(@NotNull InspectionProfileEntry tool) {
    InspectionsKt.enableInspectionTool(getProject(), tool, getTestRootDisposable());
  }

  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return LocalInspectionTool.EMPTY_ARRAY;
  }

  @Override
  protected void tearDown() throws Exception {
    Project project = getProject();

    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    runAll(
      () -> {
        if (ApplicationManager.getApplication() != null) {
          CodeStyle.dropTemporarySettings(project);
        }
      },
      () -> {
        if (myCodeStyleSettingsTracker != null) {
          myCodeStyleSettingsTracker.checkForSettingsDamage();
        }
      },
      () -> {
        StartMarkAction.checkCleared(project);
        InplaceRefactoring.checkCleared();
      },
      () -> {
        if (project != null) {
          doTearDown(project, null);
        }
      },
      () -> {
        if (project != null) {
          // needed for myVirtualFilePointerTracker check below
          ((ProjectRootManagerImpl)ProjectRootManager.getInstance(project)).clearScopesCachesForModules();
        }
      },
      () -> checkEditorsReleased(),
      () -> super.tearDown(),
      () -> WriteAction.runAndWait(() -> {
        if (project != null && LegacyBridgeProjectLifecycleListener.Companion.enabled(project)) {
          ProjectJdkTableImpl jdkTable = (ProjectJdkTableImpl)ProjectJdkTable.getInstance();
          for (Sdk jdk : jdkTable.getAllJdks()) {
            jdkTable.removeTestJdk(jdk);
          }
        }
      }),
      () -> myOldSdks.checkForJdkTableLeaks(),
      () -> {
        if (myThreadTracker != null) {
          myThreadTracker.checkLeak();
        }
      },
      () -> {
        if (project != null) {
          InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);
        }
      },
      () -> {
        if (myVirtualFilePointerTracker != null) {
          myVirtualFilePointerTracker.assertPointersAreDisposed();
        }
      }
    );
  }

  public static void doTearDown(@NotNull Project project, @Nullable TestApplicationManager testAppManager) {
    try {
      TestApplicationManagerKt.tearDownProjectAndApp(project, testAppManager);
    }
    finally {
      ourTestCase = null;
    }
  }

  static void checkAssertions() throws Exception {
    if (!ourAssertionsInTestDetected) {
      if (IdeaLogger.ourErrorsOccurred != null) {
        throw IdeaLogger.ourErrorsOccurred;
      }
    }
  }

  static void tearDownSourceRoot(@NotNull Project project) {
    WriteCommandAction.runWriteCommandAction(project, () -> {
      if (ourSourceRoot != null) {
        try {
          for (VirtualFile child : ourSourceRoot.getChildren()) {
            child.delete(LightPlatformTestCase.class);
          }
        }
        catch (IOException e) {
          //noinspection CallToPrintStackTrace
          e.printStackTrace();
        }
      }
    });
  }

  public static void clearEncodingManagerDocumentQueue() {
    EncodingManager encodingManager = ApplicationManager.getApplication().getServiceIfCreated(EncodingManager.class);
    if (encodingManager instanceof EncodingManagerImpl) {
      ((EncodingManagerImpl)encodingManager).clearDocumentQueue();
    }
  }

  public static void clearUncommittedDocuments(@NotNull Project project) {
    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
    documentManager.clearUncommittedDocuments();

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    if (projectManager.isDefaultProjectInitialized()) {
      Project defaultProject = projectManager.getDefaultProject();
      ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(defaultProject)).clearUncommittedDocuments();
    }
  }

  public static void checkEditorsReleased() {
    // don't use method references here to make stack trace reading easier
    //noinspection Convert2MethodRef
    runAll(
      () -> UIUtil.dispatchAllInvocationEvents(),
      () -> {
        Application app = ApplicationManager.getApplication();
        // getAllEditors() should be called only after dispatchAllInvocationEvents(), that's why separate RunAll is used
        EditorFactory editorFactory = app == null ? null : app.getServiceIfCreated(EditorFactory.class);
        if (editorFactory == null) {
          return;
        }

        RunAll runAll = new RunAll();
        for (Editor editor : editorFactory.getAllEditors()) {
          runAll = runAll
            .append(
              () -> EditorFactoryImpl.throwNotReleasedError(editor),
              () -> editorFactory.releaseEditor(editor)
            );
        }
        runAll.run();
      });
  }

  @Override
  public final void runBare() throws Throwable {
    runBareImpl(this::startRunAndTear);
  }

  protected void runBareImpl(ThrowableRunnable<?> start) throws Throwable {
    if (!shouldRunTest()) {
      return;
    }

    TestRunnerUtil.replaceIdeEventQueueSafely();
    ThrowableRunnable<Throwable> testRunnable = () -> {
      try {
        start.run();
      }
      finally {
        EdtTestUtil.runInEdtAndWait(() -> {
          try {
            Application application = ApplicationManager.getApplication();
            if (application instanceof ApplicationEx) {
              HeavyPlatformTestCase.cleanupApplicationCaches(getProject());
            }
            resetAllFields();
          }
          catch (Throwable e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
          }
        });
      }
    };
    if (runInDispatchThread()) {
      EdtTestUtil.runInEdtAndWait(testRunnable);
    }
    else {
      testRunnable.run();
    }

    // just to make sure all deferred Runnables to finish
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());

    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private void startRunAndTear() throws Throwable {
    setUp();
    try {
      ourAssertionsInTestDetected = true;
      runTest();
      ourAssertionsInTestDetected = false;
    }
    finally {
      //try{
      EdtTestUtil.runInEdtAndWait(() -> tearDown());
      //}
      //catch(Throwable th){
      //th.printStackTrace();
      //}
    }
  }

  @Override
  public Object getData(@NotNull String dataId) {
    return getProject() == null || getProject().isDisposed() ? null : new TestDataProvider(getProject()).getData(dataId);
  }

  protected Sdk getProjectJDK() {
    return null;
  }

  @NotNull
  protected String getModuleTypeId() {
    return EmptyModuleType.EMPTY_MODULE;
  }

  /**
   * Creates dummy source file. One is not placed under source root so some PSI functions like resolve to external classes
   * may not work. Though it works significantly faster and yet can be used if you need to create some PSI structures for
   * test purposes
   *
   * @param fileName - name of the file to create. Extension is used to choose what PSI should be created like java, jsp, aj, xml etc.
   * @param text     - file text.
   * @return dummy psi file.
   *
   */
  @NotNull
  protected PsiFile createFile(@NonNls @NotNull String fileName, @NonNls @NotNull String text) throws IncorrectOperationException {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject())
      .createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), true, false);
  }

  @NotNull
  protected PsiFile createLightFile(@NonNls @NotNull String fileName, @NotNull String text) throws IncorrectOperationException {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject())
      .createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), false, false);
  }

  /**
   * Convenient conversion of testSomeTest -> someTest | SomeTest where testSomeTest is the name of current test.
   *
   * @param lowercaseFirstLetter - whether first letter after test should be lowercased.
   */
  @NotNull
  @Override
  protected String getTestName(boolean lowercaseFirstLetter) {
    String name = getName();
    assertTrue("Test name should start with 'test': " + name, name.startsWith("test"));
    name = name.substring("test".length());
    if (!name.isEmpty() && lowercaseFirstLetter && !PlatformTestUtil.isAllUppercaseName(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  @NotNull
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyle.getSettings(getProject());
  }

  @NotNull
  protected CommonCodeStyleSettings getLanguageSettings(@NotNull Language language) {
    return getCurrentCodeStyleSettings().getCommonSettings(language);
  }

  @NotNull
  protected <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull Class<T> settingsClass) {
    return getCurrentCodeStyleSettings().getCustomSettings(settingsClass);
  }

  protected void commitDocument(@NotNull Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
  }

  protected void commitAllDocuments() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  protected Document getDocument(@NotNull PsiFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
  public static synchronized void closeAndDeleteProject() {
    Project project = ourProject;
    if (project == null) {
      return;
    }
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IllegalStateException("Must not call closeAndDeleteProject from under write action");
    }

    if (!project.isDisposed()) {
      assertEquals(project, ourModule.getProject());

      @SuppressWarnings("ConstantConditions")
      File ioFile = new File(project.getProjectFilePath());
      if (ioFile.exists()) {
        File dir = ioFile.getParentFile();
        if (dir.getName().startsWith(UsefulTestCase.TEMP_DIR_MARKER)) {
          FileUtil.delete(dir);
        }
        else {
          FileUtil.delete(ioFile);
        }
      }
    }

    assertTrue(ProjectManagerEx.getInstanceEx().forceCloseProject(project));
    assertTrue(project.isDisposed());

    // project may be disposed but empty folder may still be there
    if (ourPathToKeep != null) {
      Path parent = ourPathToKeep.getParent();
      if (parent.getFileName().toString().startsWith(UsefulTestCase.TEMP_DIR_MARKER)) {
        // delete only empty folders
        try {
          Files.deleteIfExists(parent);
        }
        catch (IOException ignore) {
        }
      }
    }

    setProject(null);
    assertTrue(ourModule.isDisposed());
    ourModule = null;
    if (ourPsiManager != null) {
      assertTrue(ourPsiManager.isDisposed());
      ourPsiManager = null;
    }
    ourPathToKeep = null;
  }

  protected static void setProject(Project project) {
    ourProject = project;
  }

  private static class SimpleLightProjectDescriptor extends LightProjectDescriptor {
    @NotNull private final String myModuleTypeId;
    @Nullable private final Sdk mySdk;

    SimpleLightProjectDescriptor(@NotNull String moduleTypeId, @Nullable Sdk sdk) {
      myModuleTypeId = moduleTypeId;
      mySdk = sdk;
    }

    @NotNull
    @Override
    public String getModuleTypeId() {
      return myModuleTypeId;
    }

    @Nullable
    @Override
    public Sdk getSdk() {
      return mySdk;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SimpleLightProjectDescriptor that = (SimpleLightProjectDescriptor)o;

      if (!myModuleTypeId.equals(that.myModuleTypeId)) return false;
      return areJdksEqual(that.getSdk());
    }

    @Override
    public int hashCode() {
      return myModuleTypeId.hashCode();
    }

    private boolean areJdksEqual(final Sdk newSdk) {
      if (mySdk == null || newSdk == null) return mySdk == newSdk;
      if (!mySdk.getName().equals(newSdk.getName())) return false;

      OrderRootType[] rootTypes = {OrderRootType.CLASSES, AnnotationOrderRootType.getInstance()};
      for (OrderRootType rootType : rootTypes) {
        final String[] myUrls = mySdk.getRootProvider().getUrls(rootType);
        final String[] newUrls = newSdk.getRootProvider().getUrls(rootType);
        if (!ContainerUtil.newHashSet(myUrls).equals(ContainerUtil.newHashSet(newUrls))) return false;
      }
      return true;
    }
  }
}
