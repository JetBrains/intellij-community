/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaLogger;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.DocumentReferenceManagerImpl;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
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
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author yole
 */
public abstract class LightPlatformTestCase extends UsefulTestCase implements DataProvider {
  @NonNls private static final String LIGHT_PROJECT_MARK = "Light project: ";

  private static IdeaTestApplication ourApplication;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  protected static Project ourProject;
  private static Module ourModule;
  private static PsiManager ourPsiManager;
  private static boolean ourAssertionsInTestDetected;
  private static VirtualFile ourSourceRoot;
  private static TestCase ourTestCase;
  public static Thread ourTestThread;
  private static LightProjectDescriptor ourProjectDescriptor;
  private static Sdk[] myOldSdks;

  private ThreadTracker myThreadTracker;

  static {
    PlatformTestUtil.registerProjectCleanup(LightPlatformTestCase::closeAndDeleteProject);
  }

  /**
   * @return Project to be used in tests for example for project components retrieval.
   */
  public static Project getProject() {
    return ourProject;
  }

  /**
   * @return Module to be used in tests for example for module components retrieval.
   */
  public static Module getModule() {
    return ourModule;
  }

  /**
   * Shortcut to PsiManager.getInstance(getProject())
   */
  @NotNull
  public static PsiManager getPsiManager() {
    if (ourPsiManager == null) {
      ourPsiManager = PsiManager.getInstance(ourProject);
    }
    return ourPsiManager;
  }

  @NotNull
  public static IdeaTestApplication initApplication() {
    ourApplication = IdeaTestApplication.getInstance();
    return ourApplication;
  }

  @TestOnly
  public static void disposeApplication() {
    if (ourApplication != null) {
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(ourApplication));

      ourApplication = null;
    }
  }

  public static IdeaTestApplication getApplication() {
    return ourApplication;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void reportTestExecutionStatistics() {
    System.out.println("----- TEST STATISTICS -----");
    UsefulTestCase.logSetupTeardownCosts();
    System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.appInstancesCreated' value='%d']",
                                     MockApplication.INSTANCES_CREATED));
    System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.projectInstancesCreated' value='%d']",
                                     ProjectManagerImpl.TEST_PROJECTS_CREATED));
    long totalGcTime = 0;
    for (GarbageCollectorMXBean mxBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      totalGcTime += mxBean.getCollectionTime();
    }
    System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.gcTimeMs' value='%d']", totalGcTime));
    System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.classesLoaded' value='%d']",
                                     ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount()));
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

  private static void initProject(@NotNull final LightProjectDescriptor descriptor) throws Exception {
    ourProjectDescriptor = descriptor;

    if (ourProject != null) {
      closeAndDeleteProject();
    }
    ApplicationManager.getApplication().runWriteAction(LightPlatformTestCase::cleanPersistedVFSContent);

    final File projectFile = FileUtil.createTempFile(ProjectImpl.LIGHT_PROJECT_NAME, ProjectFileType.DOT_DEFAULT_EXTENSION);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectFile);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    new Throwable(projectFile.getPath()).printStackTrace(new PrintStream(buffer));

    ourProject = PlatformTestCase.createProject(projectFile, LIGHT_PROJECT_MARK + buffer);
    ourPathToKeep = projectFile.getPath();
    ourPsiManager = null;

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

    // project creation may make a lot of pointers, do not regard them as leak
    ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).storePointers();
  }

  /**
   * @return The only source root
   */
  public static VirtualFile getSourceRoot() {
    return ourSourceRoot;
  }

  @Override
  protected void setUp() throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      super.setUp();
      ApplicationInfoImpl.setInStressTest(isStressTest());
      if (isPerformanceTest()) {
        Timings.getStatistics();
      }
      initApplication();

      ourApplication.setDataProvider(this);
      LightProjectDescriptor descriptor = getProjectDescriptor();
      doSetup(descriptor, configureLocalInspectionTools(), getTestRootDisposable());
      InjectedLanguageManagerImpl.pushInjectors(getProject());

      storeSettings();

      myThreadTracker = new ThreadTracker();
      ModuleRootManager.getInstance(ourModule).orderEntries().getAllLibrariesAndSdkClassesRoots();
      VirtualFilePointerManagerImpl filePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
      filePointerManager.storePointers();
    });
  }

  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return new SimpleLightProjectDescriptor(getModuleType(), getProjectJDK());
  }

  public static void doSetup(@NotNull LightProjectDescriptor descriptor,
                             @NotNull LocalInspectionTool[] localInspectionTools,
                             @NotNull Disposable parentDisposable) throws Exception {
    assertNull("Previous test " + ourTestCase + " hasn't called tearDown(). Probably overridden without super call.", ourTestCase);
    IdeaLogger.ourErrorsOccurred = null;
    ApplicationManager.getApplication().assertIsDispatchThread();

    myOldSdks = ProjectJdkTable.getInstance().getAllJdks();

    boolean reusedProject = true;
    if (ourProject == null || ourProjectDescriptor == null || !ourProjectDescriptor.equals(descriptor)) {
      initProject(descriptor);
      reusedProject = false;
    }

    ProjectManagerEx projectManagerEx = ProjectManagerEx.getInstanceEx();
    try {
      projectManagerEx.openTestProject(ourProject);
    }
    catch (Throwable e) {
      ourProject = null;
      throw e;
    }
    if (reusedProject) {
      DumbService.getInstance(ourProject).queueTask(new UnindexedFilesUpdater(ourProject));
    }

    MessageBusConnection connection = ourProject.getMessageBus().connect(parentDisposable);
    connection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void moduleAdded(@NotNull Project project, @NotNull Module module) {
        fail("Adding modules is not permitted in LightIdeaTestCase.");
      }
    });

    clearUncommittedDocuments(getProject());

    InspectionsKt.configureInspections(localInspectionTools, getProject(), parentDisposable);

    assertFalse(getPsiManager().isDisposed());
    Boolean passed = null;
    try {
      passed = StartupManagerEx.getInstanceEx(getProject()).startupActivityPassed();
    }
    catch (Exception ignored) {

    }
    assertTrue("open: " + getProject().isOpen() +
               "; disposed:" + getProject().isDisposed() +
               "; startup passed:" + passed +
               "; all open projects: " + Arrays.asList(ProjectManager.getInstance().getOpenProjects()), getProject().isInitialized());

    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(new CodeStyleSettings());

    final FileDocumentManager manager = FileDocumentManager.getInstance();
    if (manager instanceof FileDocumentManagerImpl) {
      Document[] unsavedDocuments = manager.getUnsavedDocuments();
      manager.saveAllDocuments();
      ApplicationManager.getApplication().runWriteAction(((FileDocumentManagerImpl)manager)::dropAllUnsavedDocuments);

      assertEmpty("There are unsaved documents", Arrays.asList(unsavedDocuments));
    }
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
  }

  protected void enableInspectionTools(@NotNull InspectionProfileEntry... tools) {
    InspectionsKt.enableInspectionTools(getProject(), getTestRootDisposable(), tools);
  }

  protected void enableInspectionTool(@NotNull InspectionToolWrapper toolWrapper) {
    InspectionsKt.enableInspectionTool(getProject(), toolWrapper, getTestRootDisposable());
  }

  protected void enableInspectionTool(@NotNull InspectionProfileEntry tool) {
    InspectionsKt.enableInspectionTool(getProject(), tool, getTestRootDisposable());
  }

  @NotNull
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return LocalInspectionTool.EMPTY_ARRAY;
  }

  @SuppressWarnings("TearDownDoesntCallSuperTearDown")
  @Override
  protected void tearDown() throws Exception {
    Project project = getProject();

    new RunAll(
      () -> CodeStyleSettingsManager.getInstance(project).dropTemporarySettings(),
      this::checkForSettingsDamage,
      () -> doTearDown(project, ourApplication),
      () -> checkEditorsReleased(),
      () -> checkForJdkTableLeaks(myOldSdks),
      super::tearDown,
      () -> myThreadTracker.checkLeak(),
      () -> InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project),
      () -> ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).assertPointersAreDisposed()
    ).run();
  }

  public static void doTearDown(@NotNull Project project, @NotNull IdeaTestApplication application) {
    new RunAll().
      append(() -> ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue()).
      append(() -> CodeStyleSettingsManager.getInstance(project).dropTemporarySettings()).
      append(LightPlatformTestCase::checkJavaSwingTimersAreDisposed).
      append(() -> UsefulTestCase.doPostponedFormatting(project)).
      append(() -> LookupManager.getInstance(project).hideActiveLookup()).
      append(() -> ((StartupManagerImpl)StartupManager.getInstance(project)).prepareForNextTest()).
      append(() -> { if (ProjectManager.getInstance() == null) throw new AssertionError("Application components damaged"); }).
      append(() -> WriteCommandAction.runWriteCommandAction(project, () -> {
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

        FileDocumentManager manager = FileDocumentManager.getInstance();
        if (manager instanceof FileDocumentManagerImpl) {
          ((FileDocumentManagerImpl)manager).dropAllUnsavedDocuments();
        }
      })).
      append(() -> assertFalse(PsiManager.getInstance(project).isDisposed())).
      append(() -> {
        EncodingManager encodingManager = EncodingManager.getInstance();
        if (encodingManager instanceof EncodingManagerImpl) {
          ((EncodingManagerImpl)encodingManager).clearDocumentQueue();
        }

        if (!ourAssertionsInTestDetected) {
          if (IdeaLogger.ourErrorsOccurred != null) {
            throw IdeaLogger.ourErrorsOccurred;
          }
        }
      }).
      append(() -> clearUncommittedDocuments(project)).
      append(() -> ((HintManagerImpl)HintManager.getInstance()).cleanup()).
      append(() -> DocumentCommitThread.getInstance().clearQueue()).
      append(() -> ((UndoManagerImpl)UndoManager.getGlobalInstance()).dropHistoryInTests()).
      append(() -> ((UndoManagerImpl)UndoManager.getInstance(project)).dropHistoryInTests()).
      append(() -> ((DocumentReferenceManagerImpl)DocumentReferenceManager.getInstance()).cleanupForNextTest()).
      append(() -> TemplateDataLanguageMappings.getInstance(project).cleanupForNextTest()).
      append(() -> ((PsiManagerImpl)PsiManager.getInstance(project)).cleanupForNextTest()).
      append(() -> ProjectManagerEx.getInstanceEx().closeTestProject(project)).
      append(() -> application.setDataProvider(null)).
      append(() -> ourTestCase = null).
      append(() -> CompletionProgressIndicator.cleanupForNextTest()).
      append(() -> {
        if (ourTestCount++ % 100 == 0) {
          // some tests are written in Groovy, and running all of them may result in some 40M of memory wasted on bean infos
          // so let's clear the cache every now and then to ensure it doesn't grow too large
          GCUtil.clearBeanInfoCache();
        }
      }).
      run();
  }
  
  private static int ourTestCount;

  private static void checkJavaSwingTimersAreDisposed() throws Exception {
    Class<?> TimerQueueClass = Class.forName("javax.swing.TimerQueue");
    Method sharedInstance = ReflectionUtil.getMethod(TimerQueueClass, "sharedInstance");

    Object timerQueue = sharedInstance.invoke(null);
    DelayQueue delayQueue = ReflectionUtil.getField(TimerQueueClass, timerQueue, DelayQueue.class, "queue");
    Delayed timer = delayQueue.peek();
    if (timer != null) {
      long delay = timer.getDelay(TimeUnit.MILLISECONDS);
      String text = "(delayed for " + delay + "ms)";
      Method getTimer = ReflectionUtil.getDeclaredMethod(timer.getClass(), "getTimer");
      Timer swingTimer = (Timer)getTimer.invoke(timer);
      text = "Timer (listeners: "+Arrays.asList(swingTimer.getActionListeners()) + ") "+text;
      throw new AssertionFailedError("Not disposed java.swing.Timer: " + text + "; queue:" + timerQueue);
    }
  }

  public static void clearUncommittedDocuments(@NotNull Project project) {
    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
    documentManager.clearUncommittedDocuments();

    ProjectManagerImpl projectManager = (ProjectManagerImpl)ProjectManager.getInstance();
    if (projectManager.isDefaultProjectInitialized()) {
      Project defaultProject = projectManager.getDefaultProject();
      ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(defaultProject)).clearUncommittedDocuments();
    }
  }

  public static void checkEditorsReleased() {
    new RunAll(
      () -> UIUtil.dispatchAllInvocationEvents(),
      () -> {
        RunAll runAll = new RunAll();
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
          runAll = runAll
            .append(() -> EditorFactoryImpl.throwNotReleasedError(editor))
            .append(() -> EditorFactory.getInstance().releaseEditor(editor));
        }
        runAll.run();
      }).run();
  }

  @Override
  public final void runBare() throws Throwable {
    runBareImpl(this::startRunAndTear);
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  protected void runBareImpl(ThrowableRunnable<?> start) throws Exception {
    if (!shouldRunTest()) {
      return;
    }

    TestRunnerUtil.replaceIdeEventQueueSafely();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        ourTestThread = Thread.currentThread();
        start.run();
      }
      finally {
        ourTestThread = null;
        try {
          Application application = ApplicationManager.getApplication();
          if (application instanceof ApplicationEx) {
            PlatformTestCase.cleanupApplicationCaches(ourProject);
          }
          resetAllFields();
        }
        catch (Throwable e) {
          //noinspection CallToPrintStackTrace
          e.printStackTrace();
        }
      }
    });

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
      tearDown();
      //}
      //catch(Throwable th){
      //  noinspection CallToPrintStackTrace
      //th.printStackTrace();
      //}
    }
  }

  @Override
  public Object getData(String dataId) {
    return ourProject == null || ourProject.isDisposed() ? null : new TestDataProvider(ourProject).getData(dataId);
  }

  protected Sdk getProjectJDK() {
    return null;
  }

  @NotNull
  protected ModuleType getModuleType() {
    return EmptyModuleType.getInstance();
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
  protected static PsiFile createFile(@NonNls @NotNull String fileName, @NonNls @NotNull String text) throws IncorrectOperationException {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject())
      .createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), true, false);
  }

  @NotNull
  protected static PsiFile createLightFile(@NonNls @NotNull String fileName, @NotNull String text) throws IncorrectOperationException {
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

  protected static void commitDocument(@NotNull Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
  }

  protected static void commitAllDocuments() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  @NotNull
  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  protected static Document getDocument(@NotNull PsiFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
  public static synchronized void closeAndDeleteProject() {
    if (ourProject == null) {
      return;
    }
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IllegalStateException("Must not call closeAndDeleteProject from under write action");
    }

    if (!ourProject.isDisposed()) {
      assertEquals(ourProject, ourModule.getProject());

      @SuppressWarnings("ConstantConditions")
      File ioFile = new File(ourProject.getProjectFilePath());
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

    assertTrue(ProjectManagerEx.getInstanceEx().closeAndDispose(ourProject));
    assertTrue(ourProject.isDisposed());

    // project may be disposed but empty folder may still be there
    if (ourPathToKeep != null) {
      File parent = new File(ourPathToKeep).getParentFile();
      if (parent.getName().startsWith(UsefulTestCase.TEMP_DIR_MARKER)) {
        // delete only empty folders
        //noinspection ResultOfMethodCallIgnored
        parent.delete();
      }
    }

    ourProject = null;
    assertTrue(ourModule.isDisposed());
    ourModule = null;
    if (ourPsiManager != null) {
      assertTrue(ourPsiManager.isDisposed());
      ourPsiManager = null;
    }
    ourPathToKeep = null;
  }

  private static class SimpleLightProjectDescriptor extends LightProjectDescriptor {
    @NotNull private final ModuleType myModuleType;
    @Nullable private final Sdk mySdk;

    SimpleLightProjectDescriptor(@NotNull ModuleType moduleType, @Nullable Sdk sdk) {
      myModuleType = moduleType;
      mySdk = sdk;
    }

    @NotNull
    @Override
    public ModuleType getModuleType() {
      return myModuleType;
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

      if (!myModuleType.equals(that.myModuleType)) return false;
      return areJdksEqual(that.getSdk());
    }

    @Override
    public int hashCode() {
      return myModuleType.hashCode();
    }

    private boolean areJdksEqual(final Sdk newSdk) {
      if (mySdk == null || newSdk == null) return mySdk == newSdk;

      final String[] myUrls = mySdk.getRootProvider().getUrls(OrderRootType.CLASSES);
      final String[] newUrls = newSdk.getRootProvider().getUrls(OrderRootType.CLASSES);
      return ContainerUtil.newHashSet(myUrls).equals(ContainerUtil.newHashSet(newUrls));
    }
  }
}
