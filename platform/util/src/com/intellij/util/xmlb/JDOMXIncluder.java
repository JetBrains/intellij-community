// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.Stack;
import org.jdom.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

public final class JDOMXIncluder {
  private static final Logger LOG = Logger.getInstance(JDOMXIncluder.class);
  public static final PathResolver DEFAULT_PATH_RESOLVER = new PathResolver() {
    @Override
    public @NotNull URL resolvePath(@NotNull String relativePath, @Nullable URL base) throws MalformedURLException {
      return base == null ? new URL(relativePath) : new URL(base, relativePath);
    }
  };

  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String HREF = "href";
  @NonNls private static final String BASE = "base";
  @NonNls private static final String PARSE = "parse";
  @NonNls private static final String XML = "xml";
  @NonNls private static final String XPOINTER = "xpointer";

  private final boolean myIgnoreMissing;
  private final PathResolver myPathResolver;

  private JDOMXIncluder(boolean ignoreMissing, @NotNull PathResolver pathResolver) {
    myIgnoreMissing = ignoreMissing;
    myPathResolver = pathResolver;
  }

  /**
   * The original element will be mutated in place.
   */
  public static @NotNull Element resolveRoot(@NotNull Element original, URL base) throws XIncludeException, MalformedURLException {
    List<Element> resolved = new JDOMXIncluder(false, DEFAULT_PATH_RESOLVER).doResolve(original.clone(), base);
    if (resolved.size() == 1) {
      return resolved.get(0);
    }
    else if (resolved.size() > 1) {
      throw new XIncludeException("Tried to include multiple roots");
    }
    else {
      throw new XIncludeException("No root element");
    }
  }

  /**
   * @deprecated Use {@link #resolveRoot(Element, URL)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static @NotNull Document resolve(Document original, String base) throws XIncludeException, MalformedURLException {
    return new Document(resolveRoot(original.getRootElement(), new URL((base))));
  }

  /**
   * The original element will be mutated in place.
   *
   * (used by groovy).
   */
  @SuppressWarnings("unused")
  public static void resolveNonXIncludeElement(@NotNull Element original,
                                               @Nullable URL base,
                                               boolean ignoreMissing,
                                               @NotNull PathResolver pathResolver)
    throws XIncludeException, MalformedURLException {
    LOG.assertTrue(!isIncludeElement(original));

    Stack<URL> bases = new Stack<>();
    if (base != null) {
      bases.push(base);
    }
    new JDOMXIncluder(ignoreMissing, pathResolver).resolveNonXIncludeElement(original, bases);
  }

  public static @NotNull List<Element> resolve(@NotNull Element original, URL base) throws XIncludeException, MalformedURLException {
    return new JDOMXIncluder(false, DEFAULT_PATH_RESOLVER).doResolve(original, base);
  }

  private @NotNull List<Element> doResolve(@NotNull Element original, URL base) throws XIncludeException, MalformedURLException {
    Stack<URL> bases = new Stack<>();
    if (base != null) {
      bases.push(base);
    }
    return resolve(original, bases);
  }

  private static boolean isIncludeElement(Element element) {
    return element.getName().equals(INCLUDE) && element.getNamespace().equals(JDOMUtil.XINCLUDE_NAMESPACE);
  }

  private @NotNull List<Element> resolve(@NotNull Element original, @NotNull Stack<URL> bases) throws XIncludeException, MalformedURLException {
    if (isIncludeElement(original)) {
      return resolveXIncludeElement(original, bases);
    }
    else {
      resolveNonXIncludeElement(original, bases);
      return Collections.singletonList(original);
    }
  }

  private @NotNull List<Element> resolveXIncludeElement(@NotNull Element element, @NotNull Stack<URL> bases)
    throws XIncludeException, MalformedURLException {
    URL base = null;
    if (!bases.isEmpty()) {
      base = bases.peek();
    }

    String href = element.getAttributeValue(HREF);
    assert href != null : "Missing href attribute";

    String baseAttribute = element.getAttributeValue(BASE, Namespace.XML_NAMESPACE);
    if (baseAttribute != null) {
      base = new URL(baseAttribute);
    }

    final String parseAttribute = element.getAttributeValue(PARSE);
    if (parseAttribute != null) {
      LOG.assertTrue(parseAttribute.equals(XML), parseAttribute + " is not a legal value for the parse attribute");
    }

    URL remote = myPathResolver.resolvePath(href, base);
    assert !bases.contains(remote) : "Circular XInclude Reference to " + remote.toExternalForm();

    final Element fallbackElement = element.getChild("fallback", element.getNamespace());
    List<Element> remoteParsed = parseRemote(bases, remote, fallbackElement);
    if (!remoteParsed.isEmpty()) {
      remoteParsed = extractNeededChildren(element, remoteParsed);
    }

    int i = 0;
    for (; i < remoteParsed.size(); i++) {
      Element o = remoteParsed.get(i);
      if (isIncludeElement(o)) {
        List<Element> elements = resolveXIncludeElement(o, bases);
        remoteParsed.addAll(i, elements);
        i += elements.size() - 1;
        remoteParsed.remove(i);
      }
      else {
        resolveNonXIncludeElement(o, bases);
      }
    }

    for (Content content : remoteParsed) {
      content.detach();
    }
    return remoteParsed;
  }

  private static @NotNull List<Element> extractNeededChildren(@NotNull Element element, @NotNull List<Element> remoteElements) {
    final String xpointer = element.getAttributeValue(XPOINTER);
    if (xpointer == null) {
      return remoteElements;
    }

    Matcher matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer);
    if (!matcher.matches()) {
      throw new XIncludeException("Unsupported XPointer: " + xpointer);
    }

    String pointer = matcher.group(1);
    matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer);
    if (!matcher.matches()) {
      throw new XIncludeException("Unsupported pointer: " + pointer);
    }

    String rootTagName = matcher.group(1);

    assert remoteElements.size() == 1;
    Element e = remoteElements.get(0);
    if (!e.getName().equals(rootTagName)) {
      return Collections.emptyList();
    }

    String subTagName = matcher.group(2);
    if (subTagName != null) {
      // cut off the slash
      e = e.getChild(subTagName.substring(1));
      assert e != null;
    }
    return new ArrayList<>(e.getChildren());
  }

  private @NotNull List<Element> parseRemote(@NotNull Stack<URL> bases, @NotNull URL remote, @Nullable Element fallbackElement) {
    try {
      bases.push(remote);
      Element root = JDOMUtil.loadResource(remote);
      return resolve(root, bases);
    }
    catch (JDOMException e) {
      throw new XIncludeException(e);
    }
    catch (IOException e) {
      if (fallbackElement != null) {
        // TODO[yole] return contents of fallback element (we don't have fallback elements with content ATM)
        return Collections.emptyList();
      }
      if (myIgnoreMissing) {
        LOG.info(remote.toExternalForm() + " include ignored: " + e.getMessage());
        return Collections.emptyList();
      }
      throw new XIncludeException(e);
    }
    finally {
      bases.pop();
    }
  }

  private void resolveNonXIncludeElement(@NotNull Element original, @NotNull Stack<URL> bases)
    throws XIncludeException, MalformedURLException {
    List<Content> contentList = original.getContent();
    for (int i = contentList.size() - 1; i >= 0; i--) {
      Content content = contentList.get(i);
      if (content instanceof Element) {
        Element element = (Element)content;
        if (isIncludeElement(element)) {
          original.setContent(i, resolveXIncludeElement(element, bases));
        }
        else {
          // process child element to resolve possible includes
          resolveNonXIncludeElement(element, bases);
        }
      }
    }
  }

  public interface PathResolver {
    @NotNull
    URL resolvePath(@NotNull String relativePath, @Nullable URL base) throws MalformedURLException;
  }
}