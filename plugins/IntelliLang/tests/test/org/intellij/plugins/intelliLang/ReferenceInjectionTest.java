// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.injection.Injectable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.InjectionTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.UIUtil;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction;
import org.intellij.plugins.intelliLang.references.FileReferenceInjector;
import org.intellij.plugins.intelliLang.references.InjectedReferencesContributor;
import org.intellij.plugins.intelliLang.references.InjectedReferencesInspection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class ReferenceInjectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().enableInspections(new InjectedReferencesInspection());
  }

  @Test
  public void testInjectReference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" \n" +
                                         "     xxx=\"ba<caret>r\"/>");
    EdtTestUtil.runInEdtAndWait(() -> {
      assertNull(myFixture.getReferenceAtCaretPosition());
      assertTrue(new InjectLanguageAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
      assertFalse(new UnInjectLanguageAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

      InjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
      assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);
      assertFalse(new InjectLanguageAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
      assertTrue(new UnInjectLanguageAction().isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

      myFixture.configureByText("bar.xml",
                                "<foo xmlns=\"<error descr=\"URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)\">http://foo.bar</error>\" \n" +
                                "     xxx=\"<error descr=\"Cannot resolve file 'bar'\">b<caret>ar</error>\"/>");
      myFixture.testHighlighting();

      UnInjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertNull(myFixture.getReferenceAtCaretPosition());
    });
  }

  @Test
  public void testSurviveSerialization() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" \n" +
                                         "     xxx=\"ba<caret>r\"/>");
    EdtTestUtil.runInEdtAndWait(() -> {
      assertNull(myFixture.getReferenceAtCaretPosition());

      InjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
      assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);

      Configuration configuration = Configuration.getInstance();
      Element element = configuration.getState();
      configuration.loadState(element);

      PsiManager.getInstance(myFixture.getProject()).dropPsiCaches();
      assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);

      UnInjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertNull(myFixture.getReferenceAtCaretPosition());
    });
  }

  @Test
  public void testIgnoreInjectionOfUnloadedSupport() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Configuration configuration = Configuration.getProjectInstance(myFixture.getProject());
    Element oldState = configuration.getState();
    try {
      configuration.loadState(JDOMUtil.load("""
        <LanguageInjectionConfiguration>
          <injection language="file-reference" injector-id="unloaded-support">
            <display-name>Unloaded Support</display-name>
            <place><![CDATA[psiParameter().ofMethod(0, psiMethod().withName("forName"))]]></place>
          </injection>
        </LanguageInjectionConfiguration>"""));
      configuration.incModificationCount();
      myFixture.configureByText("foo.xml", "<foo xxx=\"ba<caret>r\"/>");

      List<String> errors = new ArrayList<>();
      LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
        @Override
        public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
          errors.add(message);
          return Action.NONE;
        }
      }, () -> assertNull(getInjectedReferences()));
      assertThat(errors).isEmpty();
      assertTrue(JDOMUtil.write(configuration.getState()).contains("unloaded-support"));
    }
    finally {
      configuration.loadState(oldState);
      configuration.incModificationCount();
    }
  }

  @Test
  public void testInjectIntoTagValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" <bar>x<caret>xx</bar>/>");
    EdtTestUtil.runInEdtAndWait(() -> {
      assertNull(myFixture.getReferenceAtCaretPosition());

      InjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
      assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);

      UnInjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertNull(myFixture.getReferenceAtCaretPosition());
    });
  }

  @Test
  public void testInjectIntoJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              return "ba<caret>r.xml";
          }   \s
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      assertNull(getInjectedReferences());

      InjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
      PsiReference[] references = getInjectedReferences();
      assertThat(references).hasSize(1);
      PsiReference reference = references[0];
      assertTrue(reference instanceof FileReference);

      UnInjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertNull(getInjectedReferences());
    });
  }

  @Test
  public void testInjectionDoesntSurviveLiteralReplacement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Survive.java", """
      class Survive {
          String bar() {
              return "ba<caret>r.xml";
          }   \s
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      assertNull(getInjectedReferences());

      InjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
      PsiReference[] references = getInjectedReferences();
      assertThat(references).hasSize(1);
      PsiReference reference = references[0];
      assertTrue(reference instanceof FileReference);

      String textToReplace = "\"bar.xml\"";

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(myFixture.getProject());
        Document document = manager.getDocument(myFixture.getFile());
        int start = document.getText().indexOf(textToReplace);
        document.replaceString(start, start + textToReplace.length(), "null");
        manager.commitDocument(document);
      });

      assertNull(getInjectedReferences());
    });
  }

  @Test
  public void testUndoLanguageInjection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              String result = "{\\"a<caret>\\" : 1}";
              return result;
          }   \s
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      InjectLanguageAction.invokeImpl(myFixture.getProject(),
                                      myFixture.getEditor(),
                                      myFixture.getFile(),
                                      Injectable.fromLanguage(Language.findLanguageByID("JSON")));
      myFixture.checkResult("""
                              class Foo {
                                  String bar() {
                                      String result = "{\\"a\\" : 1}";
                                      return result;
                                  }   \s
                              }""");
      new InjectionTestFixture(myFixture).assertInjectedLangAtCaret("JSON");
      undo();
      new InjectionTestFixture(myFixture).assertInjectedLangAtCaret(null);
    });
  }

  private void undo() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    UIUtil.invokeAndWaitIfNeeded(() -> {
      final TestDialog oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK);
      try {
        UndoManager undoManager = UndoManager.getInstance(myFixture.getProject());
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
        undoManager.undo(textEditor);
      }
      finally {
        TestDialogManager.setTestDialog(oldTestDialog);
      }
    });
  }

  @Test
  public void testInjectByAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {
          @org.intellij.lang.annotations.Language("file-reference")
          String bar() {
             return "<error descr="Cannot resolve file 'unknown.file'">unknown.file</error>";
          } \s
      }""");
    myFixture.testHighlighting();
  }

  @Test
  public void testConvertToAnnotationLanguageInjection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              String result = "{\\"a<caret>\\" : 1}";
              return result;
          }   \s
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiLanguageInjectionHost injectionHost = myFixture.findElementByText("\"{\\\"a\\\" : 1}\"", PsiLanguageInjectionHost.class);
      SmartPsiElementPointer<PsiLanguageInjectionHost> hostPtr = SmartPointerManager.createPointer(injectionHost);

      StoringFixPresenter storedFix = new StoringFixPresenter();
      InjectLanguageAction.invokeImpl(myFixture.getProject(),
                                      myFixture.getEditor(),
                                      myFixture.getFile(),
                                      Injectable.fromLanguage(Language.findLanguageByID("JSON")),
                                      storedFix);
      myFixture.checkResult("""
                              class Foo {
                                  String bar() {
                                      String result = "{\\"a\\" : 1}";
                                      return result;
                                  }   \s
                              }""");
      new InjectionTestFixture(myFixture).assertInjectedLangAtCaret("JSON");

      storedFix.process(hostPtr.getElement());
      myFixture.checkResult("""
                              import org.intellij.lang.annotations.Language;

                              class Foo {
                                  String bar() {
                                      @Language("JSON") String result = "{\\"a\\" : 1}";
                                      return result;
                                  }   \s
                              }""");
      new InjectionTestFixture(myFixture).assertInjectedLangAtCaret("JSON");

      UnInjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      new InjectionTestFixture(myFixture).assertInjectedLangAtCaret(null);
    });
  }

  @Test
  public void testConvertToAnnotationReferenceInjection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              String result = "ba<caret>r.xml";
              return result;
          }   \s
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiLanguageInjectionHost injectionHost = myFixture.findElementByText("\"bar.xml\"", PsiLanguageInjectionHost.class);
      SmartPsiElementPointer<PsiLanguageInjectionHost> hostPtr = SmartPointerManager.createPointer(injectionHost);

      StoringFixPresenter storedFix = new StoringFixPresenter();

      InjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector(), storedFix);
      myFixture.checkResult("""
                              class Foo {
                                  String bar() {
                                      String result = "bar.xml";
                                      return result;
                                  }   \s
                              }""");
      assertThat(getInjectedReferences()).singleElement().isInstanceOf(FileReference.class);

      storedFix.process((hostPtr.getElement()));
      myFixture.checkResult("""
                              import org.intellij.lang.annotations.Language;

                              class Foo {
                                  String bar() {
                                      @Language("file-reference") String result = "bar.xml";
                                      return result;
                                  }   \s
                              }""");
      assertThat(getInjectedReferences()).singleElement().isInstanceOf(FileReference.class);

      UnInjectLanguageAction.invokeImpl(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      assertNull(getInjectedReferences());
    });
  }

  @Test
  public void testTernary() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              @org.intellij.lang.annotations.Language("encoding-reference")
              String cset = true ? "<error descr="Unknown encoding: 'cp1252345'">cp1252345</error>" : "utf-8";//
          }
      }""");
    myFixture.testHighlighting();
  }

  @Test
  public void testEmptyLiteral() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              @org.intellij.lang.annotations.Language("encoding-reference")
              String cset = true ? <error descr="Unknown encoding: ''">""</error> : "utf-8";//
          }
      }""");
    myFixture.testHighlighting();
  }

  private PsiReference[] getInjectedReferences() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> {
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      element = PsiTreeUtil.getParentOfType(element, PsiLanguageInjectionHost.class);
      assertNotNull(element);
      return InjectedReferencesContributor.getInjectedReferences(element);
    });
  }
}
