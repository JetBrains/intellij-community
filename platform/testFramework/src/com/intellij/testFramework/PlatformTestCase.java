/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaLogger;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.project.impl.TooManyProjectLeakedException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.util.*;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public abstract class PlatformTestCase extends UsefulTestCase implements DataProvider {
  private static IdeaTestApplication ourApplication;
  private static boolean ourReportedLeakedProjects;
  protected ProjectManagerEx myProjectManager;
  protected Project myProject;
  protected Module myModule;
  protected static final Collection<File> myFilesToDelete = new HashSet<>();
  protected boolean myAssertionsInTestDetected;
  protected static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.PlatformTestCase");
  public static Thread ourTestThread;
  private static TestCase ourTestCase;
  private static final long DEFAULT_TEST_TIME = 300L;
  public static long ourTestTime = DEFAULT_TEST_TIME;
  private EditorListenerTracker myEditorListenerTracker;
  private ThreadTracker myThreadTracker;

  private static boolean ourPlatformPrefixInitialized;
  private static Set<VirtualFile> ourEternallyLivingFilesCache;

  /**
   * If a temp directory is reused from some previous test run, there might be cached children in its VFS.
   * Ensure they're removed
   */
  public static void synchronizeTempDirVfs(@NotNull VirtualFile tempDir) {
    tempDir.getChildren();
    tempDir.refresh(false, true);
  }

  protected void initApplication() throws Exception {
    boolean firstTime = ourApplication == null;
    ourApplication = IdeaTestApplication.getInstance(null);
    ourApplication.setDataProvider(this);

    if (firstTime) {
      cleanPersistedVFSContent();
    }
  }

  private static final String[] PREFIX_CANDIDATES = {
    "AppCode", "CLion", "CidrCommon",
    "Python", "PyCharmCore", "Ruby", "UltimateLangXml", "Idea", "PlatformLangXml" };

  /**
   * @deprecated calling this method is no longer necessary
   */
  public static void autodetectPlatformPrefix() {
    doAutodetectPlatformPrefix();
  }

  public static void doAutodetectPlatformPrefix() {
    if (ourPlatformPrefixInitialized) {
      return;
    }
    URL resource = PlatformTestCase.class.getClassLoader().getResource("idea/ApplicationInfo.xml");
    if (resource == null) {
      for (String candidate : PREFIX_CANDIDATES) {
        resource = PlatformTestCase.class.getClassLoader().getResource("META-INF/" + candidate + "Plugin.xml");
        if (resource != null) {
          setPlatformPrefix(candidate);
          break;
        }
      }
    }
  }

  private static void cleanPersistedVFSContent() {
    ((PersistentFSImpl)PersistentFS.getInstance()).cleanPersistedContents();
  }

  @NotNull
  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return new CodeStyleSettings();
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File tempDir = new File(FileUtilRt.getTempDirectory());
    myFilesToDelete.add(tempDir);

    if (ourTestCase != null) {
      String message = "Previous test " + ourTestCase + " hasn't called tearDown(). Probably overridden without super call.";
      ourTestCase = null;
      fail(message);
    }
    IdeaLogger.ourErrorsOccurred = null;

    LOG.info(getClass().getName() + ".setUp()");

    initApplication();

    myEditorListenerTracker = new EditorListenerTracker();
    myThreadTracker = new ThreadTracker();

    setUpProject();

    storeSettings();
    ourTestCase = this;
    if (myProject != null) {
      ProjectManagerEx.getInstanceEx().openTestProject(myProject);
      CodeStyleSettingsManager.getInstance(myProject).setTemporarySettings(new CodeStyleSettings());
      InjectedLanguageManagerImpl.pushInjectors(getProject());
    }

    DocumentCommitThread.getInstance().clearQueue();
    UIUtil.dispatchAllInvocationEvents();
  }

  public final Project getProject() {
    return myProject;
  }

  public final PsiManager getPsiManager() {
    return PsiManager.getInstance(myProject);
  }

  public Module getModule() {
    return myModule;
  }

  protected void setUpProject() throws Exception {
    myProjectManager = ProjectManagerEx.getInstanceEx();
    assertNotNull("Cannot instantiate ProjectManager component", myProjectManager);

    File projectFile = getIprFile();

    myProject = doCreateProject(projectFile);
    myProjectManager.openTestProject(myProject);
    LocalFileSystem.getInstance().refreshIoFiles(myFilesToDelete);

    setUpModule();

    setUpJdk();

    LightPlatformTestCase.clearUncommittedDocuments(getProject());

    runStartupActivities();
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
  }

  protected Project doCreateProject(@NotNull File projectFile) throws Exception {
    return createProject(projectFile, getClass().getName() + "." + getName());
  }

  @NotNull
  public static Project createProject(File projectFile, String creationPlace) {
    return createProject(projectFile.getPath(), creationPlace);
  }

  @NotNull
  public static Project createProject(@NotNull String path, String creationPlace) {
    String fileName = PathUtilRt.getFileName(path);

    try {
      String projectName = FileUtilRt.getNameWithoutExtension(fileName);
      Project project = ProjectManagerEx.getInstanceEx().newProject(projectName, path, false, false);
      assert project != null;

      project.putUserData(CREATION_PLACE, creationPlace);
      return project;
    }
    catch (TooManyProjectLeakedException e) {
      if (ourReportedLeakedProjects) {
        fail("Too many projects leaked, again.");
        return null;
      }
      ourReportedLeakedProjects = true;

      StringBuilder leakers = new StringBuilder();
      leakers.append("Too many projects leaked: \n");
      for (Project project : e.getLeakedProjects()) {
        String presentableString = getCreationPlace(project);
        leakers.append(presentableString);
        leakers.append("\n");
      }

      String dumpPath = PathManager.getHomePath() + "/leakedProjects.hprof.zip";
      System.out.println("##teamcity[publishArtifacts 'leakedProjects.hprof.zip']");
      try {
        FileUtil.delete(new File(dumpPath));
        MemoryDumpHelper.captureMemoryDumpZipped(dumpPath);
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
      fail(leakers+"\nPlease see '"+dumpPath+"' for a memory dump");
      return null;
    }
  }

  @NotNull
  public static String getCreationPlace(@NotNull Project project) {
    String place = project.getUserData(CREATION_PLACE);
    Object base;
    try {
      base = project.isDisposed() ? "" : project.getBaseDir();
    }
    catch (Exception e) {
      base = " (" + e + " while getting base dir)";
    }
    return project + (place != null ? place : "") + base;
  }

  protected void runStartupActivities() {
    final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(myProject);
    startupManager.runStartupActivities();
    startupManager.startCacheUpdate();
    startupManager.runPostStartupActivities();
  }

  protected File getIprFile() throws IOException {
    File tempFile = FileUtil.createTempFile(getName(), ProjectFileType.DOT_DEFAULT_EXTENSION);
    myFilesToDelete.add(tempFile);
    return tempFile;
  }

  protected void setUpModule() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        myModule = createMainModule();
      }
    }.execute().throwException();
  }

  protected Module createMainModule() throws IOException {
    return createModule(myProject.getName());
  }

  protected Module createModule(@NonNls final String moduleName) {
    return doCreateRealModule(moduleName);
  }

  protected Module doCreateRealModule(final String moduleName) {
    return doCreateRealModuleIn(moduleName, myProject, getModuleType());
  }

  protected static Module doCreateRealModuleIn(String moduleName, final Project project, final ModuleType moduleType) {
    final VirtualFile baseDir = project.getBaseDir();
    assertNotNull(baseDir);
    final File moduleFile = new File(FileUtil.toSystemDependentName(baseDir.getPath()), moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    FileUtil.createIfDoesntExist(moduleFile);
    myFilesToDelete.add(moduleFile);
    return new WriteAction<Module>() {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
        assertNotNull(virtualFile);
        Module module = ModuleManager.getInstance(project).newModule(virtualFile.getPath(), moduleType.getId());
        module.getModuleFile();
        result.setResult(module);
      }
    }.execute().getResultObject();
  }

  protected ModuleType getModuleType() {
    return EmptyModuleType.getInstance();
  }

  public static void cleanupApplicationCaches(Project project) {
    if (project != null && !project.isDisposed()) {
      UndoManagerImpl globalInstance = (UndoManagerImpl)UndoManager.getGlobalInstance();
      if (globalInstance != null) {
        globalInstance.dropHistoryInTests();
      }
      ((UndoManagerImpl)UndoManager.getInstance(project)).dropHistoryInTests();

      ((PsiManagerImpl)PsiManager.getInstance(project)).cleanupForNextTest();
    }

    final ProjectManager projectManager = ProjectManager.getInstance();
    assert projectManager != null : "The ProjectManager is not initialized yet";
    ProjectManagerImpl projectManagerImpl = (ProjectManagerImpl)projectManager;
    if (projectManagerImpl.isDefaultProjectInitialized()) {
      Project defaultProject = projectManager.getDefaultProject();
      ((PsiManagerImpl)PsiManager.getInstance(defaultProject)).cleanupForNextTest();
    }

    ((FileBasedIndexImpl) FileBasedIndex.getInstance()).cleanupForNextTest();

    LocalFileSystemImpl localFileSystem = (LocalFileSystemImpl)LocalFileSystem.getInstance();
    if (localFileSystem != null) {
      localFileSystem.cleanupForNextTest();
    }

  }

  private static Set<VirtualFile> eternallyLivingFiles() {
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

  public static void addSurvivingFiles(@NotNull Collection<VirtualFile> files) {
    for (VirtualFile each : files) {
      registerSurvivor(eternallyLivingFiles(), each);
    }
  }

  private static void registerSurvivor(Set<VirtualFile> survivors, VirtualFile file) {
    addSubTree(file, survivors);
    while (file != null && survivors.add(file)) {
      file = file.getParent();
    }
  }

  private static void addSubTree(VirtualFile root, Set<VirtualFile> to) {
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
    List<Throwable> exceptions = new SmartList<>();
    Project project = myProject;
    if (project != null) {
      try {
        LightPlatformTestCase.doTearDown(project, ourApplication, false, exceptions);
      }
      catch (Throwable e) {
        exceptions.add(e);
      }

      disposeProject(exceptions);
    }

    try {
      checkForSettingsDamage(exceptions);
    }
    catch (Throwable e) {
      exceptions.add(e);
    }
    try {
      if (project != null) {
        try {
          InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);
        }
        catch (AssertionError e) {
          exceptions.add(e);
        }
      }
      try {
        for (final File fileToDelete : myFilesToDelete) {
          delete(fileToDelete);
        }
        LocalFileSystem.getInstance().refreshIoFiles(myFilesToDelete);
      }
      catch (Throwable e) {
        exceptions.add(e);
      }

      if (!myAssertionsInTestDetected) {
        if (IdeaLogger.ourErrorsOccurred != null) {
          exceptions.add(IdeaLogger.ourErrorsOccurred);
        }
      }

      try {
        super.tearDown();
      }
      catch (Throwable e) {
        exceptions.add(e);
      }

      try {
        if (myEditorListenerTracker != null) {
          myEditorListenerTracker.checkListenersLeak();
        }
      }
      catch (AssertionError error) {
        exceptions.add(error);
      }
      try {
        if (myThreadTracker != null) {
          myThreadTracker.checkLeak();
        }
      }
      catch (AssertionError error) {
        exceptions.add(error);
      }
      try {
        LightPlatformTestCase.checkEditorsReleased(exceptions);
      }
      catch (Throwable error) {
        exceptions.add(error);
      }
    }
    finally {
      myProjectManager = null;
      myProject = null;
      myModule = null;
      myFilesToDelete.clear();
      myEditorListenerTracker = null;
      myThreadTracker = null;
      ourTestCase = null;

      CompoundRuntimeException.throwIfNotEmpty(exceptions);
    }
  }

  private void disposeProject(@NotNull List<Throwable> exceptions) {
    try {
      DocumentCommitThread.getInstance().clearQueue();
      // sometimes SwingUtilities maybe confused about EDT at this point
      if (SwingUtilities.isEventDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    catch (Throwable e) {
      exceptions.add(e);
    }

    Project project = myProject;
    if (project == null) {
      return;
    }

    closeAndDisposeProjectAndCheckThatNoOpenProjects(project, exceptions);
    myProject = null;
  }

  public static void closeAndDisposeProjectAndCheckThatNoOpenProjects(@NotNull final Project projectToClose, @NotNull final List<Throwable> exceptions) {
    try {
      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      if (projectManager instanceof ProjectManagerImpl) {
        for (Project project : projectManager.closeTestProject(projectToClose)) {
          exceptions.add(new IllegalStateException("Test project is not disposed: " + project + ";\n created in: " + getCreationPlace(project)));
          try {
            ((ProjectManagerImpl)projectManager).closeProject(project, false, true, false);
          }
          catch (Throwable e) {
            exceptions.add(e);
          }
        }
      }
    }
    catch (Throwable e) {
      exceptions.add(e);
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          Disposer.dispose(projectToClose);
        }
        catch (Throwable e) {
          exceptions.add(e);
        }
      });
    }
  }

  protected void resetAllFields() {
    resetClassFields(getClass());
  }

  @Override
  protected final <T extends Disposable> T disposeOnTearDown(T disposable) {
    Disposer.register(myProject, disposable);
    return disposable;
  }

  private void resetClassFields(final Class<?> aClass) {
    try {
      clearDeclaredFields(this, aClass);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }

    if (aClass == PlatformTestCase.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  private String getFullName() {
    return getClass().getName() + "." + getName();
  }

  private void delete(File file) {
    boolean b = FileUtil.delete(file);
    if (!b && file.exists() && !myAssertionsInTestDetected) {
      fail("Can't delete " + file.getAbsolutePath() + " in " + getFullName());
    }
  }

  protected void setUpJdk() {
    //final ProjectJdkEx jdk = ProjectJdkUtil.getDefaultJdk("java 1.4");
    final Sdk jdk = getTestProjectJdk();
//    ProjectJdkImpl jdk = ProjectJdkTable.getInstance().addJdk(defaultJdk);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ModuleRootModificationUtil.setModuleSdk(module, jdk);
    }
  }

  @Nullable
  protected Sdk getTestProjectJdk() {
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
        SwingUtilities.invokeAndWait(() -> {
          cleanupApplicationCaches(getProject());
          resetAllFields();
        });
      }
      catch (Throwable e) {
        // Ignore
      }
    }
  }

  private void runBareImpl() throws Throwable {
    ThrowableRunnable<Throwable> runnable = () -> {
      ourTestThread = Thread.currentThread();
      ourTestTime = DEFAULT_TEST_TIME;
      try {
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
      }
      finally {
        ourTestThread = null;
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

  protected void runBareRunnable(ThrowableRunnable<Throwable> runnable) throws Throwable {
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
  protected void invokeTestRunnable(@NotNull final Runnable runnable) throws Exception {
    final Exception[] e = new Exception[1];
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
        e[0] = e1;
      }
    };

    if (annotatedWith(WrapInCommand.class)) {
      CommandProcessor.getInstance().executeCommand(myProject, runnable1, "", null);
    }
    else {
      runnable1.run();
    }

    if (e[0] != null) {
      throw e[0];
    }
  }

  @Override
  public Object getData(String dataId) {
    return myProject == null ? null : new TestDataProvider(myProject).getData(dataId);
  }

  public static File createTempDir(@NonNls final String prefix) throws IOException {
    return createTempDir(prefix, true);
  }

  public static File createTempDir(@NonNls final String prefix, final boolean refresh) throws IOException {
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

  protected File createTempDirectory() throws IOException {
    return createTempDir(getTestName(true));
  }

  protected File createTempDirectory(final boolean refresh) throws IOException {
    return createTempDir(getTestName(true), refresh);
  }

  @NotNull
  protected File createTempFile(@NotNull String name, @Nullable String text) throws IOException {
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

  public static void setContentOnDisk(@NotNull File file, byte[] bom, @NotNull String content, @NotNull Charset charset) throws IOException {
    FileOutputStream stream = new FileOutputStream(file);
    if (bom != null) {
      stream.write(bom);
    }
    try (OutputStreamWriter writer = new OutputStreamWriter(stream, charset)) {
      writer.write(content);
    }
  }

  public static VirtualFile createTempFile(@NonNls @NotNull String ext, @Nullable byte[] bom, @NonNls @NotNull String content, @NotNull Charset charset) throws IOException {
    File temp = FileUtil.createTempFile("copy", "." + ext);
    setContentOnDisk(temp, bom, content, charset);

    myFilesToDelete.add(temp);
    final VirtualFile file = getVirtualFile(temp);
    assert file != null : temp;
    return file;
  }

  @Nullable
  protected PsiFile getPsiFile(final Document document) {
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
  }

  /**
   * @deprecated calling this method is no longer necessary
   */
  public static void initPlatformLangPrefix() {
  }

  /**
   * This is the main point to set up your platform prefix. This allows you to use some sub-set of
   * core plugin descriptors to make initialization faster (e.g. for running tests in classpath of the module where the test is located).
   * It is calculated by some marker class presence in classpath.
   * Note that it applies NEGATIVE logic for detection: prefix will be set if only marker class
   * is NOT present in classpath.
   * Also, only the very FIRST call to this method will take effect.
   *
   * @param classToTest marker class qualified name
   * @param prefix platform prefix to be set up if marker class not found in classpath.
   * @deprecated calling this method is no longer necessary
   */
  public static void initPlatformPrefix(String classToTest, String prefix) {
    if (!ourPlatformPrefixInitialized) {
      ourPlatformPrefixInitialized = true;
      boolean isUltimate = true;
      try {
        PlatformTestCase.class.getClassLoader().loadClass(classToTest);
      }
      catch (ClassNotFoundException e) {
        isUltimate = false;
      }
      if (!isUltimate) {
        setPlatformPrefix(prefix);
      }
    }
  }

  private static void setPlatformPrefix(String prefix) {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, prefix);
    ourPlatformPrefixInitialized = true;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface WrapInCommand {
  }

  protected static VirtualFile createChildData(@NotNull final VirtualFile dir, @NotNull @NonNls final String name) {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        result.setResult(dir.createChildData(null, name));
      }
    }.execute().throwException().getResultObject();
  }

  protected static VirtualFile createChildDirectory(@NotNull final VirtualFile dir, @NotNull @NonNls final String name) {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        result.setResult(dir.createChildDirectory(null, name));
      }
    }.execute().throwException().getResultObject();
  }

  protected static void rename(@NotNull final VirtualFile vFile1, @NotNull final String newName) {
    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        vFile1.rename(this, newName);
      }
    }.execute().throwException();
  }

  protected static void delete(@NotNull final VirtualFile vFile1) {
    VfsTestUtil.deleteFile(vFile1);
  }

  public static void move(@NotNull final VirtualFile vFile1, @NotNull final VirtualFile newFile) {
    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        vFile1.move(this, newFile);
      }
    }.execute().throwException();
  }

  protected static VirtualFile copy(@NotNull final VirtualFile file, @NotNull final VirtualFile newParent, @NotNull final String copyName) {
    final VirtualFile[] copy = new VirtualFile[1];

    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        copy[0] = file.copy(this, newParent, copyName);
      }
    }.execute().throwException();
    return copy[0];
  }

  public static void copyDirContentsTo(@NotNull final VirtualFile vTestRoot, @NotNull final VirtualFile toDir) {
    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        for (VirtualFile file : vTestRoot.getChildren()) {
          VfsUtil.copy(this, file, toDir);
        }
      }
    }.execute().throwException();
  }

  public static void setFileText(@NotNull final VirtualFile file, @NotNull final String text) throws IOException {
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        VfsUtil.saveText(file, text);
      }
    }.execute().throwException();
  }

  public static void setBinaryContent(@NotNull final VirtualFile file, @NotNull final byte[] content) {
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        file.setBinaryContent(content);
      }
    }.execute().throwException();
  }
  public static void setBinaryContent(@NotNull final VirtualFile file, @NotNull final byte[] content, final long newModificationStamp, final long newTimeStamp, final Object requestor) {
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        file.setBinaryContent(content,newModificationStamp, newTimeStamp,requestor);
      }
    }.execute().throwException();
  }
}
