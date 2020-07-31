// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.plugins.PluginUtil;
import com.intellij.ide.plugins.PluginUtilImpl;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.mock.*;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.PomModel;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistryImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** @noinspection JUnitTestCaseWithNonTrivialConstructors*/
public abstract class ParsingTestCase extends UsefulTestCase {
  private PluginDescriptor myPluginDescriptor;

  private MockApplication myApp;
  protected MockProjectEx myProject;

  protected String myFilePrefix = "";
  protected String myFileExt;
  protected final String myFullDataPath;
  protected PsiFile myFile;
  private MockPsiManager myPsiManager;
  private PsiFileFactoryImpl myFileFactory;
  protected Language myLanguage;
  private final ParserDefinition[] myDefinitions;
  private final boolean myLowercaseFirstLetter;
  private ExtensionPointImpl<KeyedLazyInstance<ParserDefinition>> myLangParserDefinition;

  protected ParsingTestCase(@NotNull String dataPath, @NotNull String fileExt, ParserDefinition @NotNull ... definitions) {
    this(dataPath, fileExt, false, definitions);
  }

  protected ParsingTestCase(@NotNull String dataPath, @NotNull String fileExt, boolean lowercaseFirstLetter, ParserDefinition @NotNull ... definitions) {
    myDefinitions = definitions;
    myFullDataPath = getTestDataPath() + "/" + dataPath;
    myFileExt = fileExt;
    myLowercaseFirstLetter = lowercaseFirstLetter;
  }

  @NotNull
  protected MockApplication getApplication() {
    return myApp;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    MockApplication app = MockApplication.setUp(getTestRootDisposable());
    myApp = app;
    MutablePicoContainer appContainer = app.getPicoContainer();
    ComponentAdapter component = appContainer.getComponentAdapter(ProgressManager.class.getName());
    if (component == null) {
      appContainer.registerComponentInstance(ProgressManager.class.getName(), new ProgressManagerImpl());
    }

    myProject = new MockProjectEx(getTestRootDisposable());
    myPsiManager = new MockPsiManager(myProject);
    myFileFactory = new PsiFileFactoryImpl(myPsiManager);
    appContainer.registerComponentInstance(MessageBus.class, app.getMessageBus());
    appContainer.registerComponentInstance(SchemeManagerFactory.class, new MockSchemeManagerFactory());
    MockEditorFactory editorFactory = new MockEditorFactory();
    appContainer.registerComponentInstance(EditorFactory.class, editorFactory);
    app.registerService(FileDocumentManager.class, new MockFileDocumentManagerImpl(editorFactory::createDocument, FileDocumentManagerImpl.HARD_REF_TO_DOCUMENT_KEY));
    app.registerService(PluginUtil.class, new PluginUtilImpl());

    app.registerService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    app.registerService(DefaultASTFactory.class, new DefaultASTFactoryImpl());
    app.registerService(ReferenceProvidersRegistry.class, new ReferenceProvidersRegistryImpl());
    myProject.registerService(PsiDocumentManager.class, new MockPsiDocumentManager());
    myProject.registerService(PsiManager.class, myPsiManager);
    myProject.registerService(TreeAspect.class, new TreeAspect());
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(myProject)));
    myProject.registerService(StartupManager.class, new StartupManagerImpl(myProject));
    registerExtensionPoint(app.getExtensionArea(), FileTypeFactory.FILE_TYPE_FACTORY_EP, FileTypeFactory.class);
    registerExtensionPoint(app.getExtensionArea(), MetaLanguage.EP_NAME, MetaLanguage.class);

    myLangParserDefinition = app.getExtensionArea().registerFakeBeanPoint(LanguageParserDefinitions.INSTANCE.getName(), getPluginDescriptor());

    if (myDefinitions.length > 0) {
      configureFromParserDefinition(myDefinitions[0], myFileExt);
      // first definition is registered by configureFromParserDefinition
      for (int i = 1, length = myDefinitions.length; i < length; i++) {
        registerParserDefinition(myDefinitions[i]);
      }
    }

    // That's for reparse routines
    myProject.registerService(PomModel.class, new PomModelImpl(myProject));
  }

  protected final void registerParserDefinition(@NotNull ParserDefinition definition) {
    Language language = definition.getFileNodeType().getLanguage();
    myLangParserDefinition.registerExtension(new KeyedLazyInstance<ParserDefinition>() {
      @Override
      public String getKey() {
        return language.getID();
      }

      @NotNull
      @Override
      public ParserDefinition getInstance() {
        return definition;
      }
    });
    LanguageParserDefinitions.INSTANCE.clearCache(language);
    disposeOnTearDown(() -> LanguageParserDefinitions.INSTANCE.clearCache(language));
  }

  public void configureFromParserDefinition(@NotNull ParserDefinition definition, String extension) {
    myLanguage = definition.getFileNodeType().getLanguage();
    myFileExt = extension;
    registerParserDefinition(definition);
    myApp.registerService(FileTypeManager.class, new MockFileTypeManager(new MockLanguageFileType(myLanguage, myFileExt)));
  }

  protected final <T> void registerExtension(@NotNull ExtensionPointName<T> name, @NotNull T extension) {
    //noinspection unchecked
    registerExtensions(name, (Class<T>)extension.getClass(), Collections.singletonList(extension));
  }

  protected final <T> void registerExtensions(@NotNull ExtensionPointName<T> name, @NotNull Class<T> extensionClass, @NotNull List<T> extensions) {
    ExtensionsAreaImpl area = myApp.getExtensionArea();
    ExtensionPoint<T> point = area.getExtensionPointIfRegistered(name.getName());
    if (point == null) {
      point = registerExtensionPoint(area, name, extensionClass);
    }

    for (T extension : extensions) {
      // no need to specify disposable because ParsingTestCase in any case clean area for each test
      //noinspection deprecation
      point.registerExtension(extension);
    }
  }

  protected final <T> void addExplicitExtension(@NotNull LanguageExtension<T> collector, @NotNull Language language, @NotNull T object) {
    ExtensionsAreaImpl area = myApp.getExtensionArea();
    PluginDescriptor pluginDescriptor = getPluginDescriptor();
    if (!area.hasExtensionPoint(collector.getName())) {
      area.registerFakeBeanPoint(collector.getName(), pluginDescriptor);
    }
    LanguageExtensionPoint<T> extension = new LanguageExtensionPoint<>(language.getID(), object);
    extension.setPluginDescriptor(pluginDescriptor);
    ExtensionTestUtil.addExtension(area, collector, extension);
  }

  protected final <T> void registerExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<T> aClass) {
    registerExtensionPoint(myApp.getExtensionArea(), extensionPointName, aClass);
  }

  protected <T> ExtensionPointImpl<T> registerExtensionPoint(@NotNull ExtensionsAreaImpl extensionArea,
                                                             @NotNull BaseExtensionPointName<T> extensionPointName,
                                                             @NotNull Class<T> extensionClass) {
    // todo get rid of it - registerExtensionPoint should be not called several times
    String name = extensionPointName.getName();
    if (extensionArea.hasExtensionPoint(name)) {
      return extensionArea.getExtensionPoint(name);
    }
    else {
      return extensionArea.registerPoint(name, extensionClass, getPluginDescriptor());
    }
  }

  @NotNull
  // easy debug of not disposed extension
  private PluginDescriptor getPluginDescriptor() {
    PluginDescriptor pluginDescriptor = myPluginDescriptor;
    if (pluginDescriptor == null) {
      pluginDescriptor = new DefaultPluginDescriptor(PluginId.getId(getClass().getName() + "." + getName()), ParsingTestCase.class.getClassLoader());
      myPluginDescriptor = pluginDescriptor;
    }
    return pluginDescriptor;
  }

  @NotNull
  public MockProjectEx getProject() {
    return myProject;
  }

  public MockPsiManager getPsiManager() {
    return myPsiManager;
  }

  @Override
  protected void tearDown() throws Exception {
    myFile = null;
    myProject = null;
    myPsiManager = null;
    myFileFactory = null;
    super.tearDown();
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  @NotNull
  public final String getTestName() {
    return getTestName(myLowercaseFirstLetter);
  }

  protected boolean includeRanges() {
    return false;
  }

  protected boolean skipSpaces() {
    return false;
  }

  protected boolean checkAllPsiRoots() {
    return true;
  }

  /* Sanity check against thoughtlessly copy-pasting actual test results as the expected test data. */
  protected void ensureNoErrorElements() {
    myFile.accept(new PsiRecursiveElementVisitor() {
      private static final int TAB_WIDTH = 8;

      @Override
      public void visitErrorElement(@NotNull PsiErrorElement element) {
        // Very dump approach since a corresponding Document is not available.
        String text = myFile.getText();
        String[] lines = StringUtil.splitByLinesKeepSeparators(text);

        int offset = element.getTextOffset();
        LineColumn position = StringUtil.offsetToLineColumn(text, offset);
        int lineNumber = position != null ? position.line : -1;
        int column = position != null ? position.column : 0;

        String line = StringUtil.trimTrailing(lines[lineNumber]);
        // Sanitize: expand indentation tabs, replace the rest with a single space
        int numIndentTabs = StringUtil.countChars(line.subSequence(0, column), '\t', 0, true);
        int indentedColumn = column + numIndentTabs * (TAB_WIDTH - 1);
        String lineWithNoTabs = StringUtil.repeat(" ", numIndentTabs * TAB_WIDTH) + line.substring(numIndentTabs).replace('\t', ' ');
        String errorUnderline = StringUtil.repeat(" ", indentedColumn) + StringUtil.repeat("^", Math.max(1, element.getTextLength()));

        fail(String.format("Unexpected error element: %s:%d:%d\n\n%s\n%s\n%s",
                           myFile.getName(), lineNumber + 1, column,
                           lineWithNoTabs, errorUnderline, element.getErrorDescription()));
      }
    });
  }

  protected void doTest(boolean checkResult) {
    doTest(checkResult, false);
  }

  protected void doTest(boolean checkResult, boolean ensureNoErrorElements) {
    String name = getTestName();
    try {
      String text = loadFile(name + "." + myFileExt);
      myFile = createPsiFile(name, text);
      ensureParsed(myFile);
      assertEquals("light virtual file text mismatch", text, ((LightVirtualFile)myFile.getVirtualFile()).getContent().toString());
      assertEquals("virtual file text mismatch", text, LoadTextUtil.loadText(myFile.getVirtualFile()));
      assertEquals("doc text mismatch", text, Objects.requireNonNull(myFile.getViewProvider().getDocument()).getText());
      assertEquals("psi text mismatch", text, myFile.getText());
      ensureCorrectReparse(myFile);
      if (checkResult) {
        checkResult(name, myFile);
        if (ensureNoErrorElements) {
          ensureNoErrorElements();
        }
      }
      else {
        toParseTreeText(myFile, skipSpaces(), includeRanges());
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void doTest(String suffix) throws IOException {
    String name = getTestName();
    String text = loadFile(name + "." + myFileExt);
    myFile = createPsiFile(name, text);
    ensureParsed(myFile);
    assertEquals(text, myFile.getText());
    checkResult(name + suffix, myFile);
  }

  protected void doCodeTest(@NotNull String code) throws IOException {
    String name = getTestName();
    myFile = createPsiFile("a", code);
    ensureParsed(myFile);
    assertEquals(code, myFile.getText());
    checkResult(myFilePrefix + name, myFile);
  }

  protected PsiFile createPsiFile(@NotNull String name, @NotNull String text) {
    return createFile(name + "." + myFileExt, text);
  }

  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    LightVirtualFile virtualFile = new LightVirtualFile(name, myLanguage, text);
    virtualFile.setCharset(StandardCharsets.UTF_8);
    return createFile(virtualFile);
  }

  protected PsiFile createFile(@NotNull LightVirtualFile virtualFile) {
    return myFileFactory.trySetupPsiForFile(virtualFile, myLanguage, true, false);
  }

  protected void checkResult(@NotNull @TestDataFile String targetDataName, @NotNull PsiFile file) throws IOException {
    doCheckResult(myFullDataPath, file, checkAllPsiRoots(), targetDataName, skipSpaces(), includeRanges(), allTreesInSingleFile());
  }

  protected boolean allTreesInSingleFile() {
    return false;
  }

  public static void doCheckResult(@NotNull String testDataDir,
                                   @NotNull PsiFile file,
                                   boolean checkAllPsiRoots,
                                   @NotNull String targetDataName,
                                   boolean skipSpaces,
                                   boolean printRanges) {
    doCheckResult(testDataDir, file, checkAllPsiRoots, targetDataName, skipSpaces, printRanges, false);
  }

  public static void doCheckResult(@NotNull String testDataDir,
                                   @NotNull PsiFile file,
                                   boolean checkAllPsiRoots,
                                   @NotNull String targetDataName,
                                   boolean skipSpaces,
                                   boolean printRanges,
                                   boolean allTreesInSingleFile) {
    FileViewProvider provider = file.getViewProvider();
    Set<Language> languages = provider.getLanguages();

    if (!checkAllPsiRoots || languages.size() == 1) {
      doCheckResult(testDataDir, targetDataName + ".txt", toParseTreeText(file, skipSpaces, printRanges).trim());
      return;
    }

    if (allTreesInSingleFile) {
      String expectedName = targetDataName + ".txt";
      StringBuilder sb = new StringBuilder();
      List<Language> languagesList = new ArrayList<>(languages);
      ContainerUtil.sort(languagesList, Comparator.comparing(Language::getID));
      for (Language language : languagesList) {
        sb.append("Subtree: ").append(language.getDisplayName()).append(" (").append(language.getID()).append(")").append("\n")
          .append(toParseTreeText(provider.getPsi(language), skipSpaces, printRanges).trim())
          .append("\n").append(StringUtil.repeat("-", 80)).append("\n");
      }
      doCheckResult(testDataDir, expectedName, sb.toString());
    }
    else {
      for (Language language : languages) {
        PsiFile root = provider.getPsi(language);
        assertNotNull("FileViewProvider " + provider + " didn't return PSI root for language " + language.getID(), root);
        String expectedName = targetDataName + "." + language.getID() + ".txt";
        doCheckResult(testDataDir, expectedName, toParseTreeText(root, skipSpaces, printRanges).trim());
      }
    }
  }

  protected void checkResult(@NotNull String actual) {
    String name = getTestName();
    doCheckResult(myFullDataPath, myFilePrefix + name + ".txt", actual);
  }

  protected void checkResult(@NotNull @TestDataFile String targetDataName, @NotNull String actual) {
    doCheckResult(myFullDataPath, targetDataName, actual);
  }

  public static void doCheckResult(@NotNull String fullPath, @NotNull String targetDataName, @NotNull String actual) {
    String expectedFileName = fullPath + File.separatorChar + targetDataName;
    UsefulTestCase.assertSameLinesWithFile(expectedFileName, actual);
  }

  protected static String toParseTreeText(@NotNull PsiElement file,  boolean skipSpaces, boolean printRanges) {
    return DebugUtil.psiToString(file, skipSpaces, printRanges);
  }

  protected String loadFile(@NotNull @TestDataFile String name) throws IOException {
    return loadFileDefault(myFullDataPath, name);
  }

  public static String loadFileDefault(@NotNull String dir, @NotNull String name) throws IOException {
    return FileUtil.loadFile(new File(dir, name), CharsetToolkit.UTF8, true).trim();
  }

  public static void ensureParsed(@NotNull PsiFile file) {
    file.accept(new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        element.acceptChildren(this);
      }
    });
  }

  public static void ensureCorrectReparse(@NotNull final PsiFile file) {
    final String psiToStringDefault = DebugUtil.psiToString(file, false, false);

    DebugUtil.performPsiModification("ensureCorrectReparse", () -> {
                                       final String fileText = file.getText();
                                       final DiffLog diffLog = new BlockSupportImpl().reparseRange(
                                         file, file.getNode(), TextRange.allOf(fileText), fileText, new EmptyProgressIndicator(), fileText);
                                       diffLog.performActualPsiChange(file);
                                     });

    assertEquals(psiToStringDefault, DebugUtil.psiToString(file, false, false));
  }

  public void registerMockInjectedLanguageManager() {
    registerExtensionPoint(myProject.getExtensionArea(), MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME, MultiHostInjector.class);

    registerExtensionPoint(myApp.getExtensionArea(), LanguageInjector.EXTENSION_POINT_NAME, LanguageInjector.class);
    myProject.registerService(DumbService.class, new MockDumbService(myProject));
    myProject.registerService(InjectedLanguageManager.class, new InjectedLanguageManagerImpl(myProject));
  }
}