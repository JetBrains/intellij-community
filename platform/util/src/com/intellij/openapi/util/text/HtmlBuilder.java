// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk.Element;
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
    if (!chunk.isEmpty()) {
      myChunks.add(chunk);
    }
    return this;
  }

  @Contract("_ -> this")
  public HtmlBuilder append(@NotNull HtmlBuilder builder) {
    if (this == builder) {
      throw new IllegalArgumentException("Cannot add builder to itself");
    }
    myChunks.addAll(builder.myChunks);
    return this;
  }

  /**
   * Appends a text chunk to this builder
   *
   * @param text text to append (must not be escaped by caller).
   *             All {@code '\n'} characters will be converted to {@code <br/>}
   * @return this builder
   */
  @Contract("_ -> this")
  public HtmlBuilder append(@NotNull @Nls String text) {
    return append(HtmlChunk.text(text));
  }

  /**
   * Appends a raw html text to this builder. Should be used with care.
   * The purpose of this method is to be able to externalize the text with embedded link. E.g.:
   * {@code "Click <a href=\"...\">here</a> for details"}.
   *
   * @param rawHtml raw HTML content. It's the responsibility of the caller to balance tags and escape HTML entities.
   * @return this builder
   */
  @Contract("_ -> this")
  public HtmlBuilder appendRaw(@NotNull @Nls String rawHtml) {
    return append(HtmlChunk.raw(rawHtml));
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
    return append(HtmlChunk.link(target, text));
  }

  /**
   * Appends a collection of chunks interleaving them with a supplied separator chunk
   * 
   * @param separator a separator chunk
   * @param children chunks to append
   * @return this builder
   */
  @Contract("_, _ -> this")
  public HtmlBuilder appendWithSeparators(@NotNull HtmlChunk separator, @NotNull Iterable<? extends HtmlChunk> children) {
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
   * Appends a non-breaking space ({@code &nbsp;} entity).
   * 
   * @return this builder
   */
  @Contract(" -> this")
  public HtmlBuilder nbsp() {
    return append(HtmlChunk.nbsp());
  }

  /**
   * Appends a series of non-breaking spaces ({@code &nbsp;} entities).
   * 
   * @param count number of non-breaking spaces to append
   * @return this builder
   */
  @Contract("_ -> this")
  public HtmlBuilder nbsp(int count) {
    return append(HtmlChunk.nbsp(count));
  }

  /**
   * Appends a line-break ({@code <br/>}).
   * 
   * @return this builder
   */
  @Contract(" -> this")
  public HtmlBuilder br() {
    return append(HtmlChunk.br());
  }

  /**
   * Appends a horizontal-rule ({@code <hr/>}).
   *
   * @return this builder
   */
  @Contract(" -> this")
  public HtmlBuilder hr() {
    return append(HtmlChunk.hr());
  }

  /**
   * Wraps this builder content with a specified tag
   * 
   * @param tag name of the tag to wrap with
   * @return a new Element object that contains chunks from this builder
   */
  @Contract(pure = true)
  public @NotNull Element wrapWith(@NotNull @NonNls String tag) {
    return HtmlChunk.tag(tag).children(myChunks.toArray(new HtmlChunk[0]));
  }

  /**
   * Wraps this builder content with a specified element
   * 
   * @param element name of the tag to wrap with
   * @return a new Element object that contains chunks from this builder
   */
  @Contract(pure = true)
  public @NotNull Element wrapWith(@NotNull HtmlChunk.Element element) {
    return element.children(myChunks.toArray(new HtmlChunk[0]));
  }

  /**
   * Wraps this builder content with a {@code <html><body></body></html>}.
   * 
   * @return a new HTML Element object that wraps BODY Element that contains 
   * chunks from this builder
   */
  @Contract(pure = true)
  public @NotNull Element wrapWithHtmlBody() {
    return wrapWith("body").wrapWith("html");
  }

  /**
   * @return true if no elements were added to this builder
   */
  @Contract(pure = true)
  public boolean isEmpty() {
    return myChunks.isEmpty();
  }

  /**
   * @return a fragment chunk that contains all the chunks of this builder.
   */
  public HtmlChunk toFragment() {
    return new HtmlChunk.Fragment(new ArrayList<>(myChunks));
  }
  
  /**
   * @return a rendered HTML representation of all the chunks in this builder.
   */
  @Override
  @Contract(pure = true)
  public @NlsSafe String toString() {
    StringBuilder sb = new StringBuilder();
    for (HtmlChunk chunk : myChunks) {
      chunk.appendTo(sb);
    }
    return sb.toString();
  }
}
