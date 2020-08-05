// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple builder to create HTML fragments. It encapsulates a series of {@link HtmlChunk} objects.  
 */
public final class HtmlBuilder {
  private final List<HtmlChunk> myChunks = new ArrayList<>();

  /**
   * Appends a new chunk to this builder
   * 
   * @param chunk chunk to append
   * @return this builder
   */
  @Contract("_ -> this")
  public HtmlBuilder append(@NotNull HtmlChunk chunk) {
    myChunks.add(chunk);
    return this;
  }

  /**
   * Appends a text chunk to this builder
   *
   * @param text text to append (must not be escaped by caller)
   * @return this builder
   */
  @Contract("_ -> this")
  public HtmlBuilder append(@NotNull @Nls String text) {
    myChunks.add(HtmlChunk.text(text));
    return this;
  }

  /**
   * Appends a raw html text to this builder. Should be sued with care.
   * The purpose of this method is to be able to externalize the text with embedded link. E.g.:
   * {@code "Click <a href=\"...\">here</a> for details"}.
   *
   * @param rawHtml raw HTML content. It's the responsibility of the caller to balance tags and escape HTML entities.
   * @return this builder
   */
  @Contract("_ -> this")
  public HtmlBuilder appendRaw(@NotNull @Nls String rawHtml) {
    myChunks.add(HtmlChunk.raw(rawHtml));
    return this;
  }

  /**
   * Appends a link element to this builder
   * 
   * @param target link target (href)
   * @param text link text
   * @return this builder
   */
  @Contract("_, _ -> this")
  public HtmlBuilder appendLink(@NotNull @NonNls String target, @NotNull @Nls String text) {
    myChunks.add(HtmlChunk.link(target, text));
    return this;
  }

  /**
   * Appends a collection of chunks interleaving them with a supplied separator chunk
   * 
   * @param separator a separator chunk
   * @param children chunks to append
   * @return this builder
   */
  @Contract("_, _ -> this")
  public HtmlBuilder appendWithSeparators(@NotNull HtmlChunk separator, @NotNull Iterable<HtmlChunk> children) {
    boolean first = true;
    for (HtmlChunk child : children) {
      if (!first) {
        append(separator);
      }
      first = false;
      append(child);
    }
    return this;
  }

  /**
   * Appends a series of non-breaking spaces ({@code &nbsp;} entities).
   * 
   * @param count number of non-breaking spaces to append
   * @return this builder
   */
  @Contract("_ -> this")
  public HtmlBuilder nbsp(int count) {
    myChunks.add(HtmlChunk.nbsp(count));
    return this;
  }

  /**
   * Appends a line-break ({@code <br/>}).
   * 
   * @return this builder
   */
  @Contract(" -> this")
  public HtmlBuilder br() {
    myChunks.add(HtmlChunk.br());
    return this;
  }

  /**
   * Wraps this builder content with a specified tag
   * 
   * @param tag name of the tag to wrap with
   * @return a new Element object that contains elements from this builder
   */
  public HtmlChunk.Element wrapWith(@NotNull @NonNls String tag) {
    return HtmlChunk.tag(tag).children(myChunks.toArray(new HtmlChunk[0]));
  }

  /**
   * @return true if no elements were added to this builder
   */
  public boolean isEmpty() {
    return myChunks.isEmpty();
  }

  /**
   * @return a rendered HTML representation of all the chunks in this builder.
   */
  @Override
  public @NlsSafe String toString() {
    StringBuilder sb = new StringBuilder();
    for (HtmlChunk chunk : myChunks) {
      chunk.appendTo(sb);
    }
    return sb.toString();
  }
}
