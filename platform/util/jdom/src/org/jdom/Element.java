/*--

 Copyright (C) 2000-2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom;

import org.jdom.filter.AbstractFilter;
import org.jdom.filter.Filter;
import org.jdom.filter2.ElementFilter;
import org.jdom.filter2.Filters;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Stream;

import static org.jdom.JDOMConstants.NS_PREFIX_DEFAULT;
import static org.jdom.JDOMConstants.NS_PREFIX_XML;

/**
 * An XML element. Methods allow the user to get and manipulate its child
 * elements and content, directly access the element's textual content,
 * manipulate its attributes, and manage namespaces.
 * <p>
 * See {@link NamespaceAware} and {@link #getNamespacesInScope()} for more
 * details on what the Namespace scope is and how it is managed in JDOM and
 * specifically by this Element class.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 * @author Lucas Gonze
 * @author Kevin Regan
 * @author Dan Schaffer
 * @author Yusuf Goolamabbas
 * @author Kent C. Johnson
 * @author Jools Enticknap
 * @author Alex Rosen
 * @author Bradley S. Huffman
 * @author Victor Toni
 * @author Rolf Lear
 * @see NamespaceAware
 * @see Content
 */
public class Element extends Content implements Parent, Serializable {
  /**
   * The local name of the element
   */
  protected String name;

  /**
   * The namespace of the element
   */
  protected Namespace namespace;

  /**
   * Additional namespace declarations to store on this element; useful
   * during output
   */
  transient List<Namespace> additionalNamespaces;

  /**
   * The attributes of the element.  Subclasses have to
   * track attributes using their own mechanism.
   */
  transient AttributeList attributes = null; // = new AttributeList(this);

  /**
   * The content of the element.  Subclasses have to
   * track content using their own mechanism.
   */
  protected transient ContentList content = new ContentList(this);

  /**
   * This protected constructor is provided in order to support an Element
   * subclass that wants full control over variable initialization. It
   * intentionally leaves all instance variables null, allowing a lightweight
   * subclass implementation. The subclass is responsible for ensuring all the
   * get and set methods on Element behave as documented.
   * <p>
   * When implementing an Element subclass which doesn't require full control
   * over variable initialization, be aware that simply calling super() (or
   * letting the compiler add the implicit super() call) will not initialize
   * the instance variables which will cause many of the methods to throw a
   * NullPointerException. Therefore, the constructor for these subclasses
   * should call one of the public constructors so variable initialization is
   * handled automatically.
   */
  protected Element() {
    super(CType.Element);
  }

  /**
   * Creates a new element with the supplied (local) name and namespace. If
   * the provided namespace is null, the element will have no namespace.
   *
   * @param name      local name of the element
   * @param namespace namespace for the element
   * @throws IllegalNameException if the given name is illegal as an element
   *                              name
   */
  public Element(final String name, final Namespace namespace) {
    super(CType.Element);
    setName(name);
    setNamespace(namespace);
  }

  /**
   * Create a new element with the supplied (local) name and no namespace.
   *
   * @param name local name of the element
   * @throws IllegalNameException if the given name is illegal as an element name.
   */
  public Element(final String name) {
    this(name, (Namespace)null);
  }

  /**
   * Creates a new element with the supplied (local) name and a namespace
   * given by a URI. The element will be put into the unprefixed (default)
   * namespace.
   *
   * @param name name of the element
   * @param uri  namespace URI for the element
   * @throws IllegalNameException if the given name is illegal as an element
   *                              name or the given URI is illegal as a
   *                              namespace URI
   */
  public Element(final String name, final String uri) {
    this(name, Namespace.getNamespace(NS_PREFIX_DEFAULT, uri));
  }

  /**
   * Creates a new element with the supplied (local) name and a namespace
   * given by the supplied prefix and URI combination.
   *
   * @param name   local name of the element
   * @param prefix namespace prefix
   * @param uri    namespace URI for the element
   * @throws IllegalNameException if the given name is illegal as an element
   *                              name, the given prefix is illegal as a
   *                              namespace prefix, or the given URI is
   *                              illegal as a namespace URI
   */
  public Element(final String name, final String prefix, final String uri) {
    this(name, Namespace.getNamespace(prefix, uri));
  }

  /**
   * Returns the (local) name of the element (without any namespace prefix).
   *
   * @return local element name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the (local) name of the element.
   *
   * @param name the new (local) name of the element
   * @return the target element
   * @throws IllegalNameException if the given name is illegal as an Element
   *                              name
   */
  public Element setName(final String name) {
    final String reason = Verifier.checkElementName(name);
    if (reason != null) {
      throw new IllegalNameException(name, "element", reason);
    }
    this.name = name;
    return this;
  }

  /**
   * Returns the element's {@link Namespace}.
   *
   * @return the element's namespace
   */
  public Namespace getNamespace() {
    return namespace;
  }

  /**
   * Sets the element's {@link Namespace}. If the provided namespace is null,
   * the element will have no namespace.
   *
   * @param namespace the new namespace. A null implies Namespace.NO_NAMESPACE.
   * @return the target element
   * @throws IllegalAddException if there is a Namespace conflict
   */
  public Element setNamespace(Namespace namespace) {
    if (namespace == null) {
      namespace = Namespace.NO_NAMESPACE;
    }

    if (additionalNamespaces != null) {
      String reason = Verifier.checkNamespaceCollision(namespace, additionalNamespaces);
      if (reason != null) {
        throw new IllegalAddException(this, namespace, reason);
      }
    }
    if (hasAttributes()) {
      for (Attribute a : getAttributes()) {
        final String reason =
          Verifier.checkNamespaceCollision(namespace, a);
        if (reason != null) {
          throw new IllegalAddException(this, namespace, reason);
        }
      }
    }

    this.namespace = namespace;
    return this;
  }

  /**
   * Returns the namespace prefix of the element or an empty string if none
   * exists.
   *
   * @return the namespace prefix
   */
  public String getNamespacePrefix() {
    return namespace.getPrefix();
  }

  /**
   * Returns the namespace URI mapped to this element's prefix (or the
   * in-scope default namespace URI if no prefix). If no mapping is found, an
   * empty string is returned.
   *
   * @return the namespace URI for this element
   */
  public String getNamespaceURI() {
    return namespace.getURI();
  }

  /**
   * Returns the {@link Namespace} corresponding to the given prefix in scope
   * for this element. This involves searching up the tree, so the results
   * depend on the current location of the element. Returns null if there is
   * no namespace in scope with the given prefix at this point in the
   * document.
   *
   * @param prefix namespace prefix to look up
   * @return the Namespace for this prefix at this
   * location, or null if none
   */
  public Namespace getNamespace(final String prefix) {
    if (prefix == null) {
      return null;
    }

    if (NS_PREFIX_XML.equals(prefix)) {
      // Namespace "xml" is always bound.
      return Namespace.XML_NAMESPACE;
    }

    // Check if the prefix is the prefix for this element
    if (prefix.equals(getNamespacePrefix())) {
      return getNamespace();
    }

    // Scan the additional namespaces
    if (additionalNamespaces != null) {
      for (final Namespace ns : additionalNamespaces) {
        if (prefix.equals(ns.getPrefix())) {
          return ns;
        }
      }
    }

    if (attributes != null) {
      for (final Attribute a : attributes) {
        if (prefix.equals(a.getNamespacePrefix())) {
          return a.getNamespace();
        }
      }
    }

    // If we still don't have a match, ask the parent
    if (parent instanceof Element) {
      return ((Element)parent).getNamespace(prefix);
    }

    return null;
  }

  /**
   * Returns the full name of the element, in the form
   * [namespacePrefix]:[localName]. If the element does not have a namespace
   * prefix, then the local name is returned.
   *
   * @return qualified name of the element (including
   * namespace prefix)
   */
  public String getQualifiedName() {
    // Note: Any changes here should be reflected in
    // XMLOutputter.printQualifiedName()
    if ("".equals(namespace.getPrefix())) {
      return getName();
    }

    return namespace.getPrefix() + ':' + name;
  }

  /**
   * Adds a namespace declarations to this element. This should <i>not</i> be
   * used to add the declaration for this element itself; that should be
   * assigned in the construction of the element. Instead, this is for adding
   * namespace declarations on the element not relating directly to itself.
   * It's used during output to for stylistic reasons move namespace
   * declarations higher in the tree than they would have to be.
   *
   * @param additionalNamespace namespace to add
   * @throws IllegalAddException if the namespace prefix collides with another
   *                             namespace prefix on the element
   */
  public void addNamespaceDeclaration(Namespace additionalNamespace) {
    if (additionalNamespaces == null) {
      additionalNamespaces = new ArrayList<>();
    }
    else {
      for (Namespace ns : additionalNamespaces) {
        if (ns == additionalNamespace) {
          return;
        }
      }
    }

    // Verify the new namespace prefix doesn't collide with another
    // declared namespace, an attribute prefix, or this element's prefix
    final String reason = Verifier.checkNamespaceCollision(additionalNamespace, this);
    if (reason != null) {
      throw new IllegalAddException(this, additionalNamespace, reason);
    }

    additionalNamespaces.add(additionalNamespace);
  }

  /**
   * Returns a list of the additional namespace declarations on this element.
   * This includes only additional namespace, not the namespace of the element
   * itself, which can be obtained through {@link #getNamespace()}. If there
   * are no additional declarations, this returns an empty list. Note, the
   * returned list is unmodifiable.
   *
   * @return a List of the additional namespace
   * declarations
   */
  public List<Namespace> getAdditionalNamespaces() {
    // Not having the returned list be live allows us to avoid creating a
    // new list object when XMLOutputter calls this method on an element
    // with an empty list.
    if (additionalNamespaces == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(additionalNamespaces);
  }

  /**
   * Returns the XPath 1.0 string value of this element, which is the
   * complete, ordered content of all text node descendants of this element
   * (i&#46;e&#46; the text that's left after all references are resolved
   * and all other markup is stripped out.)
   *
   * @return a concatenation of all text node descendants
   */
  @Override
  public String getValue() {
    final StringBuilder buffer = new StringBuilder();

    for (Content child : getContent()) {
      if (child instanceof Element || child instanceof Text) {
        buffer.append(child.getValue());
      }
    }
    return buffer.toString();
  }

  /**
   * Returns whether this element is a root element. This can be used in
   * tandem with {@link #getParent} to determine if an element has any
   * "attachments" to a parent element or document.
   * <p>
   * An element is a root element when it has a parent and that parent is a
   * Document. In particular, this means that detached Elements are <b>not</b>
   * root elements.
   *
   * @return whether this is a root element
   */
  public boolean isRootElement() {
    return parent instanceof Document;
  }

  @Override
  public int getContentSize() {
    return content.size();
  }

  @Override
  public int indexOf(final Content child) {
    return content.indexOf(child);
  }

  /**
   * Returns the textual content directly held under this element as a string.
   * This includes all text within this single element, including whitespace
   * and CDATA sections if they exist. It's essentially the concatenation of
   * all {@link Text} and {@link CDATA} nodes returned by {@link #getContent}.
   * The call does not recurse into child elements. If no textual value exists
   * for the element, an empty string is returned.
   *
   * @return text content for this element, or empty
   * string if none
   */
  public String getText() {
    if (content.size() == 0) {
      return "";
    }

    // If we hold only a Text or CDATA, return it directly
    if (content.size() == 1) {
      final Object obj = content.get(0);
      if (obj instanceof Text) {
        return ((Text)obj).getText();
      }
      return "";
    }

    // Else build String up
    final StringBuilder textContent = new StringBuilder();
    boolean hasText = false;

    for (final Object obj : content) {
      if (obj instanceof Text) {
        textContent.append(((Text)obj).getText());
        hasText = true;
      }
    }

    if (!hasText) {
      return "";
    }
    return textContent.toString();
  }

  /**
   * Returns the textual content of this element with all surrounding
   * whitespace removed. If no textual value exists for the element, or if
   * only whitespace exists, the empty string is returned.
   *
   * @return trimmed text content for this element, or
   * empty string if none
   */
  public String getTextTrim() {
    return getText().trim();
  }

  /**
   * Returns the textual content of this element with all surrounding
   * whitespace removed and internal whitespace normalized to a single space.
   * If no textual value exists for the element, or if only whitespace exists,
   * the empty string is returned.
   *
   * @return normalized text content for this element, or
   * empty string if none
   */
  public String getTextNormalize() {
    return Text.normalizeString(getText());
  }

  /**
   * Returns the textual content of the named child element, or null if
   * there's no such child. This method is a convenience because calling
   * <code>getChild().getText()</code> can throw a NullPointerException.
   *
   * @param cname the name of the child
   * @return text content for the named child, or null if
   * no such child
   */
  public String getChildText(final String cname) {
    final Element child = getChild(cname);
    if (child == null) {
      return null;
    }
    return child.getText();
  }

  /**
   * Returns the trimmed textual content of the named child element, or null
   * if there's no such child. See <code>{@link #getTextTrim()}</code> for
   * details of text trimming.
   *
   * @param cname the name of the child
   * @return trimmed text content for the named child, or
   * null if no such child
   */
  @SuppressWarnings("unused")
  public String getChildTextTrim(final String cname) {
    final Element child = getChild(cname);
    if (child == null) {
      return null;
    }
    return child.getTextTrim();
  }

  public String getChildTextTrim(final String cname, final Namespace ns) {
    final Element child = getChild(cname, ns);
    if (child == null) {
      return null;
    }
    return child.getTextTrim();
  }

  /**
   * Returns the textual content of the named child element, or null if
   * there's no such child.
   *
   * @param cname the name of the child
   * @param ns    the namespace of the child. A null implies Namespace.NO_NAMESPACE.
   * @return text content for the named child, or null if no such child
   */
  public String getChildText(final String cname, final Namespace ns) {
    final Element child = getChild(cname, ns);
    if (child == null) {
      return null;
    }
    return child.getText();
  }

  /**
   * Sets the content of the element to be the text given. All existing text
   * content and non-text context is removed. If this element should have both
   * textual content and nested elements, use <code>{@link #setContent}</code>
   * instead. Setting a null text value is equivalent to setting an empty
   * string value.
   *
   * @param text new text content for the element
   * @return the target element
   * @throws IllegalDataException if the assigned text contains an illegal
   *                              character such as a vertical tab (as
   *                              determined by {@link
   *                              Verifier#checkCharacterData})
   */
  public Element setText(String text) {
    content.clear();
    if (text != null) {
      addContent(new Text(text));
    }
    return this;
  }

  /**
   * This returns the full content of the element as a List which
   * may contain objects of type <code>Text</code>, <code>Element</code>,
   * <code>Comment</code>, <code>ProcessingInstruction</code>,
   * <code>CDATA</code>, and <code>EntityRef</code>.
   * The List returned is "live" in document order and modifications
   * to it affect the element's actual contents.  Whitespace content is
   * returned in its entirety.
   *
   * <p>
   * Sequential traversal through the List is best done with an Iterator
   * since the underlying implement of List.size() may require walking the
   * entire list.
   * </p>
   *
   * @return a <code>List</code> containing the mixed content of the
   * element: may contain <code>Text</code>,
   * <code>{@link Element}</code>, <code>{@link Comment}</code>,
   * <code>{@link ProcessingInstruction}</code>,
   * <code>{@link CDATA}</code>, and
   * <code>{@link EntityRef}</code> objects.
   */
  @Override
  public List<Content> getContent() {
    return content;
  }

  /**
   * Return a filter view of this <code>Element</code>'s content.
   *
   * <p>
   * Sequential traversal through the List is best done with a Iterator
   * since the underlying implement of List.size() may require walking the
   * entire list.
   * </p>
   *
   * @param filter <code>Filter</code> to apply
   * @return <code>List</code> - filtered Element content
   */
  public <E extends Content> List<E> getContent(Filter<E> filter) {
    return content.getView(AbstractFilter.toFilter2(filter));
  }

  public Stream<Content> content() {
    return content.stream();
  }

  /**
   * Removes all child content from this parent.
   *
   * @return list of the old children detached from this parent
   */
  @Override
  public List<Content> removeContent() {
    List<Content> old = new ArrayList<>(content);
    content.clear();
    return old;
  }

  /**
   * Remove all child content from this parent matching the supplied filter.
   *
   * @param filter filter to select which content to remove
   * @return list of the old children detached from this parent
   */
  public <F extends Content> List<F> removeContent(Filter<F> filter) {
    List<F> old = new ArrayList<>();
    Iterator<F> iter = content.getView(AbstractFilter.toFilter2(filter)).iterator();
    while (iter.hasNext()) {
      F child = iter.next();
      old.add(child);
      iter.remove();
    }
    return old;
  }

  /**
   * This sets the content of the element.  The supplied List should
   * contain only objects of type <code>Element</code>, <code>Text</code>,
   * <code>CDATA</code>, <code>Comment</code>,
   * <code>ProcessingInstruction</code>, and <code>EntityRef</code>.
   *
   * <p>
   * When all objects in the supplied List are legal and before the new
   * content is added, all objects in the old content will have their
   * parentage set to null (no parent) and the old content list will be
   * cleared. This has the effect that any active list (previously obtained
   * with a call to {@link #getContent} or {@link #getChildren}) will also
   * change to reflect the new content.  In addition, all objects in the
   * supplied List will have their parentage set to this element, but the
   * List itself will not be "live" and further removals and additions will
   * have no effect on this elements content. If the user wants to continue
   * working with a "live" list, then a call to setContent should be
   * followed by a call to {@link #getContent} or {@link #getChildren} to
   * obtain a "live" version of the content.
   * </p>
   *
   * <p>
   * Passing a null or empty List clears the existing content.
   * </p>
   *
   * <p>
   * In event of an exception the original content will be unchanged and
   * the objects in the supplied content will be unaltered.
   * </p>
   *
   * @param newContent <code>Collection</code> of content to set
   * @return this element modified
   * @throws IllegalAddException if the List contains objects of
   *                             illegal types or with existing parentage.
   */
  public Element setContent(Collection<? extends Content> newContent) {
    content.clearAndSet(newContent);
    return this;
  }

  /**
   * Replace the current child the given index with the supplied child.
   * <p>
   * In event of an exception the original content will be unchanged and
   * the supplied child will be unaltered.
   * </p>
   *
   * @param index - index of child to replace.
   * @param child - child to add.
   * @return element on which this method was invoked
   * @throws IllegalAddException       if the supplied child is already attached
   *                                   or not legal content for this parent.
   * @throws IndexOutOfBoundsException if index is negative or greater
   *                                   than the current number of children.
   */
  public Element setContent(final int index, final Content child) {
    content.set(index, child);
    return this;
  }

  /**
   * Replace the child at the given index with the supplied
   * collection.
   * <p>
   * In event of an exception the original content will be unchanged and
   * the content in the supplied collection will be unaltered.
   * </p>
   *
   * @param index      - index of child to replace.
   * @param newContent - <code>Collection</code> of content to replace child.
   * @return object on which this method was invoked
   * @throws IllegalAddException       if the collection contains objects of
   *                                   illegal types.
   * @throws IndexOutOfBoundsException if index is negative or greater
   *                                   than the current number of children.
   */
  public Parent setContent(final int index, final Collection<? extends Content> newContent) {
    content.remove(index);
    content.addAll(index, newContent);
    return this;
  }

  /**
   * This adds text content to this element.  It does not replace the
   * existing content as does <code>setText()</code>.
   *
   * @param str <code>String</code> to add
   * @return this element modified
   * @throws IllegalDataException if <code>str</code> contains an
   *                              illegal character such as a vertical tab (as determined
   *                              by {@link Verifier#checkCharacterData})
   */
  public Element addContent(final String str) {
    return addContent(new Text(str));
  }

  /**
   * Appends the child to the end of the element's content list.
   *
   * @param child child to append to end of content list
   * @return the element on which the method was called
   * @throws IllegalAddException if the given child already has a parent.
   */
  @Override
  public Element addContent(final Content child) {
    content.add(child);
    return this;
  }

  /**
   * The same as {@link #addContent(Content)}, added to keep binary compatibility.
   */
  public Element addContent(Element child) {
    content.add(child);
    return this;
  }

  /**
   * Appends all children in the given collection to the end of
   * the content list.  In event of an exception during add the
   * original content will be unchanged and the objects in the supplied
   * collection will be unaltered.
   *
   * @param newContent <code>Collection</code> of content to append
   * @return the element on which the method was called
   * @throws IllegalAddException if any item in the collection
   *                             already has a parent or is of an inappropriate type.
   */
  @Override
  public Element addContent(final Collection<? extends Content> newContent) {
    content.addAll(newContent);
    return this;
  }

  /**
   * Inserts the child into the content list at the given index.
   *
   * @param index location for adding the collection
   * @param child child to insert
   * @return the parent on which the method was called
   * @throws IndexOutOfBoundsException if index is negative or beyond
   *                                   the current number of children
   * @throws IllegalAddException       if the given child already has a parent.
   */
  @Override
  public Element addContent(final int index, final Content child) {
    content.add(index, child);
    return this;
  }

  /**
   * Inserts the content in a collection into the content list
   * at the given index.  In event of an exception the original content
   * will be unchanged and the objects in the supplied collection will be
   * unaltered.
   *
   * @param index      location for adding the collection
   * @param newContent <code>Collection</code> of content to insert
   * @return the parent on which the method was called
   * @throws IndexOutOfBoundsException if index is negative or beyond
   *                                   the current number of children
   * @throws IllegalAddException       if any item in the collection
   *                                   already has a parent or is of an inappropriate type.
   */
  @Override
  public Element addContent(final int index, final Collection<? extends Content> newContent) {
    content.addAll(index, newContent);
    return this;
  }

  @Override
  public List<Content> cloneContent() {
    final int size = getContentSize();
    final List<Content> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      final Content child = getContent(i);
      list.add(child.clone());
    }
    return list;
  }

  @Override
  public Content getContent(final int index) {
    return content.get(index);
  }

  @Override
  public boolean removeContent(final Content child) {
    return content.remove(child);
  }

  @Override
  public Content removeContent(final int index) {
    return content.remove(index);
  }

  /**
   * Set this element's content to be the supplied child.
   * <p>
   * If the supplied child is legal content for this parent, and before
   * it is added, all content in the current content list will
   * be cleared and all current children will have their parentage set to
   * null.
   * <p>
   * This has the effect that any active list (previously obtained with
   * a call to one of the {@link #getContent} methods will also change
   * to reflect the new content.  In addition, all content in the supplied
   * collection will have their parentage set to this parent.  If the user
   * wants to continue working with a <b>"live"</b> list of this parent's
   * child, then a call to setContent should be followed by a call to one
   * of the {@link #getContent} methods to obtain a <b>"live"</b>
   * version of the children.
   * <p>
   * Passing a null child clears the existing content.
   * <p>
   * In event of an exception the original content will be unchanged and
   * the supplied child will be unaltered.
   *
   * @param child new content to replace existing content
   * @return the parent on which the method was called
   * @throws IllegalAddException if the supplied child is already attached
   *                             or not legal content for an Element
   */
  public Element setContent(final Content child) {
    content.clear();
    content.add(child);
    return this;
  }

  /**
   * Determines if this element is the ancestor of another element.
   *
   * @param element <code>Element</code> to check against
   * @return <code>true</code> if this element is the ancestor of the
   * supplied element
   */
  public boolean isAncestor(final Element element) {
    Parent p = element.getParent();
    while (p instanceof Element) {
      if (p == this) {
        return true;
      }
      p = p.getParent();
    }
    return false;
  }

  /**
   * Indicate whether this Element has any attributes.
   * Where possible you should call this method before calling getAttributes()
   * because calling getAttributes() will create the necessary Attribute List
   * memory structures, even if there are no Attributes attached to the
   * Element. Calling hasAttributes() first can save memory.
   *
   * @return true if this Element has attributes.
   */
  public boolean hasAttributes() {
    return attributes != null && !attributes.isEmpty();
  }

  /**
   * Indicate whether this Element has any additional Namespace declarations.
   * Where possible you should call this method before calling
   * {@link #getAdditionalNamespaces()} because calling getAttributes() will
   * create an unnecessary List even if there are no Additional Namespaces
   * attached to the Element. Calling this method first can save memory and
   * time.
   *
   * @return true if this Element has additional Namespaces.
   */
  public boolean hasAdditionalNamespaces() {
    return additionalNamespaces != null && !additionalNamespaces.isEmpty();
  }

  /**
   * Lazy initializer for the Attribute list.
   *
   * @return this Element's Attribute List (creating it if necessary).
   */
  private AttributeList getAttributeList() {
    if (attributes == null) {
      attributes = new AttributeList(this);
    }
    return attributes;
  }

  /**
   * <p>
   * This returns the complete set of attributes for this element, as a
   * <code>List</code> of <code>Attribute</code> objects in no particular
   * order, or an empty list if there are none.
   * The returned list is "live" and changes to it affect the
   * element's actual attributes.
   * </p>
   *
   * @return attributes for the element
   */
  public List<Attribute> getAttributes() {
    return getAttributeList();
  }

  /**
   * <p>
   * This returns the attribute for this element with the given name
   * and within no namespace, or null if no such attribute exists.
   * </p>
   *
   * @param name name of the attribute to return
   * @return attribute for the element
   */
  public Attribute getAttribute(final String name) {
    return getAttribute(name, Namespace.NO_NAMESPACE);
  }

  /**
   * <p>
   * This returns the attribute for this element with the given name
   * and within the given Namespace, or null if no such attribute exists.
   * </p>
   *
   * @param name name of the attribute to return
   * @param ns   <code>Namespace</code> to search within. A null implies Namespace.NO_NAMESPACE.
   * @return attribute for the element
   */
  public Attribute getAttribute(final String name, final Namespace ns) {
    if (attributes == null) {
      return null;
    }
    return getAttributeList().get(name, ns);
  }

  /**
   * <p>
   * This returns the attribute value for the attribute with the given name
   * and within no namespace, null if there is no such attribute, and the
   * empty string if the attribute value is empty.
   * </p>
   *
   * @param name name of the attribute whose value to be returned
   * @return the named attribute's value, or null if no such attribute
   */
  public String getAttributeValue(final String name) {
    return attributes == null ? null : getAttributeValue(name, Namespace.NO_NAMESPACE, null);
  }

  public boolean getAttributeBooleanValue(@NotNull String name) {
    return attributes != null && Boolean.parseBoolean(getAttributeValue(name, Namespace.NO_NAMESPACE, null));
  }

  /**
   * <p>
   * This returns the attribute value for the attribute with the given name
   * and within no namespace, or the passed-in default if there is no
   * such attribute.
   * </p>
   *
   * @param name name of the attribute whose value to be returned
   * @param def  a default value to return if the attribute does not exist
   * @return the named attribute's value, or the default if no such attribute
   */
  public String getAttributeValue(String name, String def) {
    return attributes == null ? def : getAttributeValue(name, Namespace.NO_NAMESPACE, def);
  }

  /**
   * <p>
   * This returns the attribute value for the attribute with the given name
   * and within the given Namespace, null if there is no such attribute, and
   * the empty string if the attribute value is empty.
   * </p>
   *
   * @param name name of the attribute whose value is to be returned
   * @param ns   <code>Namespace</code> to search within. A null implies Namespace.NO_NAMESPACE.
   * @return the named attribute's value, or null if no such attribute
   */
  public String getAttributeValue(String name, Namespace ns) {
    return getAttributeValue(name, ns, null);
  }

  /**
   * <p>
   * This returns the attribute value for the attribute with the given name
   * and within the given Namespace, or the passed-in default if there is no
   * such attribute.
   * </p>
   *
   * @param name        name of the attribute whose value is to be returned
   * @param ns          <code>Namespace</code> to search within. A null implies Namespace.NO_NAMESPACE.
   * @param defaultValue a default value to return if the attribute does not exist
   * @return the named attribute's value, or the default if no such attribute
   */
  public String getAttributeValue(String name, Namespace ns, String defaultValue) {
    Attribute attribute = attributes == null ? null : getAttributeList().get(name, ns);
    return attribute == null ? defaultValue : attribute.getValue();
  }

  /**
   * <p>
   * This sets the attributes of the element.  The supplied Collection should
   * contain only objects of type <code>Attribute</code>.
   * </p>
   *
   * <p>
   * When all objects in the supplied List are legal and before the new
   * attributes are added, all old attributes will have their
   * parentage set to null (no parent) and the old attribute list will be
   * cleared. This has the effect that any active attribute list (previously
   * obtained with a call to {@link #getAttributes}) will also change to
   * reflect the new attributes.  In addition, all attributes in the supplied
   * List will have their parentage set to this element, but the List itself
   * will not be "live" and further removals and additions will have no
   * effect on this elements attributes. If the user wants to continue
   * working with a "live" attribute list, then a call to setAttributes
   * should be followed by a call to {@link #getAttributes} to obtain a
   * "live" version of the attributes.
   * </p>
   *
   * <p>
   * Passing a null or empty List clears the existing attributes.
   * </p>
   *
   * <p>
   * In cases where the List contains duplicate attributes, only the last
   * one will be retained.  This has the same effect as calling
   * {@link #setAttribute(Attribute)} sequentially.
   * </p>
   *
   * <p>
   * In event of an exception the original attributes will be unchanged and
   * the attributes in the supplied attributes will be unaltered.
   * </p>
   *
   * @param newAttributes <code>Collection</code> of attributes to set
   * @return this element modified
   * @throws IllegalAddException if the List contains objects
   *                             that are not instances of <code>Attribute</code>,
   *                             or if any of the <code>Attribute</code> objects have
   *                             conflicting namespace prefixes.
   */
  public Element setAttributes(final Collection<? extends Attribute> newAttributes) {
    getAttributeList().clearAndSet(newAttributes);
    return this;
  }

  /**
   * The same as {@link #setAttributes(Collection)}, added to keep binary compatibility.
   */
  public Element setAttributes(List<? extends Attribute> newAttributes) {
    getAttributeList().clearAndSet(newAttributes);
    return this;
  }

  /**
   * <p>
   * This sets an attribute value for this element.  Any existing attribute
   * with the same name and namespace URI is removed.
   * </p>
   *
   * @param name  name of the attribute to set
   * @param value value of the attribute to set
   * @return this element modified
   * @throws IllegalNameException if the given name is illegal as an
   *                              attribute name.
   * @throws IllegalDataException if the given attribute value is
   *                              illegal character data (as determined by
   *                              {@link Verifier#checkCharacterData}).
   */
  public Element setAttribute(final String name, final String value) {
    final Attribute attribute = getAttribute(name);
    if (attribute == null) {
      final Attribute newAttribute = new Attribute(name, value);
      setAttribute(newAttribute);
    }
    else {
      attribute.setValue(value);
    }

    return this;
  }

  /**
   * <p>
   * This sets an attribute value for this element.  Any existing attribute
   * with the same name and namespace URI is removed.
   * </p>
   *
   * @param name  name of the attribute to set
   * @param value value of the attribute to set
   * @param ns    namespace of the attribute to set. A null implies Namespace.NO_NAMESPACE.
   * @return this element modified
   * @throws IllegalNameException if the given name is illegal as an
   *                              attribute name, or if the namespace is an unprefixed default
   *                              namespace
   * @throws IllegalDataException if the given attribute value is
   *                              illegal character data (as determined by
   *                              {@link Verifier#checkCharacterData}).
   * @throws IllegalAddException  if the attribute namespace prefix
   *                              collides with another namespace prefix on the element.
   */
  public Element setAttribute(final String name, final String value, final Namespace ns) {
    final Attribute attribute = getAttribute(name, ns);
    if (attribute == null) {
      final Attribute newAttribute = new Attribute(name, value, ns);
      setAttribute(newAttribute);
    }
    else {
      attribute.setValue(value);
    }

    return this;
  }

  /**
   * <p>
   * This sets an attribute value for this element.  Any existing attribute
   * with the same name and namespace URI is removed.
   * </p>
   *
   * @param attribute <code>Attribute</code> to set
   * @return this element modified
   * @throws IllegalAddException if the attribute being added already has a
   *                             parent or if the attribute namespace prefix collides with another
   *                             namespace prefix on the element.
   */
  public Element setAttribute(final Attribute attribute) {
    getAttributeList().add(attribute);
    return this;
  }

  /**
   * <p>
   * This removes the attribute with the given name and within no
   * namespace. If no such attribute exists, this method does nothing.
   * </p>
   *
   * @param name name of attribute to remove
   * @return whether the attribute was removed
   */
  public boolean removeAttribute(final String name) {
    return removeAttribute(name, Namespace.NO_NAMESPACE);
  }

  public void sortChildren(Comparator<? super Element> comparator) {
    content.getView(Filters.element()).sort(comparator);
  }

  /**
   * <p>
   * This removes the attribute with the given name and within the
   * given Namespace.  If no such attribute exists, this method does
   * nothing.
   * </p>
   *
   * @param name name of attribute to remove
   * @param ns   namespace URI of attribute to remove. A null implies Namespace.NO_NAMESPACE.
   * @return whether the attribute was removed
   */
  public boolean removeAttribute(final String name, final Namespace ns) {
    if (attributes == null) {
      return false;
    }
    return getAttributeList().remove(name, ns);
  }

  /**
   * <p>
   * This removes the supplied Attribute should it exist.
   * </p>
   *
   * @param attribute Reference to the attribute to be removed.
   * @return whether the attribute was removed
   */
  public boolean removeAttribute(final Attribute attribute) {
    if (attributes == null) {
      return false;
    }
    return getAttributeList().remove(attribute);
  }

  @Override
  public String toString() {
    final StringBuilder stringForm = new StringBuilder(64)
      .append("[Element: <")
      .append(getQualifiedName());

    String namespaceUri = getNamespaceURI();
    if (!"".equals(namespaceUri)) {
      stringForm
        .append(" [Namespace: ")
        .append(namespaceUri)
        .append("]");
    }
    stringForm.append("/>]");

    return stringForm.toString();
  }

  /**
   * <p>
   * This returns a deep clone of this element.
   * The new element is detached from its parent, and getParent()
   * on the clone will return null.
   * </p>
   *
   * @return the clone of this element
   */
  @Override
  public Element clone() {
    final Element element = (Element)super.clone();

    // name and namespace are references to immutable objects
    // so super.clone() handles them ok

    // Reference to parent is copied by super.clone()
    // (Object.clone()) so we have to remove it
    // Actually, super is a Content, which has already detached in the
    // clone().
    // element.parent = null;

    // Reference to content list and attribute lists are copied by
    // super.clone() so we set it new lists if the original had lists
    element.content = new ContentList(element);
    element.attributes = attributes == null ? null : new AttributeList(element);

    // Cloning attributes
    if (attributes != null) {
      for (int i = 0; i < attributes.size(); i++) {
        final Attribute attribute = attributes.get(i);
        element.attributes.add(attribute.clone());
      }
    }

    // Cloning additional namespaces
    if (additionalNamespaces != null) {
      element.additionalNamespaces = new ArrayList<>(additionalNamespaces);
    }

    // Cloning content
    for (int i = 0; i < content.size(); i++) {
      final Content c = content.get(i);
      element.content.add(c.clone());
    }

    return element;
  }


  /**
   * Returns an iterator that walks over all descendants in document order.
   *
   * @return an iterator to walk descendants
   */
  @Override
  public Iterator<Content> getDescendants() {
    return new DescendantIterator(this);
  }

  /**
   * @deprecated Use {@link #getDescendants(org.jdom.filter2.Filter)}.
   */
  @SuppressWarnings("unused")
  @Deprecated
  public <F extends Content> Iterator<F> getDescendants(final Filter<F> filter) {
    return new FilterIterator<>(new DescendantIterator(this), AbstractFilter.toFilter2(filter));
  }

  public <F extends Content> Iterator<F> getDescendants(final org.jdom.filter2.Filter<F> filter) {
    return new FilterIterator<>(new DescendantIterator(this), filter);
  }

  /**
   * This returns a <code>List</code> of all the child elements
   * nested directly (one level deep) within this element, as
   * <code>Element</code> objects.  If this target element has no nested
   * elements, an empty List is returned.  The returned list is "live"
   * in document order and changes to it affect the element's actual
   * contents.
   *
   * <p>
   * Sequential traversal through the List is best done with a Iterator
   * since the underlying implement of List.size() may not be the most
   * efficient.
   * </p>
   *
   * <p>
   * No recursion is performed, so elements nested two levels deep
   * would have to be obtained with:
   * <pre>
   * <code>
   *   for(Element oneLevelDeep : topElement.getChildren()) {
   *     List&lt;Element&gt; twoLevelsDeep = oneLevelDeep.getChildren();
   *     // Do something with these children
   *   }
   * </code>
   * </pre>
   * </p>
   *
   * @return list of child <code>Element</code> objects for this element
   */
  public List<Element> getChildren() {
    return content.getView(Filters.element());
  }

  /**
   * This returns a <code>List</code> of all the child elements
   * nested directly (one level deep) within this element with the given
   * local name and belonging to no namespace, returned as
   * <code>Element</code> objects.  If this target element has no nested
   * elements with the given name outside a namespace, an empty List
   * is returned.  The returned list is "live" in document order
   * and changes to it affect the element's actual contents.
   * <p>
   * Please see the notes for <code>{@link #getChildren}</code>
   * for a code example.
   * </p>
   *
   * @param cname local name for the children to match
   * @return all matching child elements
   */
  public List<Element> getChildren(final String cname) {
    return getChildren(cname, Namespace.NO_NAMESPACE);
  }

  /**
   * This returns a <code>List</code> of all the child elements
   * nested directly (one level deep) within this element with the given
   * local name and belonging to the given Namespace, returned as
   * <code>Element</code> objects.  If this target element has no nested
   * elements with the given name in the given Namespace, an empty List
   * is returned.  The returned list is "live" in document order
   * and changes to it affect the element's actual contents.
   * <p>
   * Please see the notes for <code>{@link #getChildren}</code>
   * for a code example.
   * </p>
   *
   * @param cname local name for the children to match
   * @param ns    <code>Namespace</code> to search within. A null implies Namespace.NO_NAMESPACE.
   * @return all matching child elements
   */
  public List<Element> getChildren(final String cname, final Namespace ns) {
    return content.getView(new ElementFilter(cname, ns));
  }

  /**
   * This returns the first child element within this element with the
   * given local name and belonging to the given namespace.
   * If no elements exist for the specified name and namespace, null is
   * returned.
   *
   * @param cname local name of child element to match
   * @param ns    <code>Namespace</code> to search within. A null implies Namespace.NO_NAMESPACE.
   * @return the first matching child element, or null if not found
   */
  public Element getChild(String cname, Namespace ns) {
    Iterator<Element> iterator = content.getView(new ElementFilter(cname, ns)).iterator();
    return iterator.hasNext() ? iterator.next() : null;
  }

  /**
   * This returns the first child element within this element with the
   * given local name and belonging to no namespace.
   * If no elements exist for the specified name and namespace, null is
   * returned.
   *
   * @param cname local name of child element to match
   * @return the first matching child element, or null if not found
   */
  public Element getChild(String cname) {
    return getChild(cname, Namespace.NO_NAMESPACE);
  }

  /**
   * <p>
   * This removes the first child element (one level deep) with the
   * given local name and belonging to no namespace.
   * Returns true if a child was removed.
   * </p>
   *
   * @param cname the name of child elements to remove
   * @return whether deletion occurred
   */
  public boolean removeChild(final String cname) {
    return removeChild(cname, Namespace.NO_NAMESPACE);
  }

  /**
   * <p>
   * This removes the first child element (one level deep) with the
   * given local name and belonging to the given namespace.
   * Returns true if a child was removed.
   * </p>
   *
   * @param cname the name of child element to remove
   * @param ns    <code>Namespace</code> to search within. A null implies Namespace.NO_NAMESPACE.
   * @return whether deletion occurred
   */
  public boolean removeChild(String cname, final Namespace ns) {
    final ElementFilter filter = new ElementFilter(cname, ns);
    final List<Element> old = content.getView(filter);
    final Iterator<Element> iter = old.iterator();
    if (iter.hasNext()) {
      iter.next();
      iter.remove();
      return true;
    }

    return false;
  }

  /**
   * <p>
   * This removes all child elements (one level deep) with the
   * given local name and belonging to no namespace.
   * Returns true if any were removed.
   * </p>
   *
   * @param cname the name of child elements to remove
   * @return whether deletion occurred
   */
  public boolean removeChildren(final String cname) {
    return removeChildren(cname, Namespace.NO_NAMESPACE);
  }

  /**
   * <p>
   * This removes all child elements (one level deep) with the
   * given local name and belonging to the given namespace.
   * Returns true if any were removed.
   * </p>
   *
   * @param cname the name of child elements to remove
   * @param ns    <code>Namespace</code> to search within. A null implies Namespace.NO_NAMESPACE.
   * @return whether deletion occurred
   */
  public boolean removeChildren(final String cname, final Namespace ns) {
    boolean deletedSome = false;

    final ElementFilter filter = new ElementFilter(cname, ns);
    final List<Element> old = content.getView(filter);
    final Iterator<Element> iter = old.iterator();
    while (iter.hasNext()) {
      iter.next();
      iter.remove();
      deletedSome = true;
    }

    return deletedSome;
  }

  /**
   * Get the Namespaces that are in-scope on this Element. Element has the
   * most complex rules for the namespaces-in-scope.
   * <p>
   * The scope is built up from a number of sources following the rules of
   * XML namespace inheritance as follows:
   * <ul>
   * <li>The {@link Namespace#XML_NAMESPACE} is added
   * <li>The element's namespace is added (commonly
   * {@link Namespace#NO_NAMESPACE})
   * <li>All the attributes are inspected and their Namespaces are included
   * <li>All Namespaces declared on this Element using
   * {@link #addNamespaceDeclaration(Namespace)} are included.
   * <li>If the element has a parent then the parent's Namespace scope is
   * inspected, and any prefixes in the parent scope that are not yet bound
   * in this Element's scope are included.
   * <li>If the default Namespace (the no-prefix namespace) has not been
   * encountered for this Element then {@link Namespace#NO_NAMESPACE} is
   * included.
   * </ul>
   * The Element's Namespace scope consist of it's inherited Namespaces and
   * any modifications to that scope derived from the Element itself. If the
   * element is detached then it's inherited scope consists of just
   * If an element has no parent then
   * <p>
   * Note that the Element's Namespace will always be reported first.
   * <p>
   * <strong>Description copied from</strong>
   * {@link NamespaceAware#getNamespacesInScope()}:
   * <p>
   * {@inheritDoc}
   *
   * @see NamespaceAware
   */
  public List<Namespace> getNamespacesInScope() {
    // The assumption here is that all namespaces are valid,
    // that there are no namespace collisions on this element

    // This method is also the 'anchor' of the three getNamespaces*() methods
    // It does not make reference to this Element instance's other
    // getNamespace*() methods

    TreeMap<String, Namespace> namespaces = new TreeMap<>();
    namespaces.put(Namespace.XML_NAMESPACE.getPrefix(), Namespace.XML_NAMESPACE);
    namespaces.put(getNamespacePrefix(), getNamespace());
    if (additionalNamespaces != null) {
      for (Namespace ns : getAdditionalNamespaces()) {
        if (!namespaces.containsKey(ns.getPrefix())) {
          namespaces.put(ns.getPrefix(), ns);
        }
      }
    }
    if (attributes != null) {
      for (Attribute att : getAttributes()) {
        Namespace ns = att.getNamespace();
        if (!namespaces.containsKey(ns.getPrefix())) {
          namespaces.put(ns.getPrefix(), ns);
        }
      }
    }
    // Right, we now have all the namespaces that are current on this EElement.
    // Include any other namespaces that are inherited.
    final Element pnt = getParentElement();
    if (pnt != null) {
      for (Namespace ns : pnt.getNamespacesInScope()) {
        if (!namespaces.containsKey(ns.getPrefix())) {
          namespaces.put(ns.getPrefix(), ns);
        }
      }
    }

    if (pnt == null && !namespaces.containsKey("")) {
      // we are the root element, and there is no 'default' namespace.
      namespaces.put(Namespace.NO_NAMESPACE.getPrefix(), Namespace.NO_NAMESPACE);
    }

    ArrayList<Namespace> al = new ArrayList<>(namespaces.size());
    al.add(getNamespace());
    namespaces.remove(getNamespacePrefix());
    al.addAll(namespaces.values());

    return Collections.unmodifiableList(al);
  }

  // used externally
  @SuppressWarnings("unused")
  public List<Namespace> getNamespacesIntroduced() {
    if (getParentElement() == null) {
      // we introduce everything... except Namespace.XML_NAMESPACE
      List<Namespace> ret = new ArrayList<>(getNamespacesInScope());
      ret.removeIf(ns -> ns == Namespace.XML_NAMESPACE || ns == Namespace.NO_NAMESPACE);
      return Collections.unmodifiableList(ret);
    }

    // OK, the things we introduce are the prefixes we have in scope that
    // are *not* in our parent's scope.
    HashMap<String, Namespace> parents = new HashMap<>();
    for (Namespace ns : getParentElement().getNamespacesInScope()) {
      parents.put(ns.getPrefix(), ns);
    }

    ArrayList<Namespace> al = new ArrayList<>();
    for (Namespace ns : getNamespacesInScope()) {
      if (!parents.containsKey(ns.getPrefix()) || ns != parents.get(ns.getPrefix())) {
        // introduced
        al.add(ns);
      }
    }

    return Collections.unmodifiableList(al);
  }

  @Override
  public Element detach() {
    return (Element)super.detach();
  }

  @Override
  public void canContainContent(Content child, int index, boolean replace) throws IllegalAddException {
    if (child instanceof DocType) {
      throw new IllegalAddException("A DocType is not allowed except at the document level");
    }
  }

  public boolean isEmpty() {
    return !hasAttributes() && getContent().isEmpty();
  }

  /**
   * Sort the Attributes of this Element using a mechanism that is safe
   * for JDOM. Other child content will be unaffected.
   * <p>
   * {@link Collections#sort(List, Comparator)} is not appropriate for sorting
   * the Lists returned from {@link Element#getContent()} because those are
   * 'live' lists, and the Collections.sort() method uses an algorithm that
   * adds the content in the new location before removing it from the old.
   * This creates validation issues with content attempting to attach to a
   * parent before detaching first.
   * <p>
   * This method provides a safe means to conveniently sort the content.
   * <p>
   * A null comparator will sort the Attributes alphabetically first by prefix,
   * then by name
   *
   * @param comparator The Comparator to use for the sorting.
   */
  public void sortAttributes(Comparator<? super Attribute> comparator) {
    if (attributes != null) {
      attributes.sort(comparator);
    }
  }

  // maven uses serialization - jdom must support serialization

  private void writeObject(final ObjectOutputStream out) throws IOException {
    // sends out the name and namespace.
    out.defaultWriteObject();
    if (hasAdditionalNamespaces()) {
      final int ans = additionalNamespaces.size();
      out.writeInt(ans);
      for (Namespace additionalNamespace : additionalNamespaces) {
        out.writeObject(additionalNamespace);
      }
    }
    else {
      out.writeInt(0);
    }
    if (hasAttributes()) {
      final int ans = attributes.size();
      out.writeInt(ans);
      for (Attribute attribute : attributes) {
        out.writeObject(attribute);
      }
    }
    else {
      out.writeInt(0);
    }

    final int cs = content.size();
    out.writeInt(cs);
    for (Content value : content) {
      out.writeObject(value);
    }
  }

  private void readObject(final ObjectInputStream in)
    throws IOException, ClassNotFoundException {

    in.defaultReadObject();

    content = new ContentList(this);

    int nss = in.readInt();

    while (--nss >= 0) {
      addNamespaceDeclaration((Namespace)in.readObject());
    }

    int ats = in.readInt();
    while (--ats >= 0) {
      setAttribute((Attribute)in.readObject());
    }

    int cs = in.readInt();
    while (--cs >= 0) {
      addContent((Content)in.readObject());
    }
  }
}
