package com.intellij.grazie.rule;

import ai.grazie.nlp.tokenizer.Tokenizer;
import ai.grazie.nlp.tokenizer.sentence.StandardSentenceTokenizer;
import ai.grazie.text.Text;
import ai.grazie.text.exclusions.Exclusion;
import ai.grazie.text.exclusions.ExclusionUtilsKt;
import ai.grazie.text.exclusions.SentenceWithExclusions;
import com.intellij.grazie.text.TextContent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import kotlin.ranges.IntRange;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class SentenceTokenizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.grazie.text.SentenceTokenizer");
  private static final Key<List<Sentence>> tokenized =
    Key.create("grazie pro sentence tokenization");

  public static List<Tokenizer.Token> tokenize(@NotNull CharSequence text) {
    CharSequence bombed = bombed(text);
    return ContainerUtil.map(
      StandardSentenceTokenizer.Companion.getDefault().tokenRanges(bombed),
      r -> new Tokenizer.Token(text.subSequence(r.getStart(), r.getEndExclusive()).toString(), new IntRange(r.getStart(), r.getEndInclusive()))
    );
  }

  private static CharSequence bombed(@NotNull CharSequence text) {
    //noinspection UnstableApiUsage
    if (ProgressManager.getGlobalProgressIndicator() == null && Cancellation.currentJob() == null && !ApplicationManager.getApplication().isDispatchThread()) {
      LOG.error("Sentence tokenizer should be called with a cancellable indicator");
    }
    return new StringUtil.BombedCharSequence(text) {
      @Override
      protected void checkCanceled() {
        ProgressManager.checkCanceled();
      }
    };
  }

  public static List<Sentence> tokenize(@NotNull TextContent content) {
    List<Sentence> result = content.getUserData(tokenized);
    if (result == null) {
      List<Exclusion> exclusions = rangeExclusions(content, TextRange.from(0, content.length()));
      Text text = ExclusionUtilsKt.withExclusions(new Text(bombed(content)), exclusions);
      result = ContainerUtil.map(StandardSentenceTokenizer.Companion.getDefault().tokenize(text), SentenceTokenizer::toSentence);
      content.putUserData(tokenized, result);
    }
    return result;
  }

  private static Sentence toSentence(Tokenizer.Token token) {
    var range = new ai.grazie.text.TextRange(token.getRange().getFirst(), token.getRange().getLast() + 1);
    @SuppressWarnings({"unchecked", "rawtypes"}) List<Exclusion> exclusions = (List) ExclusionUtilsKt.getExclusions(token.getText());
    return new Sentence(range.getStart(), token.getToken(), exclusions);
  }

  public static List<Exclusion> rangeExclusions(TextContent textContent, TextRange range) {
    return StreamEx.of(exclusionsInRange(range, textContent.markupOffsets(), Exclusion.Kind.Markup))
      .append(unknownOffsets(textContent, range))
      .sortedBy(Exclusion::getOffset)
      .toList();
  }

  private static List<Exclusion> exclusionsInRange(TextRange range, int[] offsets, Exclusion.Kind kind) {
    return IntStreamEx.of(offsets).filter(range::contains).mapToObj(offset -> new Exclusion(offset - range.getStartOffset(), kind)).toList();
  }

  private static final Method unknownOffsets = ReflectionUtil.getMethod(TextContent.class, "unknownOffsets");

  private static List<Exclusion> unknownOffsets(TextContent textContent, TextRange range) {
    if (unknownOffsets != null) {
      try {
        return exclusionsInRange(range, (int[]) unknownOffsets.invoke(textContent), Exclusion.Kind.Unknown);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }

    List<Exclusion> exclusions = new ArrayList<>();
    new Object() {
      private void findUnknownOffsets(int start, int end) {
        if (textContent.hasUnknownFragmentsIn(new TextRange(start, end))) {
          if (start == end) {
            exclusions.add(new Exclusion(start - range.getStartOffset(), Exclusion.Kind.Unknown));
          } else {
            int middle = (start + end) / 2;
            findUnknownOffsets(start, middle);
            findUnknownOffsets(middle + 1, end);
          }
        }
      }
    }.findUnknownOffsets(range.getStartOffset(), range.getEndOffset());
    return exclusions;
  }

  public record Sentence(int start, String text, List<Exclusion> exclusions) {
    public int end() {
      return start + text.length();
    }
    public SentenceWithExclusions swe() {
      return new SentenceWithExclusions(text, exclusions);
    }

    public @Nullable SentenceWithExclusions stubbedSwe() {
      String text = swe().stubExclusions();
      return text == null ? null : new SentenceWithExclusions(text, List.of());
    }
  }
}
