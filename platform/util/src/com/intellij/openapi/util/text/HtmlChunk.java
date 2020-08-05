// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.UnmodifiableHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An immutable representation of HTML node. Could be used as a DSL to quickly generate HTML strings.
 * 
 * @see HtmlBuilder
 */
public abstract class HtmlChunk {
  private static class Text extends HtmlChunk {
    private final String myContent;

    private Text(String content) {
      myContent = content;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append(StringUtil.escapeXmlEntities(myContent));
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
  
  private static class Nbsp extends HtmlChunk {
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
    private static final Element BODY = tag("body");
    private static final Element HTML = tag("html");
    private static final Element BR = tag("br");
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
      myAttributes.forEach((attrKey, attrValue) -> {
        builder.append(' ').append(attrKey).append("=\"").append(StringUtil.escapeXmlEntities(attrValue)).append('"');
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
    public Element attr(@NonNls String name, String value) {
      return new Element(myTagName, myAttributes.with(name, value), myChildren);
    }

    /**
     * @param style CSS style specification
     * @return a new element that is like this element but has the specified style added or replaced
     */
    public Element style(@NonNls String style) {
      return attr("style", style);
    }

    /**
     * @param text text to add to the list of children (should not be escaped)
     * @return a new element that is like this element but has an extra text child
     */
    public Element addText(@NotNull @Nls String text) {
      return child(text(text));
    }

    /**
     * @param chunks chunks to add to the list of children
     * @return a new element that is like this element but has extra children
     */
    public Element children(@NotNull HtmlChunk @NotNull ... chunks) {
      if (myChildren.isEmpty()) {
        return new Element(myTagName, myAttributes, Arrays.asList(chunks));
      }
      List<HtmlChunk> newChildren = new ArrayList<>(myChildren.size() + chunks.length);
      newChildren.addAll(myChildren);
      Collections.addAll(myChildren, chunks);
      return new Element(myTagName, myAttributes, newChildren);
    }

    /**
     * @param chunk a chunk to add to the list of children
     * @return a new element that is like this element but has an extra child
     */
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
  public @NotNull Element wrapWith(@NotNull @NonNls String tagName) {
    return new Element(tagName, UnmodifiableHashMap.empty(), Collections.singletonList(this));
  }

  /**
   * @return a B element that wraps this element
   */
  public @NotNull Element bold() {
    return wrapWith("b");
  }

  /**
   * @return an I element that wraps this element
   */
  public @NotNull Element italic() {
    return wrapWith("i");
  }

  /**
   * @param tagName name of the tag
   * @return an empty tag
   */
  public static @NotNull Element tag(@NotNull @NonNls String tagName) {
    return new Element(tagName, UnmodifiableHashMap.empty(), Collections.emptyList());
  }

  /**
   * @return a &lt;div&gt; element
   */
  public static @NotNull Element div() {
    return Element.DIV;
  }

  /**
   * @return a &lt;div&gt; element with a specified style.
   */
  public static @NotNull Element div(@NotNull @NonNls String style) {
    return Element.DIV.style(style);
  }

  /**
   * @return a &lt;span&gt; element.
   */
  public static @NotNull Element span() {
    return Element.SPAN;
  }

  /**
   * @return a &lt;span&gt; element with a specified style.
   */
  public static @NotNull Element span(@NonNls @NotNull String style) {
    return Element.SPAN.style(style);
  }

  /**
   * @return a &lt;br&gt; element.
   */
  public static @NotNull Element br() {
    return Element.BR;
  }

  /**
   * @return a &lt;body&gt; element.
   */
  public static @NotNull Element body() {
    return Element.BODY;
  }

  /**
   * @return a &lt;html&gt; element.
   */
  public static @NotNull Element html() {
    return Element.HTML;
  }

  /**
   * Creates a HTML text node that represents a given number of non-breaking spaces
   * 
   * @param count number of non-breaking spaces
   * @return HtmlChunk that represents a sequence of non-breaking spaces
   */
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
   * @return HtmlChunk that represents a HTML text node.
   */
  public static @NotNull HtmlChunk text(@NotNull @Nls String text) {
    return new Text(text);
  }

  /**
   * Creates a chunk that represents a piece of raw HTML. Should be used with care!
   * The purpose of this method is to be able to externalize the text with embedded link. E.g.:
   * {@code "Click <a href=\"...\">here</a> for details"}.
   * 
   * @param rawHtml raw HTML content. It's the responsibility of the caller to balance tags and escape HTML entities.
   * @return the HtmlChunk that represents the supplied content.
   */
  public static @NotNull HtmlChunk raw(@NotNull @Nls String rawHtml) {
    return new Raw(rawHtml);
  }

  /**
   * Creates an element that represents a simple HTML link.
   * 
   * @param target link target (HREF)
   * @param text link text
   * @return the Element that represents a link
   */
  public static @NotNull Element link(@NotNull @NonNls String target, @NotNull @Nls String text) {
    return new Element("a", UnmodifiableHashMap.<String, String>empty().with("href", target), Collections.singletonList(text(text)));
  }

  /**
   * Appends the rendered HTML representation of this chunk to the supplied builder
   * 
   * @param builder builder to append to.
   */
  abstract public void appendTo(@NotNull StringBuilder builder);

  /**
   * @return the rendered HTML representation of this chunk.
   */
  @Override
  public @NlsSafe @NotNull String toString() {
    StringBuilder builder = new StringBuilder();
    appendTo(builder);
    return builder.toString(); 
  }
}
