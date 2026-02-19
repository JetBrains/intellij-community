package com.intellij.grazie.pro;

import ai.grazie.nlp.langs.Language;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.text.Rule;
import com.intellij.grazie.text.TreeRuleChecker;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuleExampleTest extends BaseTestCase {

  @Test
  public void testNewlineInExample() {
    HighlightingTest.enableLanguages(Set.of(Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());
    Rule farewell = ContainerUtil.find(TreeRuleChecker.getRules(Language.GERMAN), r -> r.getGlobalId().contains("FAREWELL"));
    assertNotNull(farewell);
    String desc = farewell.getDescription();
    assertTrue(desc.contains("Grüße</b>⏎Anna"), () -> "Expected the newline to be visualized in the description: " + desc);
  }
}
