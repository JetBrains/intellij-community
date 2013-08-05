package org.intellij.plugins.intelliLang;

import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction;
import org.intellij.plugins.intelliLang.references.FileReferenceInjector;

/**
 * @author Dmitry Avdeev
 *         Date: 02.08.13
 */
public class ReferenceInjectionTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testInjectReference() throws Exception {

    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" \n" +
                                         "     xxx=\"ba<caret>r\"/>");
    assertNull(myFixture.getReferenceAtCaretPosition());
    assertTrue(new InjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));
    assertFalse(new UnInjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    assertNotNull(myFixture.getReferenceAtCaretPosition());
    assertFalse(new InjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));
    assertTrue(new UnInjectLanguageAction().isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()));

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(myFixture.getReferenceAtCaretPosition());
  }

  public void testSurviveSerialization() throws Exception {
    myFixture.configureByText("foo.xml", "<foo xmlns=\"http://foo.bar\" \n" +
                                         "     xxx=\"ba<caret>r\"/>");
    assertNull(myFixture.getReferenceAtCaretPosition());

    InjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile(), new FileReferenceInjector());
    assertNotNull(myFixture.getReferenceAtCaretPosition());

    //Configuration configuration = Configuration.getInstance();
    //Element element = configuration.getState();
    //configuration.loadState(element);

    ((PsiModificationTrackerImpl)PsiManager.getInstance(getProject()).getModificationTracker()).incCounter();
    assertNotNull(myFixture.getReferenceAtCaretPosition());

    UnInjectLanguageAction.invokeImpl(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNull(myFixture.getReferenceAtCaretPosition());
  }
}
