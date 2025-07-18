// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang;

import com.intellij.lang.Language;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.injection.Injectable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction;
import org.intellij.plugins.intelliLang.references.FileReferenceInjector;
import org.intellij.plugins.intelliLang.references.InjectedReferencesContributor;
import org.intellij.plugins.intelliLang.references.InjectedReferencesInspection;
import org.jdom.Element;

/**
 * @author Dmitry Avdeev
 */
public class ReferenceInjectionTest extends AbstractLanguageInjectionTestCase {
  public void testInjectReference() {
    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" \n" +
                                         "     xxx=\"ba<caret>r\"/>");
    assertNull(myFixture.getReferenceAtCaretPosition());
    assertTrue(new InjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));
    assertFalse(new UnInjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);
    assertFalse(new InjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));
    assertTrue(new UnInjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));

    myFixture.configureByText("bar.xml",
                              "<foo xmlns=\"<error descr=\"URI is not registered (Settings | Languages & Frameworks | Schemas and DTDs)\">http://foo.bar</error>\" \n" +
                              "     xxx=\"<error descr=\"Cannot resolve file 'bar'\">b<caret>ar</error>\"/>");
    myFixture.testHighlighting();

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(myFixture.getReferenceAtCaretPosition());
  }

  public void testSurviveSerialization() {
    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" \n" +
                                         "     xxx=\"ba<caret>r\"/>");
    assertNull(myFixture.getReferenceAtCaretPosition());

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);

    Configuration configuration = Configuration.getInstance();
    Element element = configuration.getState();
    configuration.loadState(element);

    PsiManager.getInstance(getProject()).dropPsiCaches();
    assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(myFixture.getReferenceAtCaretPosition());
  }

  public void testInjectIntoTagValue() {
    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" <bar>x<caret>xx</bar>/>");
    assertNull(myFixture.getReferenceAtCaretPosition());

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    assertTrue(myFixture.getReferenceAtCaretPosition() instanceof FileReference);

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(myFixture.getReferenceAtCaretPosition());
  }

  public void testInjectIntoJava() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              return "ba<caret>r.xml";
          }   \s
      }""");
    assertNull(getInjectedReferences());

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    PsiReference[] references = getInjectedReferences();
    PsiReference reference = assertOneElement(references);
    assertTrue(reference instanceof FileReference);

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(getInjectedReferences());
  }

  public void testInjectionDoesntSurviveLiteralReplacement() {
    myFixture.configureByText("Survive.java", """
      class Survive {
          String bar() {
              return "ba<caret>r.xml";
          }   \s
      }""");
    assertNull(getInjectedReferences());

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    PsiReference[] references = getInjectedReferences();
    PsiReference reference = assertOneElement(references);
    assertTrue(reference instanceof FileReference);

    String textToReplace = "\"bar.xml\"";

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
      Document document = manager.getDocument(getFile());
      int start = document.getText().indexOf(textToReplace);
      document.replaceString(start, start + textToReplace.length(), "null");
      manager.commitDocument(document);
    });

    assertNull(getInjectedReferences());
  }

  public void testUndoLanguageInjection() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              String result = "{\\"a<caret>\\" : 1}";
              return result;
          }   \s
      }""");
    InjectLanguageAction.invokeImpl(getProject(),
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
    assertInjectedLangAtCaret("JSON");
    undo();
    assertInjectedLangAtCaret(null);
  }

  private void undo() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      final TestDialog oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK);
      try {
        UndoManager undoManager = UndoManager.getInstance(getProject());
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(getEditor());
        undoManager.undo(textEditor);
      }
      finally {
        TestDialogManager.setTestDialog(oldTestDialog);
      }
    });
  }

  public void testInjectByAnnotation() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          @org.intellij.lang.annotations.Language("file-reference")
          String bar() {
             return "<error descr="Cannot resolve file 'unknown.file'">unknown.file</error>";
          } \s
      }""");
    myFixture.testHighlighting();
  }

  public void testConvertToAnnotationLanguageInjection() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              String result = "{\\"a<caret>\\" : 1}";
              return result;
          }   \s
      }""");
    PsiLanguageInjectionHost injectionHost = myFixture.findElementByText("\"{\\\"a\\\" : 1}\"", PsiLanguageInjectionHost.class);
    SmartPsiElementPointer<PsiLanguageInjectionHost> hostPtr = SmartPointerManager.createPointer(injectionHost);

    StoringFixPresenter storedFix = new StoringFixPresenter();
    InjectLanguageAction.invokeImpl(getProject(),
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
    assertInjectedLangAtCaret("JSON");

    storedFix.process(hostPtr.getElement());
    myFixture.checkResult("""
                            import org.intellij.lang.annotations.Language;

                            class Foo {
                                String bar() {
                                    @Language("JSON") String result = "{\\"a\\" : 1}";
                                    return result;
                                }   \s
                            }""");
    assertInjectedLangAtCaret("JSON");

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertInjectedLangAtCaret(null);
  }

  public void testConvertToAnnotationReferenceInjection() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          String bar() {
              String result = "ba<caret>r.xml";
              return result;
          }   \s
      }""");
    PsiLanguageInjectionHost injectionHost = myFixture.findElementByText("\"bar.xml\"", PsiLanguageInjectionHost.class);
    SmartPsiElementPointer<PsiLanguageInjectionHost> hostPtr = SmartPointerManager.createPointer(injectionHost);

    StoringFixPresenter storedFix = new StoringFixPresenter();

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector(), storedFix);
    myFixture.checkResult("""
                            class Foo {
                                String bar() {
                                    String result = "bar.xml";
                                    return result;
                                }   \s
                            }""");
    assertTrue(assertOneElement(getInjectedReferences()) instanceof FileReference);

    storedFix.process((hostPtr.getElement()));
    myFixture.checkResult("""
                            import org.intellij.lang.annotations.Language;

                            class Foo {
                                String bar() {
                                    @Language("file-reference") String result = "bar.xml";
                                    return result;
                                }   \s
                            }""");
    assertTrue(assertOneElement(getInjectedReferences()) instanceof FileReference);

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(getInjectedReferences());
  }

  public void testTernary() {
    myFixture.configureByText("Foo.java", """
      class Foo {
          void bar() {
              @org.intellij.lang.annotations.Language("encoding-reference")
              String cset = true ? "<error descr="Unknown encoding: 'cp1252345'">cp1252345</error>" : "utf-8";//
          }
      }""");
    myFixture.testHighlighting();
  }

  public void testEmptyLiteral() {
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
    PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    element = PsiTreeUtil.getParentOfType(element, PsiLanguageInjectionHost.class);
    assertNotNull(element);
    return InjectedReferencesContributor.getInjectedReferences(element);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new InjectedReferencesInspection());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.disableInspections(new InjectedReferencesInspection());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
