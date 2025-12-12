package com.intellij.grazie.pro;

import ai.grazie.def.WordDefinition;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.ide.inspection.ai.WordDefinitions;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NeedsCloud
public class WordDefinitionTest extends BaseTestCase {

  private static String getIntentionText() {
    return GrazieBundle.message("action.intention.word.definition.text");
  }

  @Test
  public void testEnglishWord() {
    assertDefinition("The code is in <caret>Python.", "programming language");
  }

  @Test
  public void testGermanWord() {
    HighlightingTest.enableLanguages(Set.of(Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());
    assertDefinition("Das ist mir an der Mauer <caret>passiert.", "sich vorbeibewegen");
  }

  @Test
  public void testPhrase() {
    assertDefinition(
      "Hello, this is English. I met <selection><caret>Steven Pinker</selection> yesterday.", "cognitive psychologist");
  }

  @Test
  public void testNoDefinitionForNonNaturalText() {
    checkIntentionIsAbsent("test.yaml", getIntentionText(), """
      rec<caret>eive:
        id: 123
        topic: K
        message: Hello World
      """);
  }

  private void assertDefinition(String text, String contains) {
    myFixture.configureByText("a.txt", text);
    myFixture.findSingleIntention("Show word definitions");
    var descriptorRef = new Ref<WordDefinitions.WordDefinitionWithLanguage>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      descriptorRef.set(WordDefinitions.findDefinitions(myFixture.getEditor(), myFixture.getFile()));
    });
    var descriptor = descriptorRef.get();
    assertNotNull(descriptor);
    WordDefinition.Definition[] defs = descriptor.definition().getDefinitions();
    assertTrue(defs[0].getDefinition().contains(contains), () -> Arrays.toString(defs));
  }
}
