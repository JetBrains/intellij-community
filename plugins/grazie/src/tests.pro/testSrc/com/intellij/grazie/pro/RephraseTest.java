package com.intellij.grazie.pro;

import ai.grazie.nlp.langs.Language;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.cloud.APIQueries;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@NeedsCloud
public class RephraseTest extends BaseTestCase {
  private static String getIntentionText() {
    return GrazieBundle.message("intention.rephrase.text");
  }

  private static final Map<String, List<String>> REPHRASING = Map.of(
    "horse", List.of("equine", "cavalry", "knight", "horsy"),
    "This horse raced past the barn",
    List.of(
      "That horse ran past the house",
      "A horse ran past the house",
      "The horse ran past the gate"
    ),

    "the barn", List.of("the house", "the gate"),

    "of popular music",
    List.of("of popular song", "of music that is popular", "of music", "of music that's popular"),

    "hester",
    List.of("hest", "ponni", "søt hest")
  );

  private void mockRephraser() {
    APIQueries.overrideRephraser((text, ranges, language, project) -> {
      return ranges.stream()
        .map(range -> new Pair<TextRange, String>(range, range.substring(text)))
        .map(p -> new Pair<>(p.getFirst(), REPHRASING.get(p.getSecond())))
        .filter(p -> p.getSecond() != null)
        .toList();
    }, getTestRootDisposable());
  }

  @Test
  public void testRephraseSentence() {
    String text =
      "The library remained quiet throughout the afternoon, as students focused on their studies while the rain gently tapped on the windows.";
    List<String> rephrasedSentences = rephrase(text);
    assertFalse(rephrasedSentences.isEmpty(), "No rephrased sentences found");
    rephrasedSentences.forEach(sentence -> assertNotEquals(text, sentence));
  }

  @Test
  public void testRephraseShortSentenceInRussian() {
    String text = "Почему лошади умееют так высоко прыгать?";
    List<String> rephrasedSentences = rephrase(text);
    assertFalse(rephrasedSentences.isEmpty(), "No rephrased sentences found");
    rephrasedSentences.forEach(sentence -> assertNotEquals(text, sentence));
  }

  @Test
  public void testNorwegian() {
    // Norwegian is not in the list of supported languages, but rephrasing is done via LLM which can easily understand it.
    mockRephraser();
    UiInterceptors.register(
      new ChooserInterceptor(
        List.of("hest", "ponni", "søt hest"),
        ".*ponni.*"
      )
    );
    checkIntention(
      "a.txt", getIntentionText(),
      "Some other text.\n\nHvorfor kan <selection><caret>hester</selection> hoppe så høyt? De er utrolig flinke til det!",
      "Some other text.\n\nHvorfor kan ponni hoppe så høyt? De er utrolig flinke til det!"
    );
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
      "a.txt", getIntentionText(),
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
    checkIntention(
      "a.html", getIntentionText(),
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
      "a.txt", getIntentionText(),
      "It's a type o<selection><caret>f popular music</selection>",
      "It's a type of music that is popular");
  }

  @Test
  public void testNoRephrasingForNonNaturalText() {
    checkIntentionIsAbsent("test.yaml", getIntentionText(), """
      rec<caret>eive:
        id: 123
        topic: K
        message: Hello World
      """);
  }

  private List<String> rephrase(String text) {
    var rephrasedSentences = ProgressManager.getInstance().runProcess(() -> {
      return APIQueries.getRephraser().rephrase(text, List.of(TextRange.allOf(text)), Language.RUSSIAN, getProject());
    }, new EmptyProgressIndicator());
    return ContainerUtil.flatMap(rephrasedSentences, s -> s.getSecond());
  }
}
