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

import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaLogger;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.CollectionFactory;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class LightPlatformTestCase extends UsefulTestCase implements DataProvider {
  public static final String PROFILE = "Configurable";
  private static IdeaTestApplication ourApplication;
  protected static Project ourProject;
  private static Module ourModule;
  private static PsiManager ourPsiManager;
  private static boolean ourAssertionsInTestDetected;
  private static VirtualFile ourSourceRoot;
  private static TestCase ourTestCase = null;
  public static Thread ourTestThread;
  private static LightProjectDescriptor ourProjectDescriptor;
  @NonNls private static final String LIGHT_PROJECT_MARK = "Light project: ";
  private final Map<String, InspectionTool> myAvailableInspectionTools = new THashMap<String, InspectionTool>();
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

  public static void initApplication(final DataProvider dataProvider) throws Exception {
    ourApplication = IdeaTestApplication.getInstance(null);
    ourApplication.setDataProvider(dataProvider);
  }

  @TestOnly
  public static void disposeApplication() throws Exception {
    if (ourApplication != null) {
      Disposer.dispose(ourApplication);
      ourApplication = null;
    }
  }

  public static IdeaTestApplication getApplication() {
    return ourApplication;
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
    ((PersistentFS)ManagingFS.getInstance()).cleanPersistedContents();
  }

  public static boolean isLight(Project project) {
    String creationPlace = project.getUserData(CREATION_PLACE);
    return creationPlace != null && StringUtil.startsWith(creationPlace, LIGHT_PROJECT_MARK);
  }

  private static void initProject(final LightProjectDescriptor descriptor) throws Exception {
    ourProjectDescriptor = descriptor;
    final File projectFile = FileUtil.createTempFile("lighttemp", ProjectFileType.DOT_DEFAULT_EXTENSION);

    new WriteCommandAction.Simple(null) {
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

        ourProject = PlatformTestCase.createProject(projectFile, LIGHT_PROJECT_MARK +buffer.toString());

        if (!ourHaveShutdownHook) {
          ourHaveShutdownHook = true;
          registerShutdownHook();
        }
        ourPsiManager = null;
        ourModule = createMainModule(descriptor.getModuleType());


        //ourSourceRoot = DummyFileSystem.getInstance().createRoot("src");

        final VirtualFile dummyRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
        dummyRoot.refresh(false, false);

        try {
          ourSourceRoot = dummyRoot.createChildDirectory(this, "src");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        FileBasedIndex.getInstance().registerIndexableSet(new IndexableFileSet() {
          @Override
          public boolean isInSet(final VirtualFile file) {
            return ourSourceRoot != null && file.getFileSystem() == ourSourceRoot.getFileSystem();
          }

          @Override
          public void iterateIndexableFilesIn(final VirtualFile file, final ContentIterator iterator) {
            if (file.isDirectory()) {
              for (VirtualFile child : file.getChildren()) {
                iterateIndexableFilesIn(child, iterator);
              }
            }
            else {
              iterator.processFile(file);
            }
          }
        }, null);

        final ModuleRootManager rootManager = ModuleRootManager.getInstance(ourModule);

        final ModifiableRootModel rootModel = rootManager.getModifiableModel();


        if (descriptor.getSdk() != null) {
          rootModel.setSdk(descriptor.getSdk());
        }

        final ContentEntry contentEntry = rootModel.addContentEntry(ourSourceRoot);
        contentEntry.addSourceFolder(ourSourceRoot, false);

        descriptor.configureModule(ourModule, rootModel, contentEntry);

        rootModel.commit();

        final MessageBusConnection connection = ourProject.getMessageBus().connect();
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
          @Override
          public void beforeRootsChange(ModuleRootEvent event) {
            if (!event.isCausedByFileTypesChange()) {
              //TODO: uncomment fail("Root modification in LightIdeaTestCase is not allowed.");
            }
          }

          @Override
          public void rootsChanged(ModuleRootEvent event) {
          }
        });

        connection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
          @Override
          public void moduleAdded(Project project, Module module) {
            fail("Adding modules is not permitted in LightIdeaTestCase.");
          }

          @Override
          public void beforeModuleRemoved(Project project, Module module) {
          }

          @Override
          public void moduleRemoved(Project project, Module module) {
          }

          @Override
          public void modulesRenamed(Project project, List<Module> modules) {
          }
        });


        final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(ourProject);
        startupManager.runStartupActivities();
        startupManager.startCacheUpdate();
      }
    }.execute().throwException();
  }

  protected static Module createMainModule(final ModuleType moduleType) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleManager.getInstance(ourProject).newModule("light_idea_test_case.iml", moduleType);
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
    super.setUp();
    initApplication(this);
    doSetup(new SimpleLightProjectDescriptor(getModuleType(), getProjectJDK()), configureLocalInspectionTools(), myAvailableInspectionTools);
    ((InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(getProject())).pushInjectors();

    storeSettings();

    myThreadTracker = new ThreadTracker();
  }

  public static void doSetup(final LightProjectDescriptor descriptor,
                             final LocalInspectionTool[] localInspectionTools, final Map<String, InspectionTool> availableInspectionTools) throws Exception {
    assertNull("Previous test " + ourTestCase + " hasn't called tearDown(). Probably overriden without super call.", ourTestCase);
    IdeaLogger.ourErrorsOccurred = null;

    if (ourProject == null || !ourProjectDescriptor.equals(descriptor)) {
      initProject(descriptor);
    }
    ((ProjectImpl)ourProject).setTemporarilyDisposed(false);

    ProjectManagerEx.getInstanceEx().setCurrentTestProject(ourProject);

    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).clearUncommitedDocuments();

    for (LocalInspectionTool tool : localInspectionTools) {
      enableInspectionTool(availableInspectionTools, new LocalInspectionToolWrapper(tool));
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl("Configurable") {
      @Override
      @NotNull
      public InspectionProfileEntry[] getInspectionTools(PsiElement element) {
        if (availableInspectionTools != null){
          final Collection<InspectionTool> tools = availableInspectionTools.values();
          return tools.toArray(new InspectionTool[tools.size()]);
        }
        return new InspectionTool[0];
      }

      @Override
      public List<ToolsImpl> getAllEnabledInspectionTools() {
        List<ToolsImpl> result = new ArrayList<ToolsImpl>();
        for (InspectionProfileEntry entry : getInspectionTools(null)) {
          result.add(new ToolsImpl(entry, entry.getDefaultLevel(), true));
        }
        return result;
      }

      @Override
      public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
        return key != null && availableInspectionTools.containsKey(key.toString());
      }

      @Override
      public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, PsiElement element) {
        InspectionTool localInspectionTool = availableInspectionTools.get(key.toString());
        return localInspectionTool != null ? localInspectionTool.getDefaultLevel() : HighlightDisplayLevel.WARNING;
      }

      @Override
      public InspectionTool getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
        if (availableInspectionTools.containsKey(shortName)) {
          return availableInspectionTools.get(shortName);
        }
        return null;
      }

      @Override
      public InspectionProfileEntry getToolById(String id, PsiElement element) {
        if (availableInspectionTools.containsKey(id)) {
          return availableInspectionTools.get(id);
        }
        return null;
      }
    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    inspectionProfileManager.setRootProfile(profile.getName());
    InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
    InspectionProjectProfileManager.getInstance(getProject()).setProjectProfile(profile.getName());

    assertFalse(getPsiManager().isDisposed());
    assertTrue(getProject().isInitialized());

    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(new CodeStyleSettings());

    FileDocumentManager manager = FileDocumentManager.getInstance();
    if (manager instanceof FileDocumentManagerImpl) {
      assertEmpty(manager.getUnsavedDocuments());
    }
  }

  protected void enableInspectionTool(LocalInspectionTool tool){
    enableInspectionTool(new LocalInspectionToolWrapper(tool));
  }

  protected void enableInspectionTool(InspectionTool tool){
    enableInspectionTool(myAvailableInspectionTools, tool);
  }

  private static void enableInspectionTool(final Map<String, InspectionTool> availableLocalTools, InspectionTool wrapper) {
    final String shortName = wrapper.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      HighlightDisplayKey.register(shortName, wrapper.getDisplayName(), wrapper instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)wrapper).getTool().getID() : wrapper.getShortName());
    }
    availableLocalTools.put(shortName, wrapper);
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return LocalInspectionTool.EMPTY_ARRAY;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    checkForSettingsDamage();
    doTearDown(getProject(), ourApplication, true);

    super.tearDown();

    myThreadTracker.checkLeak();
    ((InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(getProject())).checkInjectorsAreDisposed();
  }

  public static void doTearDown(Project project, IdeaTestApplication application, boolean checkForEditors) throws Exception {
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
    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
    documentManager.clearUncommitedDocuments();
    ((HintManagerImpl)HintManager.getInstance()).cleanup();

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ((UndoManagerImpl)UndoManager.getGlobalInstance()).dropHistoryInTests();
      }
    });

    TemplateDataLanguageMappings.getInstance(project).cleanupForNextTest();

    ProjectManagerEx.getInstanceEx().setCurrentTestProject(null);
    application.setDataProvider(null);
    ourTestCase = null;
    ((PsiManagerImpl)PsiManager.getInstance(project)).cleanupForNextTest();

    CompletionProgressIndicator.cleanupForNextTest();

    if (checkForEditors) {
      checkEditorsReleased();
    }
    if (isLight(project)) {
      ((ProjectImpl)project).setTemporarilyDisposed(true); // mark temporarily as disposed so that rogue component trying to access it will fail
      documentManager.clearUncommitedDocuments();
    }
  }

  public static void checkEditorsReleased() {
    final Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
    if (allEditors.length > 0) {
      String fail = null;
      for (Editor editor : allEditors) {
        fail = EditorFactoryImpl.notReleasedError(editor);
        EditorFactory.getInstance().releaseEditor(editor);
      }
      fail("Unreleased editors: " + allEditors.length + "\n"+fail);
    }
  }

  @Override
  public final void runBare() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          ourTestThread = Thread.currentThread();
          startRunAndTear();
        }
        catch (Throwable throwable) {
          throwables[0] = throwable;
        }
        finally {
          ourTestThread = null;
          try {
            PlatformTestCase.cleanupApplicationCaches(ourProject);
            resetAllFields();
          }
          catch (Throwable e) {
            e.printStackTrace();
          }
        }
      }
    });

    if (throwables[0] != null) {
      throw throwables[0];
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
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return ourProject;
    }
    return null;
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
   * @throws com.intellij.util.IncorrectOperationException
   */
  protected static PsiFile createFile(@NonNls String fileName, String text) throws IncorrectOperationException {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), true, false);
  }

  protected static PsiFile createLightFile(@NonNls String fileName, String text) throws IncorrectOperationException {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, fileType, text, LocalTimeCounter.currentTime(), false, false);
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
    if (name.length() > 0 && lowercaseFirstLetter && !UsefulTestCase.isAllUppercaseName(name)) {
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
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  protected static Document getDocument(final PsiFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  public static synchronized void closeAndDeleteProject() {
    if (ourProject != null) {
      ((ProjectImpl)ourProject).setTemporarilyDisposed(false);
      final VirtualFile projFile = ((ProjectEx)ourProject).getStateStore().getProjectFile();
      final File projectFile = projFile == null ? null : VfsUtil.virtualToIoFile(projFile);
      if (!ourProject.isDisposed()) Disposer.dispose(ourProject);

      if (projectFile != null) {
        FileUtil.delete(projectFile);
      }
      ourProject = null;
    }
  }


  static {
    System.setProperty("jbdt.test.fixture", "com.intellij.designer.dt.IJTestFixture");
  }

  private static void registerShutdownHook() {
    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        ShutDownTracker.invokeAndWait(true, new Runnable() {
          @Override
          public void run() {
            closeAndDeleteProject();
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
      return CollectionFactory.newSet(myUrls).equals(CollectionFactory.newSet(newUrls));
    }

  }
}
