// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.Stack;
import org.jdom.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JDOMXIncluder {
  private static final Logger LOG = Logger.getInstance(JDOMXIncluder.class);
  public static final PathResolver DEFAULT_PATH_RESOLVER = new PathResolver() {
    @NotNull
    @Override
    public URL resolvePath(@NotNull String relativePath, @Nullable URL base) throws MalformedURLException {
      return base == null ? new URL(relativePath) : new URL(base, relativePath);
    }
  };

  @NonNls private static final String HTTP_WWW_W3_ORG_2001_XINCLUDE = "http://www.w3.org/2001/XInclude";
  @NonNls private static final String XI = "xi";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String HREF = "href";
  @NonNls private static final String BASE = "base";
  @NonNls private static final String PARSE = "parse";
  @NonNls private static final String XML = "xml";
  @NonNls private static final String XPOINTER = "xpointer";

  public static final Namespace XINCLUDE_NAMESPACE = Namespace.getNamespace(XI, HTTP_WWW_W3_ORG_2001_XINCLUDE);
  private final boolean myIgnoreMissing;
  private final PathResolver myPathResolver;

  private JDOMXIncluder(boolean ignoreMissing, @NotNull PathResolver pathResolver) {
    myIgnoreMissing = ignoreMissing;
    myPathResolver = pathResolver;
  }

  @NotNull
  public static Document resolve(Document original, String base) throws XIncludeException, MalformedURLException {
    return resolve(original, base, false);
  }

  @NotNull
  public static Document resolve(Document original, String base, boolean ignoreMissing) throws XIncludeException, MalformedURLException {
    return resolve(original, base, ignoreMissing, DEFAULT_PATH_RESOLVER);
  }

  @NotNull
  public static Document resolve(Document original, String base, boolean ignoreMissing, PathResolver pathResolver)
    throws XIncludeException, MalformedURLException {
    return new JDOMXIncluder(ignoreMissing, pathResolver).doResolve(original, new URL(base));
  }

  @NotNull
  public static List<Content> resolve(@NotNull Element original, String base, boolean ignoreMissing, PathResolver pathResolver)
    throws XIncludeException, MalformedURLException {
    return new JDOMXIncluder(ignoreMissing, pathResolver).doResolve(original, new URL(base));
  }

  /**
   * Original element will be mutated in place.
   */
  public static void resolveNonXIncludeElement(@NotNull Element original, @Nullable URL base, boolean ignoreMissing, @NotNull PathResolver pathResolver)
    throws XIncludeException, MalformedURLException {
    LOG.assertTrue(!isIncludeElement(original));

    Stack<URL> bases = new Stack<URL>();
    if (base != null) {
      bases.push(base);
    }
    new InplaceJdomXIncluder(ignoreMissing, pathResolver).resolveNonXIncludeElement(original, bases);
  }

  @NotNull
  public static List<Content> resolve(@NotNull Element original, URL base) throws XIncludeException, MalformedURLException {
    return new JDOMXIncluder(false, DEFAULT_PATH_RESOLVER).doResolve(original, base);
  }

  private static final class InplaceJdomXIncluder extends JDOMXIncluder {
    private InplaceJdomXIncluder(boolean ignoreMissing, @NotNull PathResolver pathResolver) {
      super(ignoreMissing, pathResolver);
    }

    @Override
    @NotNull
    protected Element resolveNonXIncludeElement(@NotNull Element original, @NotNull Stack<URL> bases)
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
            // ignore result since in our case (in-place processing) returned element it is the same element
            resolveNonXIncludeElement(element, bases);
          }
        }
      }
      return original;
    }
  }

  private Document doResolve(Document original, URL base) throws MalformedURLException {
    if (original == null) {
      throw new NullPointerException("Document must not be null");
    }

    Document result = original.clone();

    Element root = result.getRootElement();
    List<Content> resolved = doResolve(root, base);

    // check that the list returned contains
    // exactly one root element
    Element newRoot = null;
    Iterator<Content> iterator = resolved.iterator();
    while (iterator.hasNext()) {
      Content o = iterator.next();
      if (o instanceof Element) {
        if (newRoot != null) {
          throw new XIncludeException("Tried to include multiple roots");
        }
        newRoot = (Element)o;
      }
      else if (o instanceof Comment || o instanceof ProcessingInstruction) {
        // do nothing
      }
      else if (o instanceof Text) {
        throw new XIncludeException("Tried to include text node outside of root element");
      }
      else if (o instanceof EntityRef) {
        throw new XIncludeException("Tried to include a general entity reference outside of root element");
      }
      else {
        throw new XIncludeException("Unexpected type " + o.getClass());
      }

    }

    if (newRoot == null) {
      throw new XIncludeException("No root element");
    }

    // Could probably combine two loops
    List<Content> newContent = result.getContent();
    // resolved contains list of new content
    // use it to replace old root element
    iterator = resolved.iterator();

    // put in nodes before root element
    int rootPosition = newContent.indexOf(result.getRootElement());
    while (iterator.hasNext()) {
      Content o = iterator.next();
      if (o instanceof Comment || o instanceof ProcessingInstruction) {
        newContent.add(rootPosition, o);
        rootPosition++;
      }
      else if (o instanceof Element) { // the root
        break;
      }
      else {
        // throw exception????
      }
    }

    // put in root element
    result.setRootElement(newRoot);

    int addPosition = rootPosition + 1;
    // put in nodes after root element
    while (iterator.hasNext()) {
      Content o = iterator.next();
      if (o instanceof Comment || o instanceof ProcessingInstruction) {
        newContent.add(addPosition, o);
        addPosition++;
      }
      else {
        // throw exception????
      }
    }

    return result;
  }

  @NotNull
  private List<Content> doResolve(@NotNull Element original, URL base) throws XIncludeException, MalformedURLException {
    Stack<URL> bases = new Stack<URL>();
    if (base != null) {
      bases.push(base);
    }

    return resolve(original, bases);
  }

  protected static boolean isIncludeElement(Element element) {
    return element.getName().equals(INCLUDE) && element.getNamespace().equals(XINCLUDE_NAMESPACE);
  }

  @NotNull
  private List<Content> resolve(@NotNull Element original, @NotNull Stack<URL> bases) throws XIncludeException, MalformedURLException {
    if (isIncludeElement(original)) {
      return resolveXIncludeElement(original, bases);
    }
    else {
      Element resolvedElement = resolveNonXIncludeElement(original, bases);
      return Collections.<Content>singletonList(resolvedElement);
    }
  }

  @NotNull
  protected List<Content> resolveXIncludeElement(@NotNull Element element, @NotNull Stack<URL> bases)
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

    URL remote = myPathResolver.resolvePath(href, base);

    final String parseAttribute = element.getAttributeValue(PARSE);
    if (parseAttribute != null) {
      LOG.assertTrue(parseAttribute.equals(XML), parseAttribute + " is not a legal value for the parse attribute");
    }

    assert !bases.contains(remote) : "Circular XInclude Reference to " + remote.toExternalForm();

    final Element fallbackElement = element.getChild("fallback", element.getNamespace());
    List<Content> remoteParsed = parseRemote(bases, remote, fallbackElement);
    if (!remoteParsed.isEmpty()) {
      remoteParsed = extractNeededChildren(element, remoteParsed);
    }

    int i = 0;
    for (; i < remoteParsed.size(); i++) {
      Content o = remoteParsed.get(i);
      if (o instanceof Element) {
        Element e = (Element)o;
        List<Content> nodes = resolve(e, bases);
        remoteParsed.addAll(i, nodes);
        i += nodes.size();
        remoteParsed.remove(i);
        i--;
        e.detach();
      }
    }

    for (Content content : remoteParsed) {
      content.detach();
    }
    return remoteParsed;
  }

  //xpointer($1)
  @NonNls public static final Pattern XPOINTER_PATTERN = Pattern.compile("xpointer\\((.*)\\)");

  // /$1(/$2)?/*
  public static final Pattern CHILDREN_PATTERN = Pattern.compile("/([^/]*)(/[^/]*)?/\\*");

  @NotNull
  private static List<Content> extractNeededChildren(@NotNull Element element, @NotNull List<Content> remoteElements) {
    final String xpointer = element.getAttributeValue(XPOINTER);
    if (xpointer == null) {
      return remoteElements;
    }

    Matcher matcher = XPOINTER_PATTERN.matcher(xpointer);
    boolean b = matcher.matches();
    assert b : "Unsupported XPointer: " + xpointer;

    String pointer = matcher.group(1);

    matcher = CHILDREN_PATTERN.matcher(pointer);

    b = matcher.matches();
    assert b : "Unsupported pointer: " + pointer;

    final String rootTagName = matcher.group(1);

    assert remoteElements.size() == 1;
    assert remoteElements.get(0) instanceof Element;

    Element e = (Element)remoteElements.get(0);
    if (!e.getName().equals(rootTagName)) {
      return Collections.emptyList();
    }

    String subTagName = matcher.group(2);
    if (subTagName != null) {
      // cut off the slash
      e = e.getChild(subTagName.substring(1));
    }
    assert e != null;
    return new ArrayList<Content>(e.getContent());
  }

  @NotNull
  private List<Content> parseRemote(@NotNull Stack<URL> bases, @NotNull URL remote, @Nullable Element fallbackElement) {
    try {
      bases.push(remote);
      Element root = JDOMUtil.loadResource(remote);
      List<Content> list = resolve(root, bases);
      bases.pop();
      return list;
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
  }

  @NotNull
  protected Element resolveNonXIncludeElement(@NotNull Element original, @NotNull Stack<URL> bases)
    throws XIncludeException, MalformedURLException {
    Element result = new Element(original.getName(), original.getNamespace());
    if (original.hasAttributes()) {
      for (Attribute a : original.getAttributes()) {
        result.setAttribute(a.clone());
      }
    }

    for (Content o : original.getContent()) {
      if (o instanceof Element) {
        Element element = (Element)o;
        if (isIncludeElement(element)) {
          result.addContent(resolveXIncludeElement(element, bases));
        }
        else {
          result.addContent(resolveNonXIncludeElement(element, bases));
        }
      }
      else {
        result.addContent(o.clone());
      }
    }

    return result;
  }

  public interface PathResolver {
    @NotNull
    URL resolvePath(@NotNull String relativePath, @Nullable URL base) throws MalformedURLException;
  }
}