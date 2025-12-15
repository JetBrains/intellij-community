package com.intellij.grazie.pro;

import ai.grazie.nlp.langs.Language;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.cloud.APIQueries;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@NeedsCloud
public class RephraseTest extends BaseTestCase {
  private static final String INTENTION_TEXT = GrazieBundle.message("intention.rephrase.text");

  private static final Map<String, List<String>> REPHRASING = Map.of(
    "This horse raced past the barn",
    List.of(
      "This equine raced past the barn",
      "This cavalry raced past the barn",
      "This knight raced past the barn",
      "This horsy raced past the barn",
      "That horse ran past the house",
      "A horse ran past the house",
      "The horse ran past the gate"
    ),

    "The horse raced past the barn",
    List.of(
      "The horse raced past the house",
      "The horse raced past the gate"
    ),

    "It's a type of popular music",
    List.of(
      "It's a type of popular song",
      "It's a type of music that is popular",
      "It's a type of music",
      "It's a type of music that's popular"
    )
  );

  private void mockRephraser() {
    APIQueries.overrideRephraser((text, range, language, project) -> {
      List<String> rephrased = REPHRASING.get(text);
      if (rephrased == null) throw new IllegalArgumentException("Unable to rephrase: " + text);
      return rephrased;
    }, getTestRootDisposable());
  }

  @Test
  public void testRephraseSentence() {
    String text =
      "The library remained quiet throughout the afternoon, as students focused on their studies while the rain gently tapped on the windows.";
    List<String> rephrasedSentences = ProgressManager.getInstance().runProcess(() -> {
      return APIQueries.getRephraser().rephrase(text, TextRange.allOf(text), Language.ENGLISH, getProject());
    }, new EmptyProgressIndicator());
    assertFalse(rephrasedSentences.isEmpty(), "No rephrased sentences found");
    rephrasedSentences.forEach(sentence -> assertNotEquals(text, sentence));
  }

  @Test
  public void testRephraseShortSentenceInRussian() {
    String text = "Почему лошади умееют так высоко прыгать?";
    List<String> rephrasedSentences = ProgressManager.getInstance().runProcess(() -> {
      return APIQueries.getRephraser().rephrase(text, TextRange.allOf(text), Language.RUSSIAN, getProject());
    }, new EmptyProgressIndicator());
    assertFalse(rephrasedSentences.isEmpty(), "No rephrased sentences found");
    rephrasedSentences.forEach(sentence -> assertNotEquals(text, sentence));
  }

  @Test
  public void testWord() {
    mockRephraser();
    UiInterceptors.register(
      new ChooserInterceptor(
        List.of(
          "equine", "cavalry", "knight", "horsy",
          "That horse ran past the house", "A horse ran past the house", "The horse ran past the gate"
        ),
        ".*knight.*"
      )
    );
    checkIntention(
      "a.txt", INTENTION_TEXT,
      "This <caret>horse raced past the barn",
      "This knight raced past the barn"
    );
  }

  @Test
  public void testRange() {
    mockRephraser();
    UiInterceptors.register(
      new ChooserInterceptor(
        List.of("the house", "the gate"),
        "the house"
      )
    );
    mockRephraser();
    checkIntention(
      "a.html", INTENTION_TEXT,
      "<i>The horse raced past <selection><caret>the barn</selection></i>",
      "<i>The horse raced past the house</i>"
    );
  }

  @Test
  public void testRangeWordMiddle() {
    mockRephraser();
    UiInterceptors.register(
      new ChooserInterceptor(
        List.of("of popular song", "of music that is popular", "of music", "of music that's popular"),
        ".*that is.*"
      )
    );
    checkIntention(
      "a.txt", INTENTION_TEXT,
      "It's a type o<selection><caret>f popular music</selection>",
      "It's a type of music that is popular");
  }
}
