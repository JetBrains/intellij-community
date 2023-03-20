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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Interface for JDOM objects which are allowed to contain
 * {@link Content} content - {@link Element} and {@link Document}.
 *
 * @author Bradley S. Huffman
 * @author Jason Hunter
 * @author Rolf Lear
 * @see Content
 * @see Document
 * @see Element
 */
public interface Parent extends Cloneable {
  /**
   * Returns the number of children in this parent's content list.
   * Children may be any {@link Content} type.
   *
   * @return number of children
   */
  int getContentSize();

  /**
   * Returns the index of the supplied child in the content list,
   * or -1 if not a child of this parent.
   *
   * @param child child to search for
   * @return index of child, or -1 if not found
   */
  int indexOf(Content child);

  /**
   * Returns a list containing detached clones of this parent's content list.
   *
   * @return list of cloned child content
   */
  List<Content> cloneContent();

  /**
   * Returns the child at the given index.
   *
   * @param index location of desired child
   * @return child at the given index
   * @throws IndexOutOfBoundsException if index is negative or beyond
   *                                   the current number of children
   * @throws IllegalStateException     if parent is a Document
   *                                   and the root element is not set
   */
  Content getContent(int index);

  /**
   * Returns the full content of this parent as a {@link List}
   * which contains objects of type {@link Content}. The returned list is
   * <b>"live"</b> and in document order. Any modifications
   * to it affect the element's actual contents. Modifications are checked
   * for conformance to XML 1.0 rules.
   * <p>
   * Sequential traversal through the List is best done with an Iterator
   * since the underlying implement of {@link List#size} may
   * require walking the entire list and indexed lookups may require
   * starting at the beginning each time.
   *
   * @return a list of the content of the parent
   * @throws IllegalStateException if parent is a Document
   *                               and the root element is not set
   */
  List<Content> getContent();

  /**
   * Removes all content from this parent and returns the detached
   * children.
   *
   * @return list of the old content detached from this parent
   */
  List<Content> removeContent();

  /**
   * Removes a single child node from the content list.
   *
   * @param child child to remove
   * @return whether the removal occurred
   */
  boolean removeContent(Content child);

  /**
   * Removes and returns the child at the given
   * index, or returns null if there's no such child.
   *
   * @param index index of child to remove
   * @return detached child at given index or null if no
   * @throws IndexOutOfBoundsException if index is negative or beyond
   *                                   the current number of children
   */
  Content removeContent(int index);

  /**
   * Obtain a deep, unattached copy of this parent and it's children.
   *
   * @return a deep copy of this parent and it's children.
   */
  Parent clone();

  /**
   * Returns an {@link Iterator} that walks over all descendants
   * in document order.
   * <p>
   * Note that this method returns an IteratorIterable instance, which means
   * that you can use it either as an Iterator, or an Iterable, allowing both:
   * <p>
   * <pre>
   *   for (Iterator<Content> it = parent.getDescendants();
   *           it.hasNext();) {
   *       Content c = it.next();
   *       ....
   *   }
   * </pre>
   * and
   * <pre>
   *   for (Content c : parent.getDescendants()) {
   *       ....
   *   }
   * </pre>
   * The Iterator version is most useful if you need to do list modification
   * on the iterator (using remove()), and for compatibility with JDOM 1.x
   *
   * @return an iterator to walk descendants
   */
  Iterator<Content> getDescendants();

  /**
   * Return this parent's parent, or null if this parent is currently
   * not attached to another parent. This is the same method as in Content but
   * also added to Parent to allow easier up-the-tree walking.
   *
   * @return this parent's parent or null if none
   */
  Parent getParent();

  /**
   * Return this parent's owning document or null if the branch containing
   * this parent is currently not attached to a document.
   *
   * @return this child's owning document or null if none
   */
  Document getDocument();

  /**
   * Test whether this Parent instance can contain the specified content
   * at the specified position.
   *
   * @param content The content to be checked
   * @param index   The location where the content would be put.
   * @param replace true if the intention is to replace the content already at
   *                the index.
   * @throws IllegalAddException if there is a problem with the content
   */
  void canContainContent(Content content, int index, boolean replace) throws IllegalAddException;

  /**
   * Appends the child to the end of the content list.
   *
   * @param child child to append to end of content list
   * @return the Parent instance on which the method was called
   * @throws IllegalAddException if the given child already has a parent.
   */
  Parent addContent(Content child);

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
  Parent addContent(Collection<? extends Content> c);

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
  Parent addContent(int index, Content child);

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
  Parent addContent(int index, Collection<? extends Content> c);
}
