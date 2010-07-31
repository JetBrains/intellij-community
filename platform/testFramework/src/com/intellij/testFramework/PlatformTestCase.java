/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaLogger;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.TooManyProjectLeakedException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.PatchedWeakReference;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.IndexedRootsProvider;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public abstract class PlatformTestCase extends UsefulTestCase implements DataProvider {
  protected static IdeaTestApplication ourApplication;
  protected boolean myRunCommandForTest = false;
  protected ProjectManagerEx myProjectManager;
  protected Project myProject;
  protected Module myModule;
  protected static final Collection<File> myFilesToDelete = new HashSet<File>();
  protected boolean myAssertionsInTestDetected;
  protected static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.PlatformTestCase");
  public static Thread ourTestThread;
  private static TestCase ourTestCase = null;
  public static final long DEFAULT_TEST_TIME = 300L;
  public static long ourTestTime = DEFAULT_TEST_TIME;
  private static final String ourOriginalTempDir = FileUtil.getTempDirectory();
  private EditorListenerTracker myEditorListenerTracker;
  private String myTempDirPath;
  private ThreadTracker myThreadTracker;

  protected static boolean ourPlatformPrefixInitialized;
  private static Set<VirtualFile> ourEternallyLivingFiles;

  static {
    Logger.setFactory(TestLoggerFactory.getInstance());
  }

  protected static long getTimeRequired() {
    return DEFAULT_TEST_TIME;
  }

  @Nullable
  protected String getApplicationConfigDirPath() throws Exception {
    return null;
  }

  protected void initApplication() throws Exception {
    boolean firstTime = ourApplication == null;
    ourApplication = IdeaTestApplication.getInstance(getApplicationConfigDirPath());
    ourApplication.setDataProvider(this);

    if (firstTime) {
      cleanPersistedVFSContent();
    }
  }

  private static void cleanPersistedVFSContent() {
    ((PersistentFS)ManagingFS.getInstance()).cleanPersistedContents();
  }

  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  protected void setUp() throws Exception {
    super.setUp();
    if (ourTestCase != null) {
      String message = "Previous test " + ourTestCase +
                       " hasn't called tearDown(). Probably overriden without super call.";
      ourTestCase = null;
      fail(message);
    }
    IdeaLogger.ourErrorsOccurred = null;

    LOG.info(getClass().getName() + ".setUp()");

    myTempDirPath = ourOriginalTempDir + "/"+getTestName(true) + "/";
    setTmpDir(myTempDirPath);
    new File(myTempDirPath).mkdir();

    initApplication();

    myEditorListenerTracker = new EditorListenerTracker();
    myThreadTracker = new ThreadTracker();

    setUpProject();
    storeSettings();
    ourTestCase = this;
  }

  public Project getProject() {
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
    LocalFileSystem.getInstance().refreshIoFiles(myFilesToDelete);

    myProject = createProject(projectFile, getClass().getName() + "." + getName());

    setUpModule();

    setUpJdk();

    ProjectManagerEx.getInstanceEx().setCurrentTestProject(myProject);

    runStartupActivities();
  }

  @Nullable
  public static Project createProject(File projectFile, String creationPlace) {
    try {
      Project project =
        ProjectManagerEx.getInstanceEx().newProject(FileUtil.getNameWithoutExtension(projectFile), projectFile.getPath(), false, false);
      assert project != null;

      project.putUserData(CREATION_PLACE, creationPlace);
      return project;
    }
    catch (TooManyProjectLeakedException e) {
      StringBuilder leakers = new StringBuilder();
      leakers.append("Too many projects leaked: \n");
      for (Project project : e.getLeakedProjects()) {
        String place = project.getUserData(CREATION_PLACE);
        leakers.append(place != null ? place : project.getBaseDir());
        leakers.append("\n");
      }

      fail(leakers.toString());
      return null;
    }
  }

  protected void runStartupActivities() {
    ((StartupManagerImpl)StartupManager.getInstance(myProject)).runStartupActivities();
    ((StartupManagerImpl)StartupManager.getInstance(myProject)).runPostStartupActivities();
  }

  protected File getIprFile() throws IOException {
    File tempFile = FileUtil.createTempFile("temp_" + getName(), ProjectFileType.DOT_DEFAULT_EXTENSION);
    myFilesToDelete.add(tempFile);
    return tempFile;
  }

  protected void setUpModule() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          myModule = createMainModule();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  protected Module createMainModule() throws IOException {
    return createModule(myProject.getName());
  }

  protected Module createModule(@NonNls final String moduleName) {
    return doCreateRealModule(moduleName);
  }

  protected Module doCreateRealModule(final String moduleName) {
    final VirtualFile baseDir = myProject.getBaseDir();
    assertNotNull(baseDir);
    final File moduleFile = new File(baseDir.getPath().replace('/', File.separatorChar),
                                     moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    FileUtil.createIfDoesntExist(moduleFile);
    myFilesToDelete.add(moduleFile);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
    Module module = ModuleManager.getInstance(myProject).newModule(virtualFile.getPath(), getModuleType());
    module.getModuleFile();
    return module;
  }

  protected ModuleType getModuleType() {
    return EmptyModuleType.getInstance();
  }

  public static void cleanupApplicationCaches(Project project) {
    if (project != null) {
      ((UndoManagerImpl)UndoManager.getInstance(project)).dropHistoryInTests();
      ((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().cleanupForNextTest();
    }

    try {
      LocalFileSystemImpl localFileSystem = (LocalFileSystemImpl)LocalFileSystem.getInstance();
      if (localFileSystem != null) {
        localFileSystem.cleanupForNextTest(eternallyLivingFiles());
      }
    }
    catch (IOException e) {
      // ignore
    }

    LocalHistoryImpl.getInstanceImpl().cleanupForNextTest();

    VirtualFilePointerManagerImpl virtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    if (virtualFilePointerManager != null) {
      virtualFilePointerManager.cleanupForNextTest();
    }
    PatchedWeakReference.clearAll();
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

  private static Set<VirtualFile> eternallyLivingFiles() {
    if (ourEternallyLivingFiles != null) {
      return ourEternallyLivingFiles;
    }

    Set<VirtualFile> survivors = new HashSet<VirtualFile>();

    for (IndexedRootsProvider provider : IndexableSetContributor.EP_NAME.getExtensions()) {
      for (VirtualFile file : IndexableSetContributor.getRootsToIndex(provider)) {
        addSubTree(file, survivors);
        while (file != null && survivors.add(file)) {
          file = file.getParent();
        }
      }
    }

    ourEternallyLivingFiles = survivors;
    return survivors;
  }

  protected void tearDown() throws Exception {
    LightPlatformTestCase.doTearDown(getProject(), ourApplication, false);

    try {
      checkForSettingsDamage();

      try {
        disposeProject();

        for (final File fileToDelete : myFilesToDelete) {
          delete(fileToDelete);
        }

        FileUtil.asyncDelete(new File(myTempDirPath));

        setTmpDir(ourOriginalTempDir);

        if (!myAssertionsInTestDetected) {
          if (IdeaLogger.ourErrorsOccurred != null) {
            throw IdeaLogger.ourErrorsOccurred;
          }
          assertNull("Logger errors occurred in " + getFullName(), IdeaLogger.ourErrorsOccurred);
        }
      }
      finally {
        ourTestCase = null;
      }

      super.tearDown();

      //cleanTheWorld();
      myEditorListenerTracker.checkListenersLeak();
      myThreadTracker.checkLeak();
      LightPlatformTestCase.checkEditorsReleased();
    }
    finally {
      myProjectManager = null;
      myProject = null;
      myModule = null;
      myFilesToDelete.clear();
    }
  }

  private void disposeProject() {
    if (myProject != null) {
      Disposer.dispose(myProject);
      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      if (projectManager != null) {
        projectManager.setCurrentTestProject(null);
      }
    }
  }

  protected void resetAllFields() {
    resetClassFields(getClass());
  }

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

  protected void simulateProjectOpen() {
    ModuleManagerImpl mm = (ModuleManagerImpl)ModuleManager.getInstance(myProject);
    StartupManagerImpl sm = (StartupManagerImpl)StartupManager.getInstance(myProject);

    mm.projectOpened();
    setUpJdk();
    sm.runStartupActivities();
    // extra init for libraries
    sm.runPostStartupActivities();
  }

  protected void setUpJdk() {
    //final ProjectJdkEx jdk = ProjectJdkUtil.getDefaultJdk("java 1.4");
    final Sdk jdk = getTestProjectJdk();
//    ProjectJdkImpl jdk = ProjectJdkTable.getInstance().addJdk(defaultJdk);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final ModifiableRootModel rootModel = rootManager.getModifiableModel();
          rootModel.setSdk(jdk);
          rootModel.commit();
        }
      });
    }
  }

  protected Sdk getTestProjectJdk() {
    return null;
  }

  public void runBare() throws Throwable {
    try {
      runBareImpl();
    }
    finally {
      try {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            cleanupApplicationCaches(getProject());
            resetAllFields();
          }
        }, ModalityState.NON_MODAL);
      }
      catch (Throwable e) {
        // Ignore
      }
    }
  }

  private void runBareImpl() throws Throwable {
    final Throwable[] throwables = new Throwable[1];
    Runnable runnable = new Runnable() {
      public void run() {
        ourTestThread = Thread.currentThread();
        ourTestTime = getTimeRequired();
        try {
          try {
            setUp();
          }
          catch (Throwable e) {
            disposeProject();
            throw e;
          }
          try {
            myAssertionsInTestDetected = true;
            runTest();
            myAssertionsInTestDetected = false;
          }
          finally {
            try {
              tearDown();
            }
            catch (Throwable th) {
              th.printStackTrace();
            }
          }
        }
        catch (Throwable throwable) {
          throwables[0] = throwable;
        }
        finally {
          ourTestThread = null;
        }
      }
    };

    runBareRunnable(runnable);

    if (IdeaLogger.ourErrorsOccurred != null) {
      throw IdeaLogger.ourErrorsOccurred;
    }

    if (throwables[0] != null) {
      throw throwables[0];
    }

    // just to make sure all deffered Runnable's to finish
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

  protected void runBareRunnable(Runnable runnable) throws Throwable {
    SwingUtilities.invokeAndWait(runnable);
  }

  protected boolean isRunInWriteAction() {
    return true;
  }

  protected void invokeTestRunnable(final Runnable runnable) throws Exception {
    final Exception[] e = new Exception[1];
    Runnable runnable1 = new Runnable() {
      public void run() {
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
      }
    };

    if (myRunCommandForTest) {
      CommandProcessor.getInstance().executeCommand(myProject, runnable1, "", null);
    }
    else {
      runnable1.run();
    }

    if (e[0] != null) {
      throw e[0];
    }
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else if (PlatformDataKeys.EDITOR.is(dataId)) {
      return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    }
    else {
      return null;
    }
  }

  public static File createTempDir(@NonNls final String prefix) throws IOException {
    final File tempDirectory = FileUtil.createTempDirectory(prefix, null);
    myFilesToDelete.add(tempDirectory);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        VirtualFileManager.getInstance().refresh(false);
      }
    });

    return tempDirectory;
  }

  protected static VirtualFile getVirtualFile(final File file) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  protected File createTempDirectory() throws IOException {
    File dir = FileUtil.createTempDirectory(getTestName(true), null);
    myFilesToDelete.add(dir);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        VirtualFileManager.getInstance().refresh(false);
      }
    });
    return dir;
  }

  protected PsiFile getPsiFile(final Document document) {
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
  }

  private static void setTmpDir(String path) {
    System.setProperty("java.io.tmpdir", path);
    FileUtil.resetCanonicalTempPathCache();

    try {
      Class<File> ioFile = File.class;
      Field field = ioFile.getDeclaredField("tmpdir");
      field.setAccessible(true);
      field.set(ioFile, null);
    }
    catch (NoSuchFieldException ignore) {
      // field was removed in JDK 1.6.0_12
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
  }

  public static void initPlatformLangPrefix() {
    initPlatformPrefix("com.intellij.openapi.project.impl.IdeaProjectManagerImpl", "PlatformLangXml");
  }

  protected static void initPlatformPrefix(String classToTest, String prefix) {
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
        System.setProperty("idea.platform.prefix", prefix);
      }
    }
  }
}
