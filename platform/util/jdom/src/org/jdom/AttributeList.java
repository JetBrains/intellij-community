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

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * <code>AttributeList</code> represents legal JDOM
 * <code>{@link Attribute}</code> content.
 * <p>
 * This class is NOT PUBLIC; users should see it as a simple List
 * implementation, although it behaves something like a Set because you cannot
 * add duplicate Attributes. An attribute is considered duplicate if it has the
 * same Namespace URI and Attribute name as another existing Attribute.
 *
 * @author Alex Rosen
 * @author Philippe Riand
 * @author Bradley S. Huffman
 * @author Rolf Lear
 */
final class AttributeList extends AbstractList<Attribute> implements RandomAccess {
  /**
   * The initial size to start the backing array.
   */
  private static final int INITIAL_ARRAY_SIZE = 4;

  /**
   * The backing array
   */
  private Attribute[] attributeData;

  /**
   * The current size
   */
  private int size;

  /**
   * The parent Element
   */
  private final Element parent;

  private static final Comparator<Attribute> ATTRIBUTE_NATURAL =
    Comparator.comparing(Attribute::getNamespacePrefix).thenComparing(Attribute::getName);

  /**
   * Create a new instance of the AttributeList representing <i>parent</i>
   * Element's Attributes
   *
   * @param parent Element whose Attributes are to be held
   */
  AttributeList(final Element parent) {
    this.parent = parent;
  }

  /**
   * Check and add <i>attribute</i> to the end of the list or replace an
   * existing <code>Attribute</code> with the same name and
   * <code>Namespace</code>.
   *
   * @param attribute The <code>Attribute</code> to insert into the list.
   * @return true as specified by <code>Collection.add()</code>.
   * @throws IllegalAddException if validation rules prevent the add
   */
  @Override
  public boolean add(final Attribute attribute) {
    if (attribute.getParent() != null) {
      throw new IllegalAddException(
        "The attribute already has an existing parent \""
        + attribute.getParent().getQualifiedName() + "\"");
    }

    if (Verifier.checkNamespaceCollision(attribute, parent) != null) {
      throw new IllegalAddException(parent, attribute,
                                    Verifier.checkNamespaceCollision(attribute, parent));
    }

    // returns -1 if not exist
    final int duplicate = indexOfDuplicate(attribute);
    if (duplicate < 0) {
      attribute.setParent(parent);
      ensureCapacity(size + 1);
      attributeData[size++] = attribute;
      modCount++;
    }
    else {
      final Attribute old = attributeData[duplicate];
      old.setParent(null);
      attributeData[duplicate] = attribute;
      attribute.setParent(parent);
    }
    return true;
  }

  /**
   * Check and add <i>attribute</i> to this list at <i>index</i>.
   *
   * @param index     where to add/insert the <code>Attribute</code>
   * @param attribute <code>Attribute</code> to add
   * @throws IllegalAddException if validation rules prevent the add
   */
  @Override
  public void add(final int index, final Attribute attribute) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          " Size: " + size());
    }

    if (attribute.getParent() != null) {
      throw new IllegalAddException(
        "The attribute already has an existing parent \"" +
        attribute.getParent().getQualifiedName() + "\"");
    }
    final int duplicate = indexOfDuplicate(attribute);
    if (duplicate >= 0) {
      throw new IllegalAddException("Cannot add duplicate attribute");
    }

    final String reason = Verifier.checkNamespaceCollision(attribute, parent);
    if (reason != null) {
      throw new IllegalAddException(parent, attribute, reason);
    }

    attribute.setParent(parent);

    ensureCapacity(size + 1);
    if (index == size) {
      attributeData[size++] = attribute;
    }
    else {
      System.arraycopy(attributeData, index, attributeData, index + 1,
                       size - index);
      attributeData[index] = attribute;
      size++;
    }
    modCount++;
  }

  /**
   * Add all the <code>Attributes</code> in <i>collection</i>.
   *
   * @param collection The <code>Collection</code> of <code>Attributes</code> to add.
   * @return <code>true</code> if the list was modified as a result of the
   * add.
   * @throws IllegalAddException if validation rules prevent the addAll
   */
  @Override
  public boolean addAll(@NotNull Collection<? extends Attribute> collection) {
    return addAll(size(), collection);
  }

  /**
   * Inserts the <code>Attributes</code> in <i>collection</i> at the specified
   * <i>index</i> in this list.
   *
   * @param index      The offset at which to start adding the <code>Attributes</code>
   * @param collection The <code>Collection</code> containing the <code>Attributes</code>
   *                   to add.
   * @return <code>true</code> if the list was modified as a result of the
   * add.
   * @throws IllegalAddException if validation rules prevent the addAll
   */
  @Override
  public boolean addAll(final int index,
                        final Collection<? extends Attribute> collection) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          " Size: " + size());
    }

    if (collection == null) {
      throw new NullPointerException(
        "Can not add a null Collection to AttributeList");
    }
    final int addcnt = collection.size();
    if (addcnt == 0) {
      return false;
    }
    if (addcnt == 1) {
      // quick check for single-add.
      add(index, collection.iterator().next());
      return true;
    }

    ensureCapacity(size() + addcnt);

    final int tmpmodcount = modCount;
    boolean ok = false;

    int count = 0;

    try {
      for (Attribute att : collection) {
        add(index + count, att);
        count++;
      }
      ok = true;
    }
    finally {
      if (!ok) {
        while (--count >= 0) {
          remove(index + count);
        }
        modCount = tmpmodcount;
      }
    }

    return true;
  }

  /**
   * Clear the current list.
   */
  @Override
  public void clear() {
    if (attributeData != null) {
      while (size > 0) {
        size--;
        attributeData[size].setParent(null);
        attributeData[size] = null;
      }
    }
    modCount++;
  }

  /**
   * Clear the current list and set it to the contents of <i>collection</i>.
   *
   * @param collection The <code>Collection</code> to use.
   * @throws IllegalAddException if validation rules prevent the addAll
   */
  void clearAndSet(final Collection<? extends Attribute> collection) {
    if (collection == null || collection.isEmpty()) {
      clear();
      return;
    }

    // keep a backup in case we need to roll-back...
    final Attribute[] old = attributeData;
    final int oldSize = size;
    final int oldModCount = modCount;

    // clear the current system
    // we need to detatch before we add so that we don't run in to a problem
    // where an attribute in the to-add list is one that we are 'clearing'
    // first.
    while (size > 0) {
      old[--size].setParent(null);
    }
    size = 0;
    attributeData = null;

    boolean ok = false;
    try {
      addAll(0, collection);
      ok = true;
    }
    finally {
      if (!ok) {
        // we have an exception pending....
        // restore the old system.
        // re-attach the old stuff
        attributeData = old;
        while (size < oldSize) {
          attributeData[size++].setParent(parent);
        }
        modCount = oldModCount;
      }
    }
  }

  /**
   * Increases the capacity of this <code>AttributeList</code> instance, if
   * necessary, to ensure that it can hold at least the number of items
   * specified by the minimum capacity argument.
   *
   * @param minCapacity the desired minimum capacity.
   */
  private void ensureCapacity(final int minCapacity) {
    if (attributeData == null) {
      attributeData =
        new Attribute[Math.max(minCapacity, INITIAL_ARRAY_SIZE)];
      return;
    }
    else if (minCapacity < attributeData.length) {
      return;
    }
    // most JVM's allocate memory in multiples of 'double-words', on
    // 64-bit it's 16-bytes, on 32-bit it's 8 bytes which all means it makes
    // sense to increment the capacity in even values.
    attributeData = Arrays.copyOf(attributeData, ((minCapacity + INITIAL_ARRAY_SIZE) >>> 1) << 1);
  }

  /**
   * Retrieve the <code>Attribute</code> at <i>offset</i>.
   *
   * @param index The position of the <code>Attribute</code> to retrieve.
   * @return The <code>Attribute</code> at position <i>index</i>.
   */
  @Override
  public Attribute get(final int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          " Size: " + size());
    }

    return attributeData[index];
  }

  /**
   * Retrieve the <code>Attribute</code> with the given name and the same
   * <code>Namespace</code> URI as <i>namespace</i>.
   *
   * @param name      name of attribute to return
   * @param namespace indicate what <code>Namespace</code> URI to consider
   * @return the <code>Attribute</code>, or null if one doesn't exist.
   */
  Attribute get(final String name, final Namespace namespace) {
    final int index = indexOf(name, namespace);
    if (index < 0) {
      return null;
    }
    return attributeData[index];
  }

  /**
   * Return index of the <code>Attribute</code> with the given <i>name</i> and
   * the same Namespace URI as <i>namespace</i>.
   *
   * @param name      name of <code>Attribute</code> to retrieve
   * @param namespace indicate what <code>Namespace</code> URI to consider
   * @return the index of the attribute that matches the conditions, or
   * <code>-1</code> if there is none.
   */
  private int indexOf(final String name, final Namespace namespace) {
    if (attributeData != null) {
      if (namespace == null) {
        return indexOf(name, Namespace.NO_NAMESPACE);
      }
      final String uri = namespace.getURI();
      for (int i = 0; i < size; i++) {
        final Attribute att = attributeData[i];
        if (uri.equals(att.getNamespaceURI()) &&
            name.equals(att.getName())) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Remove the <code>Attribute</code> at <i>index</i>.
   *
   * @param index The offset of the <code>Attribute</code> to remove.
   * @return The removed <code>Attribute</code>.
   */
  @Override
  public Attribute remove(final int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          " Size: " + size());
    }

    final Attribute old = attributeData[index];
    old.setParent(null);
    System.arraycopy(attributeData, index + 1, attributeData, index,
                     size - index - 1);
    attributeData[--size] = null; // Let gc do its work
    modCount++;
    return old;
  }

  /**
   * Remove the <code>Attribute</code> with the specified name and the same
   * URI as <i>namespace</i>.
   *
   * @param name      name of <code>Attribute</code> to remove
   * @param namespace indicate what <code>Namespace</code> URI to consider
   * @return the <code>true</code> if attribute was removed,
   * <code>false</code> otherwise
   */
  boolean remove(final String name, final Namespace namespace) {
    final int index = indexOf(name, namespace);
    if (index < 0) {
      return false;
    }
    remove(index);
    return true;
  }

  /**
   * Set the <code>Attribute</code> at <i>index</i> to be <i>attribute</i>.
   *
   * @param index     The location to set the value to.
   * @param attribute The <code>Attribute</code> to set.
   * @return The replaced <code>Attribute</code>.
   * @throws IllegalAddException if validation rules prevent the set
   */
  @Override
  public Attribute set(final int index, final Attribute attribute) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          " Size: " + size());
    }

    if (attribute.getParent() != null) {
      throw new IllegalAddException(
        "The attribute already has an existing parent \"" +
        attribute.getParent().getQualifiedName() + "\"");
    }

    final int duplicate = indexOfDuplicate(attribute);
    if ((duplicate >= 0) && (duplicate != index)) {
      throw new IllegalAddException("Cannot set duplicate attribute");
    }

    final String reason = Verifier.checkNamespaceCollision(attribute, parent, index);
    if (reason != null) {
      throw new IllegalAddException(parent, attribute, reason);
    }

    final Attribute old = attributeData[index];
    old.setParent(null);

    attributeData[index] = attribute;
    attribute.setParent(parent);
    return old;
  }

  /**
   * Return index of attribute with same name and Namespace, or -1 if one
   * doesn't exist
   */
  private int indexOfDuplicate(final Attribute attribute) {
    return indexOf(attribute.getName(), attribute.getNamespace());
  }

  /**
   * Returns an <code>Iterator</code> over the <code>Attributes</code> in this
   * list in the proper sequence.
   *
   * @return an iterator.
   */
  @Override
  public @NotNull Iterator<Attribute> iterator() {
    return new ALIterator();
  }

  /**
   * Return the number of <code>Attributes</code> in this list
   *
   * @return The number of <code>Attributes</code> in this list.
   */
  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * Return this list as a <code>String</code>
   */
  @Override
  public String toString() {
    return super.toString();
  }

  /**
   * Unlike the Arrays.binarySearch, this method never expects an
   * "already exists" condition, we only ever add, thus there will never
   * be a negative insertion-point.
   *
   * @param indexes The pointers to search within
   * @param len     The number of pointers to search within
   * @param val     The pointer we are checking for.
   * @param comp    The Comparator to compare with
   * @return the insertion point.
   */
  private int binarySearch(final int[] indexes, final int len,
                           final int val, final Comparator<? super Attribute> comp) {
    int left = 0, mid, right = len - 1, cmp;
    final Attribute base = attributeData[val];
    while (left <= right) {
      mid = (left + right) >>> 1;
      cmp = comp.compare(base, attributeData[indexes[mid]]);
      if (cmp == 0) {
        while (mid < right && comp.compare(base, attributeData[indexes[mid + 1]]) == 0) {
          mid++;
        }
        return mid + 1;
      }
      else if (cmp < 0) {
        right = mid - 1;
      }
      else {
        left = mid + 1;
      }
    }
    return left;
  }

  private void sortInPlace(final int[] indexes) {
    // the indexes are a discrete set of values that have no duplicates,
    // and describe the relative order of each of them.
    // as a result, we can do some tricks....
    final int[] unsorted = indexes.clone();
    Arrays.sort(unsorted);
    final Attribute[] usc = new Attribute[unsorted.length];
    for (int i = 0; i < usc.length; i++) {
      usc[i] = attributeData[indexes[i]];
    }
    // usc contains the content in their pre-sorted order....
    for (int i = 0; i < indexes.length; i++) {
      attributeData[unsorted[i]] = usc[i];
    }
  }

  /**
   * Sort the attributes using the supplied comparator. The attributes are
   * never added using regular mechanisms, so there are never problems with
   * detached or already-attached Attributes. The sort happens 'in place'.
   * <p>
   * If the comparator identifies two (or more) Attributes to be equal, then
   * the relative order of those attributes will not be changed.
   *
   * @param comp The Comparator to use for sorting.
   */
  @Override
  public void sort(Comparator<? super Attribute> comp) {
    if (comp == null) {
      comp = ATTRIBUTE_NATURAL;
    }
    final int sz = size;
    int[] indexes = new int[sz];
    for (int i = 0; i < sz; i++) {
      final int ip = binarySearch(indexes, i, i, comp);
      if (ip < i) {
        System.arraycopy(indexes, ip, indexes, ip + 1, i - ip);
      }
      indexes[ip] = i;
    }
    sortInPlace(indexes);
  }

  /* * * * * * * * * * * * * ContentListIterator * * * * * * * * * * * * * */
  /* * * * * * * * * * * * * ContentListIterator * * * * * * * * * * * * * */

  /**
   * A fast iterator that can beat AbstractList because we can access the data
   * directly. This is important because so much code now uses the for-each
   * type loop <code>for (Attribute a : element.getAttributes()) {...}</code>,
   * and that uses iterator().
   *
   * @author Rolf Lear
   */
  private final class ALIterator implements Iterator<Attribute> {
    // The modCount to expect (or throw ConcurrentModeEx)
    private int expect;
    // the index of the next Attribute to return.
    private int cursor = 0;
    // whether it is legal to call remove()
    private boolean canremove = false;

    private ALIterator() {
      expect = modCount;
    }

    @Override
    public boolean hasNext() {
      return cursor < size;
    }

    @Override
    public Attribute next() {
      if (modCount != expect) {
        throw new ConcurrentModificationException("ContentList was " +
                                                  "modified outside of this Iterator");
      }
      if (cursor >= size) {
        throw new NoSuchElementException("Iterated beyond the end of " +
                                         "the ContentList.");
      }
      canremove = true;
      return attributeData[cursor++];
    }

    @Override
    public void remove() {
      if (modCount != expect) {
        throw new ConcurrentModificationException("ContentList was " +
                                                  "modified outside of this Iterator");
      }
      if (!canremove) {
        throw new IllegalStateException("Can only remove() content " +
                                        "after a call to next()");
      }
      AttributeList.this.remove(--cursor);
      expect = modCount;
      canremove = false;
    }
  }
}
