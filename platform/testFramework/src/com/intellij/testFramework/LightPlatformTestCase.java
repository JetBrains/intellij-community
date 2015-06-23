/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
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
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.Consumer;
import com.intellij.util.GCUtil;import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexableFileSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel;

/**
 * @author yole
 */
public abstract class LightPlatformTestCase extends UsefulTestCase implements DataProvider {
  @NonNls public static final String PROFILE = "Configurable";

  @NonNls private static final String LIGHT_PROJECT_MARK = "Light project: ";

  private static IdeaTestApplication ourApplication;
  protected static Project ourProject;
  private static Module ourModule;
  private static PsiManager ourPsiManager;
  private static boolean ourAssertionsInTestDetected;
  private static VirtualFile ourSourceRoot;
  private static TestCase ourTestCase;
  public static Thread ourTestThread;
  private static LightProjectDescriptor ourProjectDescriptor;
  private static boolean ourHaveShutdownHook;

  private ThreadTracker myThreadTracker;

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
  public static PsiManager getPsiManager() {
    if (ourPsiManager == null) {
      ourPsiManager = PsiManager.getInstance(ourProject);
    }
    return ourPsiManager;
  }

  public static IdeaTestApplication initApplication() {
    ourApplication = IdeaTestApplication.getInstance(null);
    return ourApplication;
  }

  @TestOnly
  public static void disposeApplication() {
    if (ourApplication != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(ourApplication);
        }
      });

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

  private void resetClassFields(final Class<?> aClass) {
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

  public static boolean isLight(Project project) {
    String creationPlace = project.getUserData(CREATION_PLACE);
    return creationPlace != null && StringUtil.startsWith(creationPlace, LIGHT_PROJECT_MARK);
  }

  private static void initProject(@NotNull final LightProjectDescriptor descriptor) throws Exception {
    ourProjectDescriptor = descriptor;

    final File projectFile = FileUtil.createTempFile("light_temp_", ProjectFileType.DOT_DEFAULT_EXTENSION);

    new WriteCommandAction.Simple(null) {
      @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
      @Override
      protected void run() throws Throwable {
        if (ourProject != null) {
          closeAndDeleteProject();
        }
        else {
          cleanPersistedVFSContent();
        }

        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectFile);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new Throwable(projectFile.getPath()).printStackTrace(new PrintStream(buffer));

        ourProject = PlatformTestCase.createProject(projectFile, LIGHT_PROJECT_MARK + buffer);
        ourPathToKeep = projectFile.getPath();
        if (!ourHaveShutdownHook) {
          ourHaveShutdownHook = true;
          registerShutdownHook();
        }
        ourPsiManager = null;
        ourModule = createMainModule(descriptor.getModuleType());

        VirtualFile dummyRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
        assert dummyRoot != null;
        dummyRoot.refresh(false, false);

        try {
          ourSourceRoot = dummyRoot.createChildDirectory(this, "src");
          cleanSourceRoot();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        FileBasedIndex.getInstance().registerIndexableSet(new IndexableFileSet() {
          @Override
          public boolean isInSet(@NotNull final VirtualFile file) {
            return ourSourceRoot != null &&
                   file.getFileSystem() == ourSourceRoot.getFileSystem() &&
                   ourProject != null &&
                   ourProject.isOpen();
          }

          @Override
          public void iterateIndexableFilesIn(@NotNull final VirtualFile file, @NotNull final ContentIterator iterator) {
            VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
              @Override
              public boolean visitFile(@NotNull VirtualFile file) {
                iterator.processFile(file);
                return true;
              }
            });
          }
        }, null);

        updateModel(ourModule, new Consumer<ModifiableRootModel>() {
          @Override
          public void consume(ModifiableRootModel model) {
            final Sdk sdk = descriptor.getSdk();
            if (sdk != null) {
              model.setSdk(sdk);
            }

            ContentEntry contentEntry = model.addContentEntry(ourSourceRoot);
            contentEntry.addSourceFolder(ourSourceRoot, false);

            descriptor.configureModule(ourModule, model, contentEntry);
          }
        });
      }

      private void cleanSourceRoot() throws IOException {
        TempFileSystem tempFs = (TempFileSystem)ourSourceRoot.getFileSystem();
        for (VirtualFile child : ourSourceRoot.getChildren()) {
          if (!tempFs.exists(child)) {
            tempFs.createChildFile(this, ourSourceRoot, child.getName());
          }
          child.delete(this);
        }
      }
    }.execute().throwException();

    // project creation may make a lot of pointers, do not regard them as leak
    ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).storePointers();
  }

  protected static Module createMainModule(final ModuleType moduleType) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleManager.getInstance(ourProject).newModule("light_idea_test_case.iml", moduleType.getId());
      }
    });
  }

  /**
   * @return The only source root
   */
  public static VirtualFile getSourceRoot() {
    return ourSourceRoot;
  }

  @Override
  protected void setUp() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          LightPlatformTestCase.super.setUp();
          initApplication();
          ApplicationInfoImpl.setInPerformanceTest(isPerformanceTest());

          ourApplication.setDataProvider(LightPlatformTestCase.this);
          LightProjectDescriptor descriptor = new SimpleLightProjectDescriptor(getModuleType(), getProjectJDK());
          doSetup(descriptor, configureLocalInspectionTools(), getTestRootDisposable());
          InjectedLanguageManagerImpl.pushInjectors(getProject());

          storeSettings();

          myThreadTracker = new ThreadTracker();
          ModuleRootManager.getInstance(ourModule).orderEntries().getAllLibrariesAndSdkClassesRoots();
          VirtualFilePointerManagerImpl filePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
          filePointerManager.storePointers();
        }
        catch (RuntimeException e) {
          throw e;
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public static void doSetup(@NotNull LightProjectDescriptor descriptor,
                             @NotNull LocalInspectionTool[] localInspectionTools,
                             @NotNull Disposable parentDisposable) throws Exception {
    assertNull("Previous test " + ourTestCase + " hasn't called tearDown(). Probably overridden without super call.", ourTestCase);
    IdeaLogger.ourErrorsOccurred = null;
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (ourProject == null || ourProjectDescriptor == null || !ourProjectDescriptor.equals(descriptor)) {
      initProject(descriptor);
    }

    ProjectManagerEx projectManagerEx = ProjectManagerEx.getInstanceEx();
    projectManagerEx.openTestProject(ourProject);

    MessageBusConnection connection = ourProject.getMessageBus().connect(parentDisposable);
    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleAdded(@NotNull Project project, @NotNull Module module) {
        fail("Adding modules is not permitted in LightIdeaTestCase.");
      }
    });

    clearUncommittedDocuments(getProject());

    CodeInsightTestFixtureImpl.configureInspections(localInspectionTools, getProject(),
                                                    Collections.<String>emptyList(), parentDisposable);

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
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ((FileDocumentManagerImpl)manager).dropAllUnsavedDocuments();
        }
      });

      assertEmpty("There are unsaved documents", Arrays.asList(unsavedDocuments));
    }
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
  }

  // todo: use Class<? extends InspectionProfileEntry> once on Java 7
  protected void enableInspectionTools(@NotNull Class<?>... classes) {
    final InspectionProfileEntry[] tools = new InspectionProfileEntry[classes.length];

    final List<InspectionEP> eps = ContainerUtil.newArrayList();
    ContainerUtil.addAll(eps, Extensions.getExtensions(LocalInspectionEP.LOCAL_INSPECTION));
    ContainerUtil.addAll(eps, Extensions.getExtensions(InspectionEP.GLOBAL_INSPECTION));

    next:
    for (int i = 0; i < classes.length; i++) {
      for (InspectionEP ep : eps) {
        if (classes[i].getName().equals(ep.implementationClass)) {
          tools[i] = ep.instantiateTool();
          continue next;
        }
      }
      throw new IllegalArgumentException("Unable to find extension point for " + classes[i].getName());
    }

    enableInspectionTools(tools);
  }

  protected void enableInspectionTools(@NotNull InspectionProfileEntry... tools) {
    for (InspectionProfileEntry tool : tools) {
      enableInspectionTool(tool);
    }
  }

  protected void enableInspectionTool(@NotNull InspectionToolWrapper toolWrapper) {
    enableInspectionTool(getProject(), toolWrapper);
  }
  protected void enableInspectionTool(@NotNull InspectionProfileEntry tool) {
    InspectionToolWrapper toolWrapper = InspectionToolRegistrar.wrapTool(tool);
    enableInspectionTool(getProject(), toolWrapper);
  }

  public static void enableInspectionTool(@NotNull final Project project, @NotNull final InspectionToolWrapper toolWrapper) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final String shortName = toolWrapper.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      HighlightDisplayKey.register(shortName, toolWrapper.getDisplayName(), toolWrapper.getID());
    }
    InspectionProfileImpl.initAndDo(new Computable() {
      @Override
      public Object compute() {
        InspectionProfileImpl impl = (InspectionProfileImpl)profile;
        InspectionToolWrapper existingWrapper = impl.getInspectionTool(shortName, project);
        if (existingWrapper == null || existingWrapper.isInitialized() != toolWrapper.isInitialized() || toolWrapper.isInitialized() && toolWrapper.getTool() != existingWrapper.getTool()) {
          impl.addTool(project, toolWrapper, new THashMap<String, List<String>>());
        }
        impl.enableTool(shortName, project);
        return null;
      }
    });
  }

  @NotNull
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return LocalInspectionTool.EMPTY_ARRAY;
  }

  @Override
  protected void tearDown() throws Exception {
    Project project = getProject();
    CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    CompositeException damage = checkForSettingsDamage();
    VirtualFilePointerManagerImpl filePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    doTearDown(project, ourApplication, true);

    try {
      super.tearDown();
    }
    finally {
      myThreadTracker.checkLeak();
      InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);
      filePointerManager.assertPointersAreDisposed();
    }
    damage.throwIfNotEmpty();
  }

  public static void doTearDown(@NotNull final Project project, IdeaTestApplication application, boolean checkForEditors) throws Exception {
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
    DocumentCommitThread.getInstance().clearQueue();
    CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();
    checkAllTimersAreDisposed();
    UsefulTestCase.doPostponedFormatting(project);

    LookupManager lookupManager = LookupManager.getInstance(project);
    if (lookupManager != null) {
      lookupManager.hideActiveLookup();
    }
    ((StartupManagerImpl)StartupManager.getInstance(project)).prepareForNextTest();
    InspectionProfileManager.getInstance().deleteProfile(PROFILE);
    assertNotNull("Application components damaged", ProjectManager.getInstance());

    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        if (ourSourceRoot != null) {
          try {
            final VirtualFile[] children = ourSourceRoot.getChildren();
            for (VirtualFile child : children) {
              child.delete(this);
            }
          }
          catch (IOException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
          }
        }
        EncodingManager encodingManager = EncodingManager.getInstance();
        if (encodingManager instanceof EncodingManagerImpl) ((EncodingManagerImpl)encodingManager).clearDocumentQueue();

        FileDocumentManager manager = FileDocumentManager.getInstance();

        ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance()); // Flush postponed formatting if any.
        manager.saveAllDocuments();
        if (manager instanceof FileDocumentManagerImpl) {
          ((FileDocumentManagerImpl)manager).dropAllUnsavedDocuments();
        }
      }
    }.execute().throwException();

    assertFalse(PsiManager.getInstance(project).isDisposed());
    if (!ourAssertionsInTestDetected) {
      if (IdeaLogger.ourErrorsOccurred != null) {
        throw IdeaLogger.ourErrorsOccurred;
      }
    }
    PsiDocumentManagerImpl documentManager = clearUncommittedDocuments(project);
    ((HintManagerImpl)HintManager.getInstance()).cleanup();
    DocumentCommitThread.getInstance().clearQueue();

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ((UndoManagerImpl)UndoManager.getGlobalInstance()).dropHistoryInTests();
        ((UndoManagerImpl)UndoManager.getInstance(project)).dropHistoryInTests();

        UIUtil.dispatchAllInvocationEvents();
      }
    });

    TemplateDataLanguageMappings.getInstance(project).cleanupForNextTest();

    ProjectManagerEx.getInstanceEx().closeTestProject(project);
    application.setDataProvider(null);
    ourTestCase = null;
    ((PsiManagerImpl)PsiManager.getInstance(project)).cleanupForNextTest();

    CompletionProgressIndicator.cleanupForNextTest();

    if (checkForEditors) {
      checkEditorsReleased();
    }
    documentManager.clearUncommittedDocuments();
    
    if (ourTestCount++ % 100 == 0) {
      // some tests are written in Groovy, and running all of them may result in some 40M of memory wasted on bean infos
      // so let's clear the cache every now and then to ensure it doesn't grow too large
      GCUtil.tryClearBeanInfoCache();
    }
  }
  
  private static int ourTestCount = 0;

  public static PsiDocumentManagerImpl clearUncommittedDocuments(@NotNull Project project) {
    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
    documentManager.clearUncommittedDocuments();

    ProjectManagerImpl projectManager = (ProjectManagerImpl)ProjectManager.getInstance();
    if (projectManager.isDefaultProjectInitialized()) {
      Project defaultProject = projectManager.getDefaultProject();
      ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(defaultProject)).clearUncommittedDocuments();
    }
    return documentManager;
  }

  public static void checkEditorsReleased() throws Exception {
    CompositeException result = new CompositeException();
    final Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    if (allEditors.length > 0) {
      for (Editor editor : allEditors) {
        try {
          EditorFactoryImpl.throwNotReleasedError(editor);
        }
        catch (Throwable e) {
          result.add(e);
        }
        finally {
          EditorFactory.getInstance().releaseEditor(editor);
        }
      }
      try {
        ((EditorImpl)allEditors[0]).throwDisposalError("Unreleased editors: " + allEditors.length);
      }
      catch (Throwable e) {
        e.printStackTrace();
        result.add(e);
      }
    }
    if (!result.isEmpty()) throw result;
  }

  @Override
  public final void runBare() throws Throwable {
    if (!shouldRunTest()) {
      return;
    }

    final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();

    replaceIdeEventQueueSafely();
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          ourTestThread = Thread.currentThread();
          startRunAndTear();
        }
        catch (Throwable e) {
          throwable.set(e);
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
            e.printStackTrace();
          }
        }
      }
    });

    if (throwable.get() != null) {
      throw throwable.get();
    }

    // just to make sure all deferred Runnables to finish
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());

    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }
  }

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
   * @throws IncorrectOperationException
   *
   */
  protected static PsiFile createFile(@NonNls String fileName, @NonNls String text) throws IncorrectOperationException {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject())
      .createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), true, false);
  }

  protected static PsiFile createLightFile(@NonNls String fileName, String text) throws IncorrectOperationException {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject())
      .createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), false, false);
  }

  /**
   * Convenient conversion of testSomeTest -> someTest | SomeTest where testSomeTest is the name of current test.
   *
   * @param lowercaseFirstLetter - whether first letter after test should be lowercased.
   */
  @Override
  protected String getTestName(boolean lowercaseFirstLetter) {
    String name = getName();
    assertTrue("Test name should start with 'test': " + name, name.startsWith("test"));
    name = name.substring("test".length());
    if (!name.isEmpty() && lowercaseFirstLetter && !UsefulTestCase.isAllUppercaseName(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  protected static void commitDocument(final Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
  }

  protected static void commitAllDocuments() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return new CodeStyleSettings();
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  protected static Document getDocument(final PsiFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
  public static synchronized void closeAndDeleteProject() {
    if (ourProject != null) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();

      if (!ourProject.isDisposed()) {
        VirtualFile projectFile = ((ProjectEx)ourProject).getStateStore().getProjectFile();
        File ioFile = projectFile == null ? null : VfsUtilCore.virtualToIoFile(projectFile);
        Disposer.dispose(ourProject);
        if (ioFile != null) {
          File dir = ioFile.getParentFile();
          if (dir.getName().startsWith(UsefulTestCase.TEMP_DIR_MARKER)) {
            FileUtil.delete(dir);
          }
          else {
            FileUtil.delete(ioFile);
          }
        }
      }

      ProjectManagerEx.getInstanceEx().closeAndDispose(ourProject);

      ourProject = null;
      ourPathToKeep = null;
    }
  }

  private static void registerShutdownHook() {
    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        ShutDownTracker.invokeAndWait(true, true, new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                closeAndDeleteProject();
              }
            });
          }
        });
      }
    });
  }

  private static class SimpleLightProjectDescriptor implements LightProjectDescriptor {
    private final ModuleType myModuleType;
    private final Sdk mySdk;

    SimpleLightProjectDescriptor(ModuleType moduleType, Sdk sdk) {
      myModuleType = moduleType;
      mySdk = sdk;
    }

    @Override
    public ModuleType getModuleType() {
      return myModuleType;
    }

    @Override
    public Sdk getSdk() {
      return mySdk;
    }

    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SimpleLightProjectDescriptor that = (SimpleLightProjectDescriptor)o;

      if (myModuleType != null ? !myModuleType.equals(that.myModuleType) : that.myModuleType != null) return false;
      return areJdksEqual(that.getSdk());
    }

    @Override
    public int hashCode() {
      return myModuleType != null ? myModuleType.hashCode() : 0;
    }

    private boolean areJdksEqual(final Sdk newSdk) {
      if (mySdk == null || newSdk == null) return mySdk == newSdk;

      final String[] myUrls = mySdk.getRootProvider().getUrls(OrderRootType.CLASSES);
      final String[] newUrls = newSdk.getRootProvider().getUrls(OrderRootType.CLASSES);
      return ContainerUtil.newHashSet(myUrls).equals(ContainerUtil.newHashSet(newUrls));
    }
  }
}
