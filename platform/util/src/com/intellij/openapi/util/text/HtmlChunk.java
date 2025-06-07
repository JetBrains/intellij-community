// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.UnmodifiableHashMap;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An immutable representation of HTML node. Could be used as a DSL to quickly generate HTML strings.
 * 
 * @see HtmlBuilder
 */
public abstract class HtmlChunk {
  private static final class Empty extends HtmlChunk {
    private static final Empty INSTANCE = new Empty();
    
    @Override
    public boolean isEmpty() {
      return true;
    }
    
    @Override
    public void appendTo(@NotNull StringBuilder builder) {
    }
  }
  
  private static final class Text extends HtmlChunk {
    private final String myContent;

    private Text(String content) {
      myContent = content;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append(StringUtil.escapeXmlEntities(myContent).replaceAll("\n", "<br/>"));
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof Text && Objects.equals(myContent, ((Text)o).myContent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myContent);
    }
  }
  
  private static final class Raw extends HtmlChunk {
    private static final Pattern CLASS = Pattern.compile("class=([\"'])([A-Za-z\\-_0-9]+)\\1");
    private final String myContent;

    private Raw(String content) {
      myContent = content;
    }

    // Caution: works poorly
    @Override
    public @NotNull HtmlChunk applyStyles(@NotNull Map<@NotNull @NonNls String, @NotNull String> styles) {
      Matcher matcher = CLASS.matcher(myContent);
      StringBuffer result = new StringBuffer();
      while (matcher.find()) {
        String style = styles.get(matcher.group(2));
        if (style != null) {
          matcher.appendReplacement(result, "style=\"" + style + "\"");
        } else {
          matcher.appendReplacement(result, matcher.group());
        }
      }
      if (result.length() == 0) return this;
      matcher.appendTail(result);
      return new Raw(result.toString());
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append(myContent);
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof Raw && Objects.equals(myContent, ((Raw)o).myContent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myContent);
    }
  }
  
  static final class Fragment extends HtmlChunk {
    private final List<? extends HtmlChunk> myContent;

    Fragment(List<? extends HtmlChunk> content) {
      myContent = content;
    }

    @Override
    public @NotNull HtmlChunk applyStyles(@NotNull Map<@NotNull @NonNls String, @NotNull String> styles) {
      List<HtmlChunk> newChildren = null;
      for (HtmlChunk child : myContent) {
        HtmlChunk updated = child.applyStyles(styles);
        if (updated != child) {
          if (newChildren == null) {
            newChildren = new ArrayList<>(myContent);
          }
          newChildren.set(newChildren.indexOf(child), updated);
        }
      }
      if (newChildren != null) {
        return new Fragment(newChildren);
      }
      return this;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      for (HtmlChunk chunk : myContent) {
        chunk.appendTo(builder);
      }
    }

    @Override
    public @Nullable Icon findIcon(@NotNull String id) {
      for (HtmlChunk child : myContent) {
        Icon icon = child.findIcon(id);
        if (icon != null) {
          return icon;
        }
      }
      return null;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof Fragment && Objects.equals(myContent, ((Fragment)o).myContent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myContent);
    }
  }
  
  private static final class Nbsp extends HtmlChunk {
    private static final HtmlChunk ONE = new Nbsp(1);
    private final int myCount;

    private Nbsp(int count) {
      myCount = count;
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append(StringUtil.repeat("&nbsp;", myCount));
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof Nbsp && myCount == ((Nbsp)o).myCount;
    }

    @Override
    public int hashCode() {
      return myCount;
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
    public @NotNull HtmlChunk applyStyles(@NotNull Map<@NotNull @NonNls String, @NotNull String> styles) {
      String myClass = myAttributes.get("class");
      UnmodifiableHashMap<String, String> newAttributes = myAttributes;
      if (myClass != null) {
        String style = styles.get(myClass);
        if (style != null) {
          newAttributes = newAttributes.without("class");
          String existingStyle = newAttributes.get("style");
          if (existingStyle != null) {
            style = existingStyle.endsWith(";") ? existingStyle + " " + style 
                                                : existingStyle + "; " + style;
          }
          newAttributes = newAttributes.with("style", style);
        }
      }
      List<HtmlChunk> newChildren = null;
      for (HtmlChunk child : myChildren) {
        HtmlChunk updated = child.applyStyles(styles);
        if (updated != child) {
          if (newChildren == null) {
            newChildren = new ArrayList<>(myChildren);
          }
          newChildren.set(newChildren.indexOf(child), updated);
        }
      }
      if (newChildren != null) {
        return new Element(myTagName, newAttributes, newChildren);
      }
      return newAttributes == myAttributes ? this : new Element(myTagName, newAttributes, myChildren);
    }

    @Override
    public void appendTo(@NotNull StringBuilder builder) {
      builder.append('<').append(myTagName);
      myAttributes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
        builder.append(' ').append(entry.getKey());
        if (entry.getValue() != null) {
          builder.append("=\"").append(StringUtil.escapeXmlEntities(entry.getValue())).append('"');
        }
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
    public @NotNull Element attr(@NonNls String name, @NotNull String value) {
      return new Element(myTagName, myAttributes.with(name, value), myChildren);
    }

    /**
     * @param name attribute name
     * @param value attribute value
     * @return a new element that is like this element but has the specified attribute added or replaced
     */
    @Contract(pure = true)
    public @NotNull Element attr(@NonNls String name, int value) {
      return new Element(myTagName, myAttributes.with(name, Integer.toString(value)), myChildren);
    }

    /**
     * Adds an attribute without '=' sign and a value
     *
     * @param name attribute name
     * @return a new element that is like this element but has the specified attribute added or replaced
     */
    @Contract(pure = true)
    public @NotNull Element attr(@NonNls String name) {
      return new Element(myTagName, myAttributes.with(name, null), myChildren);
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
     * @param className name of style class
     * @return a new element that is like this element but has the specified class name
     */
    @Contract(pure = true)
    public @NotNull Element setClass(@NonNls String className) {
      return attr("class", className);
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

    @Override
    public @Nullable Icon findIcon(@NotNull String id) {
      for (HtmlChunk child : myChildren) {
        Icon icon = child.findIcon(id);
        if (icon != null) {
          return icon;
        }
      }
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Element element = (Element)o;
      return Objects.equals(myTagName, element.myTagName) &&
             Objects.equals(myAttributes, element.myAttributes) &&
             Objects.equals(myChildren, element.myChildren);
    }

    @Override
    public int hashCode() {
      int result = Objects.hashCode(myTagName);
      result = 31 * result + Objects.hashCode(myAttributes);
      result = 31 * result + Objects.hashCode(myChildren);
      return result;
    }
  }

  private static final class IconElement extends Element {
    private final @NotNull String myId;
    private final @NotNull Icon myIcon;

    private IconElement(@NotNull String id, @NotNull Icon icon) {
      super("icon", UnmodifiableHashMap.<String, String>empty().with("src", id), Collections.emptyList());
      myId = id;
      myIcon = icon;
    }

    @Override
    public @Nullable Icon findIcon(@NotNull String id) {
      if (id.equals(myId)) {
        return myIcon;
      }
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      IconElement element = (IconElement)o;
      return myId.equals(element.myId) && myIcon.equals(element.myIcon);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myId.hashCode();
      result = 31 * result + myIcon.hashCode();
      return result;
    }
  }

  /**
   * @param id id of icon to find
   * @return an icon with a given ID within this {@code HtmlChunk} tree; null if not found
   * @see #icon(String, Icon)
   */
  @Contract(pure = true)
  public @Nullable Icon findIcon(@NotNull @NonNls String id) {
    return null;
  }

  /**
   * Rewrites the HTML classes with the corresponding CSS styles.
   * Warning: this is a poor man replacement.
   * May not work as expected, especially if you are using raw elements. 
   * Use only if you control the HTML generation.
   * 
   * @param styles map where keys are class names and values are CSS definitions
   * @return a new {@link HtmlChunk} where known classes from the supplied Map 
   * are replaced with supplied styles; unknown classes are left intact.
   */
  @ApiStatus.Experimental
  @Contract(pure = true)
  public @NotNull HtmlChunk applyStyles(@NotNull Map<@NotNull @NonNls String, @NotNull String> styles) {
    return this;
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
   * @param id id of the icon (must be unique within the document)
   * @param icon an icon itself
   * @return an {@code <icon/>} HTML element with a given ID as src. The icon itself is stored and can be later retrieved via
   * {@link #findIcon(String)} call on the root {@code HtmlChunk}. This allows rendering HTML with icons using something like this:
   * <pre>{@code
   * val content = ... // get HtmlChunk
   * val editor = JEditorPane()
   * editor.editorKit = HTMLEditorKitBuilder()
   *   .withViewFactoryExtensions(
   *       ExtendableHTMLViewFactory.Extensions.icons(content))
   *   .build()
   * editor.text = content.toString()
   * }</pre>
   */
  @Contract(pure = true)
  public static @NotNull Element icon(@NotNull @NonNls String id, @NotNull Icon icon) {
    return new IconElement(id, icon);
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
  
  public static @NotNull Element font(int size) {
    return tag("font").attr("size", String.valueOf(size));
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
   * Substitutes a template where variables are wrapped with <code>$...$</code>
   * <p>
   *   Example:
   *   {@code HtmlChunk greeting = template("Hello, $user$!", Map.entry("user", text(userName).wrapWith("b")))}
   * </p>
   *
   * @param template template string. Parts outside of <code>$...$</code> are considered to be plain text.
   * @param substitutions substitution entries like (variableName -> chunk). Every variable mentioned in template
   *                      must be present in substitutions.
   * @return a {@code HtmlChunk} that represents a substituted template
   */
  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull HtmlChunk template(@NotNull @Nls String template,
                                            Map.Entry<@NotNull @NonNls String, @NotNull HtmlChunk>... substitutions) {
    String[] parts = template.split("\\$", -1);
    if (parts.length % 2 != 1) {
      throw new IllegalArgumentException("Invalid template (must have even number of '$' characters): " + template);
    }
    HtmlBuilder builder = new HtmlBuilder();
    Map<String, HtmlChunk> chunkMap = Stream.of(substitutions).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (i % 2 == 0) {
        builder.append(part);
      }
      else if (part.isEmpty()) {
        builder.append("$");
      }
      else {
        builder.append(Objects.requireNonNull(chunkMap.get(part), part));
      }
    }
    return builder.toFragment();
  }

  /**
   * Substitutes a template where single variable is wrapped with <code>$...$</code>.
   * See {@link #template(String, Map.Entry[])} for more details.
   *
   * @param template template string. Parts outside of <code>$...$</code> are considered to be plain text.
   * @param variable a variable name (between <code>$...$</code>)
   * @param substitution a substitution chunk for the variable.
   * @return a {@code HtmlChunk} that represents a substituted template
   */
  public static @NotNull HtmlChunk template(@NotNull @Nls String template,
                                            @NotNull @NonNls String variable, @NotNull HtmlChunk substitution) {
    return template(template, new AbstractMap.SimpleImmutableEntry<>(variable, substitution));
  }

  /**
   * Creates a chunk that represents a piece of raw HTML. Should be used with care!
   * The purpose of this method is to be able to externalize the text with embedded link. E.g.:
   * {@code "Click <a href=\"...\">here</a> for details"}. As an alternative, consider using
   * {@link #template(String, Map.Entry[])}.
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
    return link(target, text(text));
  }

  /**
   * Creates an element that represents an HTML link.
   * 
   * @param target link target (HREF)
   * @param text link text chunk
   * @return the Element that represents a link
   */
  @Contract(pure = true)
  public static @NotNull Element link(@NotNull @NonNls String target, @NotNull HtmlChunk text) {
    return new Element("a", UnmodifiableHashMap.<String, String>empty().with("href", target), Collections.singletonList(text));
  }

  /**
   * Creates an HTML entity (e.g. `&ndash;`)
   * @param htmlEntity entity
   * @return the HtmlChunk that represents the html entity
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk htmlEntity(@NotNull @NlsSafe String htmlEntity) {
    if (!htmlEntity.startsWith("&") && !htmlEntity.endsWith(";")) {
      throw new IllegalArgumentException("Not an entity: " + htmlEntity);
    }
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
   * @return the HtmlChunk that represents the fragment chunk
   */
  @Contract(pure = true)
  public static @NotNull HtmlChunk fragment(@NotNull HtmlChunk @NotNull ... chunks) {
    if (chunks.length == 0) {
      return empty();
    }
    return Arrays.stream(chunks).collect(toFragment());
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
