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

import org.jdom.util.IteratorIterable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

/**
 * An XML document. Methods allow access to the root element as well as the
 * {@link DocType} and other document-level information.
 *
 * @author Brett McLaughlin
 * @author Jason Hunter
 * @author Jools Enticknap
 * @author Bradley S. Huffman
 * @author Rolf Lear
 */
public final class Document extends CloneBase implements Parent, Serializable {
  /**
   * This document's content including comments, PIs, a possible
   * DocType, and a root element.
   * Subclasses have to track content using their own
   * mechanism.
   */
  private transient ContentList content = new ContentList(this);

  private String baseUri;

  // Supports the setProperty/getProperty calls
  private transient HashMap<String, Object> propertyMap = null;

  /**
   * Creates a new empty document.  A document must have a root element,
   * so this document will not be well-formed and accessor methods will
   * throw an IllegalStateException if this document is accessed before a
   * root element is added.  This method is most useful for build tools.
   */
  public Document() { }

  /**
   * This will create a new <code>Document</code>,
   * with the supplied <code>{@link Element}</code>
   * as the root element, the supplied
   * <code>{@link DocType}</code> declaration, and the specified
   * base URI.
   *
   * @param rootElement <code>Element</code> for document root.
   * @param docType     <code>DocType</code> declaration.
   * @param baseURI     the URI from which this document was loaded.
   * @throws IllegalAddException if the given docType object
   *                             is already attached to a document or the given
   *                             rootElement already has a parent
   */
  public Document(Element rootElement, DocType docType, String baseURI) {
    if (rootElement != null) {
      setRootElement(rootElement);
    }
    if (docType != null) {
      setDocType(docType);
    }
    if (baseURI != null) {
      setBaseURI(baseURI);
    }
  }

  /**
   * This will create a new <code>Document</code>,
   * with the supplied <code>{@link Element}</code>
   * as the root element and the supplied
   * <code>{@link DocType}</code> declaration.
   *
   * @param rootElement <code>Element</code> for document root.
   * @param docType     <code>DocType</code> declaration.
   * @throws IllegalAddException if the given DocType object
   *                             is already attached to a document or the given
   *                             rootElement already has a parent
   */
  public Document(Element rootElement, DocType docType) {
    this(rootElement, docType, null);
  }

  /**
   * This will create a new <code>Document</code>,
   * with the supplied <code>{@link Element}</code>
   * as the root element, and no <code>{@link DocType}</code>
   * declaration.
   *
   * @param rootElement <code>Element</code> for document root
   * @throws IllegalAddException if the given rootElement already has
   *                             a parent.
   */
  public Document(Element rootElement) {
    this(rootElement, null, null);
  }

  /**
   * This will create a new <code>Document</code>,
   * with the supplied list of content, and a
   * <code>{@link DocType}</code> declaration only if the content
   * contains a DocType instance.  A null list is treated the
   * same as the no-arg constructor.
   *
   * @param content <code>List</code> of starter content
   * @throws IllegalAddException if the List contains more than
   *                             one Element or objects of illegal types.
   */
  public Document(List<? extends Content> content) {
    setContent(content);
  }

  @Override
  public int getContentSize() {
    return content.size();
  }

  @Override
  public int indexOf(Content child) {
    return content.indexOf(child);
  }

  /**
   * This will return <code>true</code> if this document has a
   * root element, <code>false</code> otherwise.
   *
   * @return <code>true</code> if this document has a root element,
   * <code>false</code> otherwise.
   */
  public boolean hasRootElement() {
    return content.indexOfFirstElement() >= 0;
  }

  /**
   * This will return the root <code>Element</code>
   * for this <code>Document</code>
   *
   * @return <code>Element</code> - the document's root element
   * @throws IllegalStateException if the root element hasn't been set
   */
  public Element getRootElement() {
    int index = content.indexOfFirstElement();
    if (index < 0) {
      throw new IllegalStateException("Root element not set");
    }
    return (Element)content.get(index);
  }

  /**
   * This sets the root <code>{@link Element}</code> for the
   * <code>Document</code>. If the document already has a root
   * element, it is replaced.
   *
   * @param rootElement <code>Element</code> to be new root.
   * @return <code>Document</code> - modified Document.
   * @throws IllegalAddException if the given rootElement already has
   *                             a parent.
   */
  public Document setRootElement(Element rootElement) {
    int index = content.indexOfFirstElement();
    if (index < 0) {
      content.add(rootElement);
    }
    else {
      content.set(index, rootElement);
    }
    return this;
  }

  /**
   * Detach the root <code>{@link Element}</code> from this document.
   *
   * @return removed root <code>Element</code>
   */
  public Element detachRootElement() {
    int index = content.indexOfFirstElement();
    if (index < 0) {
      return null;
    }
    return (Element)removeContent(index);
  }

  /**
   * This will return the <code>{@link DocType}</code>
   * declaration for this <code>Document</code>, or
   * <code>null</code> if none exists.
   *
   * @return <code>DocType</code> - the DOCTYPE declaration.
   */
  public DocType getDocType() {
    int index = content.indexOfDocType();
    if (index < 0) {
      return null;
    }
    return (DocType)content.get(index);
  }

  /**
   * This will set the <code>{@link DocType}</code>
   * declaration for this <code>Document</code>. Note
   * that a DocType can only be attached to one Document.
   * Attempting to set the DocType to a DocType object
   * that already belongs to a Document will result in an
   * IllegalAddException being thrown.
   *
   * @param docType <code>DocType</code> declaration.
   * @return object on which the method was invoked
   * @throws IllegalAddException if the given docType is
   *                             already attached to a Document.
   */
  public Document setDocType(DocType docType) {
    if (docType == null) {
      // Remove any existing doctype
      int docTypeIndex = content.indexOfDocType();
      if (docTypeIndex >= 0) content.remove(docTypeIndex);
      return this;
    }

    if (docType.getParent() != null) {
      throw new IllegalAddException(docType,
                                    "The DocType already is attached to a document");
    }

    // Add DocType to head if new, replace old otherwise
    int docTypeIndex = content.indexOfDocType();
    if (docTypeIndex < 0) {
      content.add(0, docType);
    }
    else {
      content.set(docTypeIndex, docType);
    }

    return this;
  }

  /**
   * Appends the child to the end of the content list.
   *
   * @param child child to append to end of content list
   * @return the document on which the method was called
   * @throws IllegalAddException if the given child already has a parent.
   */
  @Override
  public Document addContent(Content child) {
    content.add(child);
    return this;
  }

  /**
   * Appends all children in the given collection to the end of
   * the content list.  In event of an exception during add the
   * original content will be unchanged and the objects in the supplied
   * collection will be unaltered.
   *
   * @param c collection to append
   * @return the document on which the method was called
   * @throws IllegalAddException if any item in the collection
   *                             already has a parent or is of an illegal type.
   */
  @Override
  public Document addContent(Collection<? extends Content> c) {
    content.addAll(c);
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
  public Document addContent(int index, Content child) {
    content.add(index, child);
    return this;
  }

  /**
   * Inserts the content in a collection into the content list
   * at the given index.  In event of an exception the original content
   * will be unchanged and the objects in the supplied collection will be
   * unaltered.
   *
   * @param index location for adding the collection
   * @param c     collection to insert
   * @return the parent on which the method was called
   * @throws IndexOutOfBoundsException if index is negative or beyond
   *                                   the current number of children
   * @throws IllegalAddException       if any item in the collection
   *                                   already has a parent or is of an illegal type.
   */
  @Override
  public Document addContent(int index, Collection<? extends Content> c) {
    content.addAll(index, c);
    return this;
  }

  @Override
  public List<Content> cloneContent() {
    int size = getContentSize();
    List<Content> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      Content child = getContent(i);
      list.add(child.clone());
    }
    return list;
  }

  @Override
  public Content getContent(int index) {
    return content.get(index);
  }

  /**
   * This will return all content for the <code>Document</code>.
   * The returned list is "live" in document order and changes to it
   * affect the document's actual content.
   *
   * <p>
   * Sequential traversal through the List is best done with a Iterator
   * since the underlying implement of List.size() may require walking the
   * entire list.
   * </p>
   *
   * @return <code>List</code> - all Document content
   * @throws IllegalStateException if the root element hasn't been set
   */
  @Override
  public List<Content> getContent() {
    if (!hasRootElement()) {
      throw new IllegalStateException("Root element not set");
    }
    return content;
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
   * This sets the content of the <code>Document</code>.  The supplied
   * List should contain only objects of type <code>Element</code>,
   * <code>Comment</code>, and <code>ProcessingInstruction</code>.
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
   * @param newContent <code>List</code> of content to set
   * @return this document modified
   * @throws IllegalAddException if the List contains objects of
   *                             illegal types or with existing parentage.
   */
  public Document setContent(Collection<? extends Content> newContent) {
    content.clearAndSet(newContent);
    return this;
  }

  /**
   * <p>
   * Sets the effective URI from which this document was loaded,
   * and against which relative URLs in this document will be resolved.
   * </p>
   *
   * @param uri the base URI of this document
   */
  public void setBaseURI(String uri) {
    this.baseUri = uri;  // XXX We don't check the URI
  }

  /**
   * <p>
   * Returns the URI from which this document was loaded,
   * or null if this is not known.
   * </p>
   *
   * @return the base URI of this document
   */
  @SuppressWarnings("unused")
  public String getBaseURI() {
    return baseUri;
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
   * @return this document instance
   * @throws IllegalAddException       if the supplied child is already attached
   *                                   or not legal content for this parent.
   * @throws IndexOutOfBoundsException if index is negative or greater
   *                                   than the current number of children.
   */
  public Document setContent(int index, Content child) {
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
   * @param collection - collection of content to add.
   * @return object on which the method was invoked
   * @throws IllegalAddException       if the collection contains objects of
   *                                   illegal types.
   * @throws IndexOutOfBoundsException if index is negative or greater
   *                                   than the current number of children.
   */
  public Document setContent(int index, Collection<? extends Content> collection) {
    content.remove(index);
    content.addAll(index, collection);
    return this;
  }

  @Override
  public boolean removeContent(Content child) {
    return content.remove(child);
  }

  @Override
  public Content removeContent(int index) {
    return content.remove(index);
  }

  /**
   * Set this document's content to be the supplied child.
   * <p>
   * If the supplied child is legal content for a Document, and before
   * it is added, all content in the current content list will
   * be cleared and all current children will have their parentage set to
   * null.
   * Passing a null child clears the existing content.
   * <p>
   * In event of an exception the original content will be unchanged and
   * the supplied child will be unaltered.
   *
   * @param child new content to replace existing content
   * @return the parent on which the method was called
   * @throws IllegalAddException if the supplied child is already attached
   *                             or not legal content for this parent
   */
  public Document setContent(Content child) {
    content.clear();
    content.add(child);
    return this;
  }

  @Override
  public String toString() {
    StringBuilder stringForm = new StringBuilder()
      .append("[Document: ");

    DocType docType = getDocType();
    if (docType != null) {
      stringForm.append(docType)
        .append(", ");
    }
    else {
      stringForm.append(" No DOCTYPE declaration, ");
    }

    Element rootElement = hasRootElement() ? getRootElement() : null;
    if (rootElement != null) {
      stringForm.append("Root is ")
        .append(rootElement);
    }
    else {
      stringForm.append(" No root element"); // shouldn't happen
    }

    stringForm.append("]");

    return stringForm.toString();
  }

  /**
   * This tests for equality of this <code>Document</code> to the supplied
   * <code>Object</code>.
   *
   * @param ob <code>Object</code> to compare to
   * @return <code>boolean</code> whether the <code>Document</code> is
   * equal to the supplied <code>Object</code>
   */
  @Override
  public boolean equals(Object ob) {
    return ob == this;
  }

  /**
   * This returns the hash code for this <code>Document</code>.
   *
   * @return <code>int</code> hash code
   */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  /**
   * This will return a deep clone of this <code>Document</code>.
   *
   * @return <code>Object</code> clone of this <code>Document</code>
   */
  @Override
  public Document clone() {
    final Document doc = (Document)super.clone();

    // The clone has a reference to this object's content list, so
    // overwrite with an empty list
    doc.content = new ContentList(doc);

    // Add the cloned content to clone

    for (int i = 0; i < content.size(); i++) {
      Object obj = content.get(i);
      if (obj instanceof Element) {
        Element element = ((Element)obj).clone();
        doc.content.add(element);
      }
      else if (obj instanceof Comment) {
        Comment comment = ((Comment)obj).clone();
        doc.content.add(comment);
      }
      else if (obj instanceof ProcessingInstruction) {
        ProcessingInstruction pi = ((ProcessingInstruction)obj).clone();
        doc.content.add(pi);
      }
      else if (obj instanceof DocType) {
        DocType dt = ((DocType)obj).clone();
        doc.content.add(dt);
      }
    }

    return doc;
  }

  /**
   * Returns an iterator that walks over all descendants in document order.
   *
   * @return an iterator to walk descendants
   */
  @Override
  public IteratorIterable<Content> getDescendants() {
    return new DescendantIterator(this);
  }

  /**
   * Always returns null, Document cannot have a parent.
   *
   * @return null
   */
  @Override
  public Parent getParent() {
    return null;  // documents never have parents
  }


  /**
   * Always returns this Document Instance
   *
   * @return 'this' because this Document is its own Document
   */
  @Override
  public Document getDocument() {
    return this;
  }

  /**
   * Assigns an arbitrary object to be associated with this document under
   * the given "id" string.  Null values are permitted.  'id' Strings beginning
   * with http://www.jdom.org/ are reserved for JDOM use. Properties set with
   * this method will not be serialized with the rest of this Document, should
   * serialization need to be done.
   *
   * @param id    the id of the stored <code>Object</code>
   * @param value the <code>Object</code> to store
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  public void setProperty(String id, Object value) {
    if (propertyMap == null) {
      propertyMap = new HashMap<>();
    }
    propertyMap.put(id, value);
  }

  /**
   * Returns the object associated with this document under the given "id"
   * string, or null if there is no binding or if the binding explicitly
   * stored a null value.
   *
   * @param id the id of the stored <code>Object</code> to return
   * @return the <code>Object</code> associated with the given id
   */
  public Object getProperty(String id) {
    if (propertyMap == null) {
      return null;
    }
    return propertyMap.get(id);
  }

  @Override
  public void canContainContent(Content child, int index, boolean replace) {
    if (child instanceof Element) {
      int cre = content.indexOfFirstElement();
      if (replace && cre == index) {
        return;
      }
      if (cre >= 0) {
        throw new IllegalAddException(
          "Cannot add a second root element, only one is allowed");
      }
      if (content.indexOfDocType() >= index) {
        throw new IllegalAddException(
          "A root element cannot be added before the DocType");
      }
    }
    if (child instanceof DocType) {
      int cdt = content.indexOfDocType();
      if (replace && cdt == index) {
        // It's OK to replace an existing DocType
        return;
      }
      if (cdt >= 0) {
        throw new IllegalAddException(
          "Cannot add a second doctype, only one is allowed");
      }
      int firstElt = content.indexOfFirstElement();
      if (firstElt != -1 && firstElt < index) {
        throw new IllegalAddException(
          "A DocType cannot be added after the root element");
      }
    }

    if (child instanceof CDATA) {
      throw new IllegalAddException("A CDATA is not allowed at the document root");
    }

    if (child instanceof Text) {
      if (Verifier.isAllXMLWhitespace(((Text)child).getText())) {
        // only whitespace, not a problem.
        return;
      }
      throw new IllegalAddException("A Text is not allowed at the document root");
    }

    if (child instanceof EntityRef) {
      throw new IllegalAddException("An EntityRef is not allowed at the document root");
    }
  }

  /**
   * JDOM2 Serialization. In this case, DocType is simple.
   */
  private static final long serialVersionUID = 200L;

  /**
   * Serialize out the Element.
   *
   * @param out where to write the Element to.
   * @throws IOException if there is a writing problem.
   * @serialData <strong>Document Properties are not serialized!</strong>
   * <p>
   * The Stream protocol is:
   * <ol>
   *   <li>The BaseURI using default Serialization.
   *   <li>The count of child Content
   *   <li>The actual Child Content.
   * </ol>
   */
  private void writeObject(final ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    final int cs = content.size();
    out.writeInt(cs);
    for (int i = 0; i < cs; i++) {
      out.writeObject(getContent(i));
    }
  }

  /**
   * Read an Element off the ObjectInputStream.
   *
   * @param in where to read the Element from.
   * @throws IOException            if there is a reading problem.
   * @throws ClassNotFoundException when a class cannot be found
   * @see #writeObject(ObjectOutputStream)
   */
  private void readObject(final ObjectInputStream in)
    throws IOException, ClassNotFoundException {

    in.defaultReadObject();

    content = new ContentList(this);

    int cs = in.readInt();
    while (--cs >= 0) {
      addContent((Content)in.readObject());
    }
  }
}
