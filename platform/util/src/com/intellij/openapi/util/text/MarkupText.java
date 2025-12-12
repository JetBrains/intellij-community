// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an immutable text string with markup to display in the UI. It's used to display simple one-line text,
 * like a completion item or a menu item. It's not intended to provide any complex markup like text positioning, indentation, margins, etc.
 * Only semantic visual markup is supported.
 */
@ApiStatus.Experimental
@NotNullByDefault
public final class MarkupText {
  private static final MarkupText EMPTY = new MarkupText(Collections.emptyList());
  
  private final List<Fragment> fragments;

  /**
   * @param fragments list of fragments. They should be displayed one after another.
   */
  private MarkupText(List<Fragment> fragments) { this.fragments = fragments; }

  /**
   * @return list of fragments.
   */
  public List<Fragment> fragments() { return fragments; }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    MarkupText that = (MarkupText)obj;
    return Objects.equals(this.fragments, that.fragments);
  }

  @Override
  public int hashCode() {
    return fragments.hashCode();
  }

  /**
   * @return textual representation for debug purposes only. It somehow mimics Markdown, but it's not a real Markdown.
   */
  @Override
  public String toString() {
    return StringUtil.join(fragments, "");
  }

  /**
   * @return HTML representation of the MarkupText. It's assumed that a few CSS classes are defined; namely "grayed" and "error".
   */
  public HtmlChunk toHtmlChunk() {
    HtmlBuilder builder = new HtmlBuilder();
    fragments.forEach(fragment -> builder.append(fragment.toHtmlChunk()));
    return builder.toFragment();
  }

  /**
   * @return text of the MarkupText with all the markup stripped out.
   */
  public @Nls String toText() {
    return StringUtil.join(fragments, Fragment::text, "");
  }

  /**
   * @return true if this MarkupText is empty.
   */
  public boolean isEmpty() {
    return fragments.isEmpty();
  }

  /**
   * @return total length of the MarkupText in characters. 
   */
  public int length() {
    return fragments.stream().mapToInt(f -> f.text.length()).sum();
  }

  /**
   * Concatenates two MarkupTexts.
   * @param other MarkupText to be concatenated with this one
   * @return a MarkupText that contains all the fragments of both MarkupTexts.
   */
  public MarkupText concat(MarkupText other) {
    if (this.isEmpty()) return other;
    if (other.isEmpty()) return this;
    return new MarkupTextBuilder().append(this).append(other).build();
  }

  /**
   * Appends a text fragment to this MarkupText.
   * 
   * @param text text to be appended
   * @param kind text kind
   * @return a MarkupText that contains the content of this MarkupText and the specified text fragment.
   */
  public MarkupText concat(@Nls String text, Kind kind) {
    if (text.isEmpty()) return this;
    return new MarkupTextBuilder().append(this).append(text, kind).build();
  }

  /**
   * Change the formatting of the specified range
   * 
   * @param fromInclusive start of the range (offset in characters)
   * @param toExclusive end of the range (offset in characters)
   * @param kind new highlighting kind for the range. All old highlighting in the specified range will be replaced.
   * @return a MarkupText with updated highlighting
   * @throws IllegalArgumentException if the range is invalid
   */
  public MarkupText highlightRange(int fromInclusive, int toExclusive, Kind kind) {
    if (fromInclusive > toExclusive ||
        fromInclusive < 0) {
      throw new IllegalArgumentException("Invalid range: " + fromInclusive + ".." + toExclusive);
    }
    if (fromInclusive == toExclusive || isEmpty()) return this;
    MarkupTextBuilder newFragments = new MarkupTextBuilder();
    int start = 0;
    @Nls StringBuilder sb = new StringBuilder();
    for (Fragment fragment : fragments) {
      int end = start + fragment.text.length();
      if (end <= fromInclusive || start >= toExclusive) {
        newFragments.append(fragment);
      } else if (start < fromInclusive) {
        newFragments.append(fragment.text.substring(0, fromInclusive - start), fragment.kind);
        if (end >= toExclusive) {
          sb.append(fragment.text, fromInclusive - start, toExclusive - start);
          newFragments.append(sb.toString(), kind);
          sb.setLength(0);
          newFragments.append(fragment.text.substring(toExclusive - start), fragment.kind);
        } else {
          sb.append(fragment.text.substring(fromInclusive - start));
        }
      } else if (end >= toExclusive) {
        sb.append(fragment.text, 0, toExclusive - start);
        newFragments.append(sb.toString(), kind);
        sb.setLength(0);
        newFragments.append(fragment.text.substring(toExclusive - start), fragment.kind);
      } else {
        sb.append(fragment.text);
      }
      start = end;
    }
    if (start < fromInclusive || start < toExclusive) {
      throw new IllegalArgumentException(
        "Invalid range: " + fromInclusive + ".." + toExclusive + " for " + this + " (length=" + start + ")");
    }
    if (sb.length() > 0) {
      newFragments.append(sb.toString(), kind);
    }
    return newFragments.build();
  }

  /**
   * @param kind highlighting kind
   * @return a markup text whose highlighting is completely replaced with the specified one
   */
  public MarkupText highlightAll(Kind kind) {
    if (isEmpty()) return this;
    String text;
    if (fragments.size() == 1) {
      Fragment fragment = fragments.get(0);
      if (fragment.kind == kind) return this;
      text = fragment.text;
    } else {
      text = StringUtil.join(fragments, fragment -> fragment.text, "");
    }
    return new MarkupText(Collections.singletonList(new Fragment(text, kind)));
  }

  /**
   * Creates a MarkupText with a single plain text fragment.
   * 
   * @param text text to be displayed in the UI
   * @return a MarkupText with a single plain text fragment.
   */
  public static MarkupText plainText(@Nls String text) {
    if (text.isEmpty()) return EMPTY;
    return new MarkupText(Collections.singletonList(new Fragment(text, Kind.NORMAL)));
  }

  /**
   * @return a new builder to build the MarkupText instance efficiently.
   */
  public static MarkupTextBuilder builder() {
    return new MarkupTextBuilder();
  }

  /**
   * @return an empty MarkupText.
   */
  public static MarkupText empty() {
    return EMPTY;
  }

  /**
   * A builder for MarkupText.
   */
  public static final class MarkupTextBuilder {
    private final List<Fragment> fragments = new ArrayList<>();

    private MarkupTextBuilder() { }

    /**
     * Appends a normal text fragment to this builder.
     * 
     * @param text text to be appended
     * @return this builder
     */
    public MarkupTextBuilder append(@Nls String text) {
      return append(text, Kind.NORMAL);
    }

    /**
     * Appends a text fragment to this builder.
     * 
     * @param text text to be appended
     * @param kind text kind
     * @return this builder
     */
    public MarkupTextBuilder append(@Nls String text, Kind kind) {
      if (text.isEmpty()) return this;
      return append(new Fragment(text, kind));
    }

    /**
     * Appends a fragment to this builder.
     * 
     * @param fragment fragment to be appended
     * @return this builder
     */
    public MarkupTextBuilder append(Fragment fragment) {
      if (fragment.text.isEmpty()) return this;
      if (!fragments.isEmpty()) {
        Fragment last = fragments.get(fragments.size() - 1);
        if (last.kind == fragment.kind) {
          fragments.remove(fragments.size() - 1);
          fragment = new Fragment(last.text + fragment.text, fragment.kind);
        }
      }
      fragments.add(fragment);
      return this;
    }

    /**
     * Appends all the fragments of another MarkupText to this builder.
     * 
     * @param other MarkupText to be appended
     * @return this builder
     */
    public MarkupTextBuilder append(MarkupText other) {
      other.fragments.forEach(this::append);
      return this;
    }

    /**
     * @return a MarkupText that contains all the fragments of this builder.
     */
    public MarkupText build() {
      if (fragments.isEmpty()) {
        return EMPTY;
      }
      return new MarkupText(Collections.unmodifiableList(new ArrayList<>(fragments)));
    }
  }

  /**
   * Fragment kind that affects its formatting.
   */
  public enum Kind {
    /**
     * No special markup
     */
    NORMAL,

    /**
     * Emphasized text (likely, italic)
     */
    EMPHASIZED,

    /**
     * Strong text (likely, bold)
     */
    STRONG,

    /**
     * Underlined text
     */
    UNDERLINED,

    /**
     * Error text (likely, red)
     */
    ERROR,

    /**
     * Strikethrough text
     */
    STRIKEOUT,

    /**
     * Grayed out text
     */
    GRAYED
  }

  /**
   * A single fragment of the MarkupText. Fragments are concatenated one after another.
   */
  public static final class Fragment {
    @Nls private final String text;
    private final Kind kind;

    /**
     * Creates a new fragment.
     * 
     * @param text text of the fragment to be displayed in UI
     * @param kind fragment kind which affects its formatting
     */
    public Fragment(@Nls String text, Kind kind) {
      this.text = text;
      this.kind = kind;
    }

    /**
     * @return text of the fragment to be displayed in UI
     */
    public @Nls String text() { return text; }

    /**
     * @return fragment kind which affects its formatting
     */
    public Kind kind() { return kind; }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      Fragment that = (Fragment)obj;
      return Objects.equals(this.text, that.text) &&
             Objects.equals(this.kind, that.kind);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, kind);
    }

    /**
     * @return HTML representation of this fragment. It's assumed that a few CSS classes are defined; namely "grayed" and "error".
     */
    public HtmlChunk toHtmlChunk() {
      switch (kind) {
        case NORMAL:
          return HtmlChunk.text(text);
        case EMPHASIZED:
          return HtmlChunk.text(text).italic();
        case STRONG:
          return HtmlChunk.text(text).bold();
        case UNDERLINED:
          return HtmlChunk.text(text).wrapWith("u");
        case ERROR:
          return HtmlChunk.text(text).wrapWith(HtmlChunk.span().setClass("error"));
        case STRIKEOUT:
          return HtmlChunk.text(text).wrapWith("s");
        case GRAYED:
          return HtmlChunk.text(text).wrapWith(HtmlChunk.span().setClass("grayed"));
        default:
          throw new InternalError();
      }
    }

    /**
     * @return textual representation for debug purposes only. It somehow mimics Markdown, but it's not a real Markdown.
     */
    @Override
    public String toString() {
      switch (kind) {
        case NORMAL:
          return text;
        case EMPHASIZED:
          return "*" + text + "*";
        case STRONG:
          return "**" + text + "**";
        case UNDERLINED:
          return "_" + text + "_";
        case ERROR:
          return "!!!" + text + "!!!";
        case STRIKEOUT:
          return "~~" + text + "~~";
        case GRAYED:
          return "[" + text + "]";
        default:
          throw new InternalError();
      }
    }
  }
}
