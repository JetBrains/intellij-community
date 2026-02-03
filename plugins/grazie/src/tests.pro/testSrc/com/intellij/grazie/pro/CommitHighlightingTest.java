package com.intellij.grazie.pro;

import org.junit.jupiter.api.Test;

class CommitHighlightingTest extends BaseTestCase {
  @NeedsCloud
  @Test
  void testAllEnginesAreHighlighted() {
    checkCommitMessageCloudHighlighting(
      """
        [synth] <GRAMMAR_ERROR descr="Grazie.MLEC.En.All: Missing punctuation">moreover</GRAMMAR_ERROR> include exception class <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.PREPOSITION_ISSUES">into</GRAMMAR_ERROR> diagnostics
        
        Mary lost
        control <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.POLARITY">at all</GRAMMAR_ERROR>.
        
        I need your <GRAMMAR_ERROR descr="NEED_HELPS">helps</GRAMMAR_ERROR> with LanguageTool.
        
        Kiss is terrible thing to waste.
        """
    );

    checkCommitMessageCloudHighlighting(
      "this is <GRAMMAR_ERROR descr=\"Grazie.MLEC.En.All: Incorrect article\">AN comment</GRAMMAR_ERROR> by MLEC."
    );
  }

  @NeedsCloud
  @Test
  public void testCommonConventions() {
    checkCommitMessageHighlighting("[grazie] allow splitting suggestions into several smaller changes, highlight their places");
    // Missing article checks are disabled by default in commit messages
    checkCommitMessageHighlighting("OC-22344 introduce lazy builder state");
    checkCommitMessageHighlighting("[swift] OC-22344 introduce lazy builder state");
    checkCommitMessageHighlighting("add examples to some rules");
    checkCommitMessageHighlighting("exclude English words");
    checkCommitMessageHighlighting("increase the priority, add the name and description");
    checkCommitMessageHighlighting("de: expand the checked news corpus");
    checkCommitMessageHighlighting("3 in commit messages won't hurt readability");
  }

  @Test
  public void testIrrelevantSentenceDecorationMistakeFiltering() {
    checkCommitMessageHighlighting("""
this is a rather important first line of the message

this is the second line

but this is a complex paragraph. we should report some casing & punctuation mistakes here (but we don't for now)
    """);
  }

  @Test
  public void testIrrelevantWarningFiltering() {
    checkCommitMessageHighlighting("this is a very important change");
  }

  private void checkCommitMessageHighlighting(String text) {
    checkCommitMessageHighlighting(text, text);
  }

  private void checkCommitMessageCloudHighlighting(String text) {
    runWithCloudProcessing(() -> {
      configureCommitMessage(text);
      myFixture.checkHighlighting();
    });
  }

  private void checkCommitMessageHighlighting(String text, String localText) {
    checkCommitMessageCloudHighlighting(text);
    initLocalProcessing();
    configureCommitMessage(localText);
    myFixture.checkHighlighting();
  }
}
