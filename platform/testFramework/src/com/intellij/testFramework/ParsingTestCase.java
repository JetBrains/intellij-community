// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.mock.*;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.*;
import org.picocontainer.defaults.AbstractComponentAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/** @noinspection JUnitTestCaseWithNonTrivialConstructors*/
public abstract class ParsingTestCase extends PlatformLiteFixture {
  protected String myFilePrefix = "";
  protected String myFileExt;
  protected final String myFullDataPath;
  protected PsiFile myFile;
  private MockPsiManager myPsiManager;
  private PsiFileFactoryImpl myFileFactory;
  protected Language myLanguage;
  private final ParserDefinition[] myDefinitions;
  private final boolean myLowercaseFirstLetter;

  protected ParsingTestCase(@NotNull String dataPath, @NotNull String fileExt, @NotNull ParserDefinition... definitions) {
    this(dataPath, fileExt, false, definitions);
  }

  protected ParsingTestCase(@NotNull String dataPath, @NotNull String fileExt, boolean lowercaseFirstLetter, @NotNull ParserDefinition... definitions) {
    myDefinitions = definitions;
    myFullDataPath = getTestDataPath() + "/" + dataPath;
    myFileExt = fileExt;
    myLowercaseFirstLetter = lowercaseFirstLetter;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    ComponentAdapter component = getApplication().getPicoContainer().getComponentAdapter(ProgressManager.class.getName());
    if (component == null) {
      getApplication().getPicoContainer().registerComponent(new AbstractComponentAdapter(ProgressManager.class.getName(), Object.class) {
        @Override
        public Object getComponentInstance(PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
          return new ProgressManagerImpl();
        }

        @Override
        public void verify(PicoContainer container) throws PicoIntrospectionException { }
      });
    }
    Extensions.registerAreaClass("IDEA_PROJECT", null);
    myProject = new MockProjectEx(getTestRootDisposable());
    myPsiManager = new MockPsiManager(myProject);
    myFileFactory = new PsiFileFactoryImpl(myPsiManager);
    MutablePicoContainer appContainer = getApplication().getPicoContainer();
    registerComponentInstance(appContainer, MessageBus.class, getApplication().getMessageBus());
    registerComponentInstance(appContainer, SchemeManagerFactory.class, new MockSchemeManagerFactory());
    MockEditorFactory editorFactory = new MockEditorFactory();
    registerComponentInstance(appContainer, EditorFactory.class, editorFactory);
    registerComponentInstance(appContainer, FileDocumentManager.class, new MockFileDocumentManagerImpl(
      charSequence -> editorFactory.createDocument(charSequence), FileDocumentManagerImpl.HARD_REF_TO_DOCUMENT_KEY));
    registerComponentInstance(appContainer, PsiDocumentManager.class, new MockPsiDocumentManager());

    registerApplicationService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    registerApplicationService(DefaultASTFactory.class, new DefaultASTFactoryImpl());
    registerApplicationService(ReferenceProvidersRegistry.class, new ReferenceProvidersRegistryImpl());
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(myPsiManager)));
    myProject.registerService(PsiManager.class, myPsiManager);
    myProject.registerService(StartupManager.class, new StartupManagerImpl(myProject));
    registerExtensionPoint(FileTypeFactory.FILE_TYPE_FACTORY_EP, FileTypeFactory.class);
    registerExtensionPoint(MetaLanguage.EP_NAME, MetaLanguage.class);

    for (ParserDefinition definition : myDefinitions) {
      addExplicitExtension(LanguageParserDefinitions.INSTANCE, definition.getFileNodeType().getLanguage(), definition);
    }
    if (myDefinitions.length > 0) {
      configureFromParserDefinition(myDefinitions[0], myFileExt);
    }

    // That's for reparse routines
    PomModelImpl pomModel = new PomModelImpl(myProject);
    myProject.registerService(PomModel.class, pomModel);
    new TreeAspect(pomModel);
  }

  public void configureFromParserDefinition(ParserDefinition definition, String extension) {
    myLanguage = definition.getFileNodeType().getLanguage();
    myFileExt = extension;
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, myLanguage, definition);
    registerComponentInstance(getApplication().getPicoContainer(), FileTypeManager.class,
                              new MockFileTypeManager(new MockLanguageFileType(myLanguage, myFileExt)));
  }

  protected <T> void addExplicitExtension(LanguageExtension<T> instance, Language language, T object) {
    instance.addExplicitExtension(language, object);
    Disposer.register(getTestRootDisposable(), () -> instance.removeExplicitExtension(language, object));
  }

  @Override
  protected <T> void registerExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<T> aClass) {
    super.registerExtensionPoint(extensionPointName, aClass);
    Disposer.register(getTestRootDisposable(), () -> Extensions.getRootArea().unregisterExtensionPoint(extensionPointName.getName()));
  }

  protected <T> void registerApplicationService(Class<T> aClass, T object) {
    getApplication().registerService(aClass, object);
    Disposer.register(getTestRootDisposable(), () -> getApplication().getPicoContainer().unregisterComponent(aClass.getName()));
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
      public void visitErrorElement(PsiErrorElement element) {
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
      assertEquals("doc text mismatch", text, requireNonNull(myFile.getViewProvider().getDocument()).getText());
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
    virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);
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
      public void visitElement(PsiElement element) {
        element.acceptChildren(this);
      }
    });
  }

  public static void ensureCorrectReparse(@NotNull final PsiFile file) {
    final String psiToStringDefault = DebugUtil.psiToString(file, false, false);

    DebugUtil.performPsiModification("ensureCorrectReparse", () -> {
                                       final String fileText = file.getText();
                                       final DiffLog diffLog = new BlockSupportImpl(file.getProject()).reparseRange(
                                         file, file.getNode(), TextRange.allOf(fileText), fileText, new EmptyProgressIndicator(), fileText);
                                       diffLog.performActualPsiChange(file);
                                     });

    assertEquals(psiToStringDefault, DebugUtil.psiToString(file, false, false));
  }

  public void registerMockInjectedLanguageManager() {
    registerExtensionPoint(Extensions.getArea(myProject), MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME, MultiHostInjector.class);

    registerExtensionPoint(LanguageInjector.EXTENSION_POINT_NAME, LanguageInjector.class);
    myProject.registerService(InjectedLanguageManager.class, new InjectedLanguageManagerImpl(myProject, new MockDumbService(myProject)));
  }
}