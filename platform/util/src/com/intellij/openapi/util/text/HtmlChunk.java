// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.UnmodifiableHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collector;

/**
 * An immutable representation of HTML node. Could be used as a DSL to quickly generate HTML strings.
 * 
 * @see HtmlBuilder
 */
public abstract class HtmlChunk {
  private static class Empty extends HtmlChunk {
    private static final Empty INSTANCE = new Empty();
    
    @Override
    public boolean isEmpty() {
      return true;
    }
    
    @Override
    public void appendTo(@NotNull StringBuilder builder) {
    }
  }
  
  private static class Text extends HtmlChunk {
    private final String myContent;

    private Text(String content) {
      myContent = content;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append(StringUtil.escapeXmlEntities(myContent).replaceAll("\n", "<br/>"));
    }
  }
  
  private static class Raw extends HtmlChunk {
    private final String myContent;

    private Raw(String content) {
      myContent = content;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append(myContent);
    }
  }
  
  static class Fragment extends HtmlChunk {
    private final List<? extends HtmlChunk> myContent;

    Fragment(List<? extends HtmlChunk> content) {
      myContent = content;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      for (HtmlChunk chunk : myContent) {
        chunk.appendTo(builder);
      }
    }
  }
  
  private static class Nbsp extends HtmlChunk {
    private static final HtmlChunk ONE = new Nbsp(1);
    private final int myCount;

    private Nbsp(int count) {
      myCount = count;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append(StringUtil.repeat("&nbsp;", myCount));
    }
  }

  public static class Element extends HtmlChunk {
    private static final Element HEAD = tag("head");
    private static final Element BODY = tag("body");
    private static final Element HTML = tag("html");
    private static final Element BR = tag("br");
    private static final Element UL = tag("ul");
    private static final Element LI = tag("li");
    private static final Element HR = tag("hr");
    private static final Element P = tag("p");
    private static final Element DIV = tag("div");
    private static final Element SPAN = tag("span");

    private final String myTagName;
    private final UnmodifiableHashMap<String, String> myAttributes;
    private final List<HtmlChunk> myChildren;

    private Element(String name,
                    UnmodifiableHashMap<String, String> attributes,
                    List<HtmlChunk> children) {
      myTagName = name;
      myAttributes = attributes;
      myChildren = children;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append('<').append(myTagName);
      myAttributes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
        builder.append(' ').append(entry.getKey()).append("=\"").append(StringUtil.escapeXmlEntities(entry.getValue())).append('"');
      });
      if (myChildren.isEmpty()) {
        builder.append("/>");
      } else {
        builder.append(">");
        for (HtmlChunk child : myChildren) {
          child.appendTo(builder);
        }
        builder.append("</").append(myTagName).append(">");
      }
    }

    /**
     * @param name attribute name
     * @param value attribute value
     * @return a new element that is like this element but has the specified attribute added or replaced
     */
    @Contract(pure = true)
    public @NotNull Element attr(@NonNls String name, String value) {
      return new Element(myTagName, myAttributes.with(name, value), myChildren);
    }

    @Contract(pure = true)
    public @NotNull Element attr(@NonNls String name, int value) {
      return new Element(myTagName, myAttributes.with(name, Integer.toString(value)), myChildren);
    }

    /**
     * @param style CSS style specification
     * @return a new element that is like this element but has the specified style added or replaced
     */
    @Contract(pure = true)
    public @NotNull Element style(@NonNls String style) {
      return attr("style", style);
    }

    /**
     * @param text text to add to the list of children (should not be escaped)
     * @return a new element that is like this element but has an extra text child
     */
    @Contract(pure = true)
    public @NotNull Element addText(@NotNull @Nls String text) {
      return child(text(text));
    }

    /**
     * @param text text to add to the list of children (should not be escaped)
     * @return a new element that is like this element but has an extra text child
     */
    @Contract(pure = true)
    public @NotNull Element addRaw(@NotNull @Nls String text) {
      return child(raw(text));
    }

    /**
     * @param chunks chunks to add to the list of children
     * @return a new element that is like this element but has extra children
     */
    @Contract(pure = true)
    public @NotNull Element children(@NotNull HtmlChunk @NotNull ... chunks) {
      if (myChildren.isEmpty()) {
        return new Element(myTagName, myAttributes, Arrays.asList(chunks));
      }
      List<HtmlChunk> newChildren = new ArrayList<>(myChildren.size() + chunks.length);
      newChildren.addAll(myChildren);
      Collections.addAll(newChildren, chunks);
      return new Element(myTagName, myAttributes, newChildren);
    }

    /**
     * @param chunks chunks to add to the list of children
     * @return a new element that is like this element but has extra children
     */
    @Contract(pure = true)
    public @NotNull Element children(@NotNull List<? extends HtmlChunk> chunks) {
      if (myChildren.isEmpty()) {
        return new Element(myTagName, myAttributes, new ArrayList<>(chunks));
      }
      List<HtmlChunk> newChildren = new ArrayList<>(myChildren.size() + chunks.size());
      newChildren.addAll(myChildren);
      newChildren.addAll(chunks);
      return new Element(myTagName, myAttributes, newChildren);
    }

    /**
     * @param chunk a chunk to add to the list of children
     * @return a new element that is like this element but has an extra child
     */
    @Contract(pure = true)
    public @NotNull Element child(@NotNull HtmlChunk chunk) {
      if (myChildren.isEmpty()) {
        return new Element(myTagName, myAttributes, Collections.singletonList(chunk));
      }
      List<HtmlChunk> newChildren = new ArrayList<>(myChildren.size() + 1);
      newChildren.addAll(myChildren);
      newChildren.add(chunk);
      return new Element(myTagName, myAttributes, newChildren);
    } 
  }

  /**
   * @param tagName name of the tag to wrap with
   * @return an element that wraps this element
   */
  @Contract(pure = true)
  public @NotNull Element wrapWith(@NotNull @NonNls String tagName) {
    return new Element(tagName, UnmodifiableHashMap.empty(), Collections.singletonList(this));
  }

  /**
   * @param element element to wrap with
   * @return an element that wraps this element
   */
  @Contract(pure = true)
  public @NotNull Element wrapWith(@NotNull Element element) {
    return element.child(this);
  }

  /**
   * @return a CODE element that wraps this element
   */
  @Contract(pure = true)
  public @NotNull Element code() {
    return wrapWith("code");
  }

  /**
   * @return a B element that wraps this element
   */
  @Contract(pure = true)
  public @NotNull Element bold() {
    return wrapWith("b");
  }

  /**
   * @return an I element that wraps this element
   */
  @Contract(pure = true)
  public @NotNull Element italic() {
    return wrapWith("i");
  }

  /**
   * @return an S element that wraps this element
   */
  @Contract(pure = true)
  public @NotNull Element strikethrough() {
    return wrapWith("s");
  }

  /**
   * @param tagName name of the tag
   * @return an empty tag
   */
  @Contract(pure = true)
  public static @NotNull Element tag(@NotNull @NonNls String tagName) {
    return new Element(tagName, UnmodifiableHashMap.empty(), Collections.emptyList());
  }

  /**
   * @return a &lt;div&gt; element
   */
  @Contract(pure = true)
  public static @NotNull Element div() {
    return Element.DIV;
  }

  /**
   * @return a &lt;div&gt; element with a specified style.
   */
  @Contract(pure = true)
  public static @NotNull Element div(@NotNull @NonNls String style) {
    return Element.DIV.style(style);
  }

  /**
   * @return a &lt;span&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element span() {
    return Element.SPAN;
  }

  /**
   * @return a &lt;span&gt; element with a specified style.
   */
  @Contract(pure = true)
  public static @NotNull Element span(@NonNls @NotNull String style) {
    return Element.SPAN.style(style);
  }

  /**
   * @return a &lt;br&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element br() {
    return Element.BR;
  }

  /**
   * @return a &lt;li&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element li() {
    return Element.LI;
  }

  /**
   * @return a &lt;ul&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element ul() {
    return Element.UL;
  }

  /**
   * @return a &lt;hr&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element hr() {
    return Element.HR;
  }

  /**
   * @return a &lt;p&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element p() {
    return Element.P;
  }

  /**
   * @return a &lt;body&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element head() {
    return Element.HEAD;
  }

  public static @NotNull Element styleTag(@NonNls @NotNull String style) {
    return tag("style").addRaw(style); //NON-NLS
  }

  public static @NotNull Element font(@NonNls @NotNull String color) {
    return tag("font").attr("color", color);
  }

  /**
   * @return a &lt;body&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element body() {
    return Element.BODY;
  }

  /**
   * @return a &lt;html&gt; element.
   */
  @Contract(pure = true)
  public static @NotNull Element html() {
    return Element.HTML;
  }

  /**
   * Creates a HTML text node that represents a non-breaking space ({@code &nbsp;}).
   * 
   * @return HtmlChunk that represents a sequence of non-breaking spaces
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk nbsp() {
    return Nbsp.ONE;
  }

  /**
   * Creates a HTML text node that represents a given number of non-breaking spaces
   * 
   * @param count number of non-breaking spaces
   * @return HtmlChunk that represents a sequence of non-breaking spaces
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk nbsp(int count) {
    if (count <= 0) {
      throw new IllegalArgumentException();
    }
    return new Nbsp(count);
  }

  /**
   * Creates a HTML text node
   * 
   * @param text text to display (no escaping should be done by caller). 
   *             All {@code '\n'} characters will be converted to {@code <br/>}
   * @return HtmlChunk that represents a HTML text node.
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk text(@NotNull @Nls String text) {
    return text.isEmpty() ? empty() : new Text(text);
  }

  /**
   * @return an empty HtmlChunk
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk empty() {
    return Empty.INSTANCE;
  }

  /**
   * Creates a chunk that represents a piece of raw HTML. Should be used with care!
   * The purpose of this method is to be able to externalize the text with embedded link. E.g.:
   * {@code "Click <a href=\"...\">here</a> for details"}.
   * 
   * @param rawHtml raw HTML content. It's the responsibility of the caller to balance tags and escape HTML entities.
   * @return the HtmlChunk that represents the supplied content.
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk raw(@NotNull @Nls String rawHtml) {
    return rawHtml.isEmpty() ? empty() : new Raw(rawHtml);
  }

  /**
   * Creates an element that represents a simple HTML link.
   * 
   * @param target link target (HREF)
   * @param text link text
   * @return the Element that represents a link
   */
  @Contract(pure = true)
  public static @NotNull Element link(@NotNull @NonNls String target, @NotNull @Nls String text) {
    return new Element("a", UnmodifiableHashMap.<String, String>empty().with("href", target), Collections.singletonList(text(text)));
  }

  /**
   * Creates an html entity (e.g. `&ndash;`)
   * @param htmlEntity entity
   * @return the HtmlChunk that represents the html entity
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk htmlEntity(@NotNull @NlsSafe String htmlEntity) {
    return raw(htmlEntity);
  }

  /**
   * @return true if this chunk is empty (doesn't produce any text) 
   */
  @Contract(pure = true)
  public boolean isEmpty() {
    return false;
  }
  

  /**
   * Appends the rendered HTML representation of this chunk to the supplied builder
   * 
   * @param builder builder to append to.
   */
  public abstract void appendTo(@NotNull StringBuilder builder);

  /**
   * @return the rendered HTML representation of this chunk.
   */
  @Override
  @Contract(pure = true)
  public @NlsSafe @NotNull String toString() {
    StringBuilder builder = new StringBuilder();
    appendTo(builder);
    return builder.toString();
  }

  /**
   * @return the collector that collects a stream of HtmlChunks to the fragment chunk.
   */
  @Contract(pure = true)
  public static @NotNull Collector<HtmlChunk, ?, HtmlChunk> toFragment() {
    return Collector.of(HtmlBuilder::new, HtmlBuilder::append, HtmlBuilder::append, HtmlBuilder::toFragment);
  }

  /**
   * @param separator a chunk that should be used as a delimiter
   * @return the collector that collects a stream of HtmlChunks to the fragment chunk.
   */
  @Contract(pure = true)
  public static @NotNull Collector<HtmlChunk, ?, HtmlChunk> toFragment(HtmlChunk separator) {
    return Collector.of(HtmlBuilder::new, (hb, c) -> {
      if (!hb.isEmpty()) {
        hb.append(separator);
      }
      hb.append(c);
    }, (hb1, hb2) -> {
      if (!hb1.isEmpty()) {
        hb1.append(separator);
      }
      return hb1.append(hb2);
    }, HtmlBuilder::toFragment);
  }
}
