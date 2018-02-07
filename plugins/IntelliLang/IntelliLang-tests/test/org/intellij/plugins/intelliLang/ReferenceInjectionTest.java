package org.intellij.plugins.intelliLang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction;
import org.intellij.plugins.intelliLang.references.FileReferenceInjector;
import org.intellij.plugins.intelliLang.references.InjectedReferencesContributor;
import org.intellij.plugins.intelliLang.references.InjectedReferencesInspection;
import org.jdom.Element;

/**
 * @author Dmitry Avdeev
 */
public class ReferenceInjectionTest extends LightCodeInsightFixtureTestCase {
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
    myFixture.configureByText("Foo.java", "class Foo {\n" +
                                          "    String bar() {\n" +
                                          "        return \"ba<caret>r.xml\";\n" +
                                          "    }    \n" +
                                          "}");
    assertNull(getInjectedReferences());

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    PsiReference[] references = getInjectedReferences();
    PsiReference reference = assertOneElement(references);
    assertTrue(reference instanceof FileReference);

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(getInjectedReferences());
  }

  public void testInjectByAnnotation() {
    myFixture.configureByText("Foo.java", "class Foo {\n" +
                                          "    @org.intellij.lang.annotations.Language(\"file-reference\")\n" +
                                          "    String bar() {\n" +
                                          "       return \"<error descr=\"Cannot resolve file 'unknown.file'\">unknown.file</error>\";\n" +
                                          "    }  \n" +
                                          "}");
    myFixture.testHighlighting();
  }

  public void testTernary() {
    myFixture.configureByText("Foo.java", "class Foo {\n" +
                                          "    void bar() {\n" +
                                          "        @org.intellij.lang.annotations.Language(\"encoding-reference\")\n" +
                                          "        String cset = true ? \"<error descr=\"Unknown encoding: 'cp1252345'\">cp1252345</error>\" : \"utf-8\";//\n" +
                                          "    }\n" +
                                          "}");
    myFixture.testHighlighting();
  }

  public void testEmptyLiteral() {
    myFixture.configureByText("Foo.java", "class Foo {\n" +
                                          "    void bar() {\n" +
                                          "        @org.intellij.lang.annotations.Language(\"encoding-reference\")\n" +
                                          "        String cset = true ? <error descr=\"Unknown encoding: ''\">\"\"</error> : \"utf-8\";//\n" +
                                          "    }\n" +
                                          "}");
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
    myFixture.disableInspections(new InjectedReferencesInspection());
    super.tearDown();
  }
}
