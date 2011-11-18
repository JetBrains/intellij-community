/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.mock.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiCachedValuesFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistryImpl;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.defaults.AbstractComponentAdapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public abstract class ParsingTestCase extends PlatformLiteFixture {
  protected String myFilePrefix = "";
  protected String myFileExt;
  @NonNls protected final String myFullDataPath;
  protected PsiFile myFile;
  private MockPsiManager myPsiManager;
  private PsiFileFactoryImpl myFileFactory;
  protected Language myLanguage;
  @NotNull private final ParserDefinition[] myDefinitions;

  public ParsingTestCase(@NonNls @NotNull String dataPath, @NotNull String fileExt, @NotNull ParserDefinition... definitions) {
    myDefinitions = definitions;
    myFullDataPath = getTestDataPath() + "/" + dataPath;
    myFileExt = fileExt;
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
          return new ProgressManagerImpl(getApplication());
        }

        @Override
        public void verify(PicoContainer container) throws PicoIntrospectionException {
        }
      });
    }
    Extensions.registerAreaClass("IDEA_PROJECT", null);
    myProject = new MockProjectEx(getTestRootDisposable());
    myPsiManager = new MockPsiManager(myProject);
    myFileFactory = new PsiFileFactoryImpl(myPsiManager);
    final MutablePicoContainer appContainer = getApplication().getPicoContainer();
    registerComponentInstance(appContainer, MessageBus.class, MessageBusFactory.newMessageBus(getApplication()));
    registerComponentInstance(appContainer, SchemesManagerFactory.class, new MockSchemesManagerFactory());
    final MockEditorFactory editorFactory = new MockEditorFactory();
    registerComponentInstance(appContainer, EditorFactory.class, editorFactory);
    registerComponentInstance(appContainer, FileDocumentManager.class, new MockFileDocumentManagerImpl(new Function<CharSequence, Document>() {
      @Override
      public Document fun(CharSequence charSequence) {
        return editorFactory.createDocument(charSequence);
      }
    }, FileDocumentManagerImpl.DOCUMENT_KEY));
    registerComponentInstance(appContainer, PsiDocumentManager.class, new MockPsiDocumentManager());
    myLanguage = myLanguage == null && myDefinitions.length > 0? myDefinitions[0].getFileNodeType().getLanguage() : myLanguage;
    registerComponentInstance(appContainer, FileTypeManager.class, new MockFileTypeManager(new MockLanguageFileType(myLanguage, myFileExt)));
    registerApplicationService(PsiBuilderFactory.class, new PsiBuilderFactoryImpl());
    registerApplicationService(DefaultASTFactory.class, new DefaultASTFactoryImpl());
    registerApplicationService(ReferenceProvidersRegistry.class, new ReferenceProvidersRegistryImpl());
    myProject.registerService(CachedValuesManager.class, new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(myPsiManager)));
    myProject.registerService(PsiManager.class, myPsiManager);
    myProject.registerService(StartupManager.class, new StartupManagerImpl(myProject));
    registerExtensionPoint(FileTypeFactory.FILE_TYPE_FACTORY_EP, FileTypeFactory.class);

    for (ParserDefinition definition : myDefinitions) {
      addExplicitExtension(LanguageParserDefinitions.INSTANCE, definition.getFileNodeType().getLanguage(), definition);
    }
  }

  protected <T> void addExplicitExtension(final LanguageExtension<T> instance, final Language language, final T object) {
    instance.addExplicitExtension(language, object);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        instance.removeExplicitExtension(language, object);
      }
    });
  }

  @Override
  protected <T> void registerExtensionPoint(final ExtensionPointName<T> extensionPointName, Class<T> aClass) {
    super.registerExtensionPoint(extensionPointName, aClass);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        Extensions.getRootArea().unregisterExtensionPoint(extensionPointName.getName());
      }
    });
  }

  protected <T> void registerApplicationService(final Class<T> aClass, T object) {
    getApplication().registerService(aClass, object);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        getApplication().getPicoContainer().unregisterComponent(aClass.getName());
      }
    });
  }

  public MockProjectEx getProject() {
    return myProject;
  }

  public MockPsiManager getPsiManager() {
    return myPsiManager;
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myFile = null;
    myProject = null;
    myPsiManager = null;
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
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

  protected void doTest(boolean checkResult) {
    String name = getTestName(false);
    try {
      String text = loadFile(name + "." + myFileExt);
      myFile = createPsiFile(name, text);
      ensureParsed(myFile);
      assertEquals("light virtual file text mismatch", text, ((LightVirtualFile)myFile.getVirtualFile()).getContent().toString());
      assertEquals("virtual file text mismatch", text, LoadTextUtil.loadText(myFile.getVirtualFile()));
      assertEquals("doc text mismatch", text, myFile.getViewProvider().getDocument().getText());
      assertEquals("psi text mismatch", text, myFile.getText());
      if (checkResult){
        checkResult(name + ".txt", myFile);
      }
      else{
        toParseTreeText(myFile, skipSpaces(), includeRanges());
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void doTest(String suffix) throws IOException {
    String name = getTestName(false);
    String text = loadFile(name + "." + myFileExt);
    myFile = createPsiFile(name, text);
    ensureParsed(myFile);
    assertEquals(text, myFile.getText());
    checkResult(name + suffix + ".txt", myFile);
  }

  protected void doCodeTest(String code) throws IOException {
    String name = getTestName(false);
    myFile = createPsiFile("a", code);
    ensureParsed(myFile);
    assertEquals(code, myFile.getText());
    checkResult(myFilePrefix + name + ".txt", myFile);
  }

  protected PsiFile createPsiFile(String name, String text) {
    return createFile(name + "." + myFileExt, text);
  }

  protected PsiFile createFile(@NonNls String name, String text) {
    LightVirtualFile virtualFile = new LightVirtualFile(name, myLanguage, text);
    virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);
    return createFile(virtualFile);
  }

  protected PsiFile createFile(LightVirtualFile virtualFile) {
    return myFileFactory.trySetupPsiForFile(virtualFile, myLanguage, true, false);
  }

  protected void checkResult(@NonNls @TestDataFile String targetDataName, final PsiFile file) throws IOException {
    doCheckResult(myFullDataPath, file, checkAllPsiRoots(), targetDataName, skipSpaces(), includeRanges());
  }

  public static void doCheckResult(String myFullDataPath,
                                   PsiFile file,
                                   boolean checkAllPsiRoots,
                                   String targetDataName,
                                   boolean skipSpaces,
                                   boolean printRanges) throws IOException {
    final PsiElement[] psiRoots = checkAllPsiRoots? file.getPsiRoots() : PsiElement.EMPTY_ARRAY;
    if(psiRoots.length > 1){
      for (int i = 0; i < psiRoots.length; i++) {
        final PsiElement psiRoot = psiRoots[i];
        doCheckResult(myFullDataPath, targetDataName + "." + i, toParseTreeText(psiRoot, skipSpaces, printRanges).trim());
      }
    }
    else{
      doCheckResult(myFullDataPath, targetDataName, toParseTreeText(file, skipSpaces, printRanges).trim());
    }
  }

  protected void checkResult(@TestDataFile @NonNls String targetDataName, final String text) throws IOException {
    doCheckResult(myFullDataPath, targetDataName, text);
  }

  private static void doCheckResult(String myFullDataPath, String targetDataName, String text) throws IOException {
    try {
      text = text.trim();
      String expectedText = doLoadFile(myFullDataPath, targetDataName);
      assertEquals(targetDataName, expectedText, text);
    }
    catch(FileNotFoundException e){
      String fullName = myFullDataPath + File.separatorChar + targetDataName;
      FileWriter writer = new FileWriter(fullName);
      try {
        writer.write(text);
      }
      finally {
        writer.close();
      }
      fail("No output text found. File " + fullName + " created.");
    }
  }

  protected static String toParseTreeText(final PsiElement file,  boolean skipSpaces, boolean printRanges) {
    return DebugUtil.psiToString(file, skipSpaces, printRanges);
  }

  protected String loadFile(@NonNls @TestDataFile String name) throws IOException {
    return doLoadFile(myFullDataPath, name);
  }

  private static String doLoadFile(String myFullDataPath, String name) throws IOException {
    String fullName = myFullDataPath + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName), CharsetToolkit.UTF8).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

  private static void ensureParsed(PsiFile file) {
    file.accept(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        element.acceptChildren(this);
      }
    });
  }
}
