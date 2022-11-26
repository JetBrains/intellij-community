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
    from the JDOM Project Management <request_AT_jdom_DOT_org).

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

import org.jdom.filter2.Filter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

/**
 * A non-public list implementation holding only legal JDOM content, including
 * content for Document or Element nodes. Users see this class as a simple List
 * implementation.
 *
 * @author Alex Rosen
 * @author Philippe Riand
 * @author Bradley S. Huffman
 * @author Rolf Lear
 * @see DocType
 * @see CDATA
 * @see Comment
 * @see Element
 * @see EntityRef
 * @see ProcessingInstruction
 * @see Text
 */
final class ContentList extends AbstractList<Content> implements RandomAccess {
  private static final int INITIAL_ARRAY_SIZE = 4;

  /**
   * Our backing list
   */
  private Content[] elementData = null;

  /**
   * The amount of valid content in elementData
   */
  private int size;

  /**
   * Completely remove references to AbstractList.modCount because in
   * ContentList it is confusing. As a consequence we also need to implement
   * a custom ListIterator for ContentList so that we don't use any of the
   * AbstractList iterators which use modCount.... so we have our own
   * ConcurrentModification checking.
   */
  private transient int sizeModCount = Integer.MIN_VALUE;

  /**
   * modCount is used for concurrent modification, but dataModCount is used
   * for refreshing filters.
   */
  private transient int dataModiCount = Integer.MIN_VALUE;

  /**
   * Document or Element this list belongs to
   */
  private final Parent parent;

  /**
   * Force either a Document or Element parent
   *
   * @param parent the Element this ContentList belongs to.
   */
  ContentList(final Parent parent) {
    this.parent = parent;
  }

  /**
   * In the FilterList and FilterList iterators it becomes confusing as to
   * which modCount is being used. This formalizes the process, and using
   * (set/get/inc)ModCount() is the only thing you should see in the remainder
   * of this code.
   *
   * @param sizemod the value to set for the size-mod count.
   * @param datamod the value to set for the data-mod count.
   */
  private void setModCount(final int sizemod, final int datamod) {
    sizeModCount = sizemod;
    dataModiCount = datamod;
  }

  /**
   * In the FilterList and FilterList iterators it becomes confusing as to
   * which modCount is being used. This formalizes the process, and using
   * (set/get/inc)ModCount() is the only thing you should see in the remainder
   * of this code.
   *
   * @return mod the value.
   */
  private int getModCount() {
    return sizeModCount;
  }

  /**
   * In the FilterList and FilterList iterators it becomes confusing as to
   * which modCount is being used. This formalizes the process, and using
   * (set/get/inc)ModCount() is the only thing you should see in the remainder
   * of this code.
   */
  private void incModCount() {
    // indicate there's a change to data
    dataModiCount++;
    // indicate there's a change to the size
    sizeModCount++;
  }

  private void incDataModOnly() {
    dataModiCount++;
  }

  /**
   * Get the modcount of data changes.
   *
   * @return the current data mode count.
   */
  private int getDataModCount() {
    return dataModiCount;
  }

  private void checkIndex(final int index, final boolean excludes) {
    final int max = excludes ? size - 1 : size;

    if (index < 0 || index > max) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          " Size: " + size);
    }
  }

  private void checkPreConditions(final Content child, final int index,
                                  final boolean replace) {
    if (child == null) {
      throw new NullPointerException("Cannot add null object");
    }

    checkIndex(index, replace);

    if (child.getParent() != null) {
      // the content to be added already has a parent.
      final Parent p = child.getParent();
      if (p instanceof Document) {
        throw new IllegalAddException((Element)child,
                                      "The Content already has an existing parent document");
      }
      throw new IllegalAddException(
        "The Content already has an existing parent \"" +
        ((Element)p).getQualifiedName() + "\"");
    }

    if (child == parent) {
      throw new IllegalAddException(
        "The Element cannot be added to itself");
    }

    // Detect if we have <a><b><c/></b></a> and c.add(a)
    if ((parent instanceof Element && child instanceof Element) &&
        ((Element)child).isAncestor((Element)parent)) {
      throw new IllegalAddException(
        "The Element cannot be added as a descendent of itself");
    }
  }

  /**
   * Check and add the <code>Content</code> to this list at the given index.
   * Inserts the specified object at the specified position in this list.
   * Shifts the object currently at that position (if any) and any subsequent
   * objects to the right (adds one to their indices).
   *
   * @param index index where to add <code>Element</code>
   * @param child <code>Content</code> to add
   */
  @Override
  public void add(final int index, final Content child) {
    // Confirm basic sanity of child.
    checkPreConditions(child, index, false);
    // Check to see whether this parent believes it can contain this content
    parent.canContainContent(child, index, false);

    child.setParent(parent);

    ensureCapacity(size + 1);
    if (index == size) {
      elementData[size++] = child;
    }
    else {
      System.arraycopy(elementData, index, elementData, index + 1, size - index);
      elementData[index] = child;
      size++;
    }
    // Successful add's increment the AbstractList's modCount
    incModCount();
  }

  /**
   * Add the specified collection to the end of this list.
   *
   * @param collection The collection to add to the list.
   * @return <code>true</code> if the list was modified as a result of the
   * add.
   */
  @Override
  public boolean addAll(final @NotNull Collection<? extends Content> collection) {
    return addAll(size, collection);
  }

  /**
   * Inserts the specified collection at the specified position in this list.
   * Shifts the object currently at that position (if any) and any subsequent
   * objects to the right (adds one to their indices).
   *
   * @param index      The offset to start adding the data in the collection
   * @param collection The collection to insert into the list.
   * @return <code>true</code> if the list was modified as a result of the
   * add. throws IndexOutOfBoundsException if index < 0 || index >
   * size()
   */
  @Override
  public boolean addAll(final int index,
                        final Collection<? extends Content> collection) {
    if ((collection == null)) {
      throw new NullPointerException(
        "Can not add a null collection to the ContentList");
    }

    checkIndex(index, false);

    if (collection.isEmpty()) {
      // some collections are expensive to get the size of.
      // use isEmpty().
      return false;
    }
    final int addcnt = collection.size();
    if (addcnt == 1) {
      // quick check for single-add.
      add(index, collection.iterator().next());
      return true;
    }

    ensureCapacity(size() + addcnt);

    final int tmpmodcount = getModCount();
    final int tmpdmc = getDataModCount();
    boolean ok = false;

    int count = 0;

    try {
      for (Content c : collection) {
        add(index + count, c);
        count++;
      }
      ok = true;
    }
    finally {
      if (!ok) {
        // something failed... remove all added content
        while (--count >= 0) {
          remove(index + count);
        }
        // restore the mod-counts.
        setModCount(tmpmodcount, tmpdmc);
      }
    }

    return true;
  }

  /**
   * Clear the current list.
   */
  @Override
  public void clear() {
    if (elementData != null) {
      for (int i = 0; i < size; i++) {
        Content obj = elementData[i];
        removeParent(obj);
      }
      elementData = null;
      size = 0;
    }
    incModCount();
  }

  /**
   * Clear the current list and set it to the contents of the
   * <code>Collection</code>. object.
   *
   * @param collection The collection to use.
   */
  void clearAndSet(final Collection<? extends Content> collection) {
    if (collection == null || collection.isEmpty()) {
      clear();
      return;
    }

    // keep a backup in case we need to roll-back...
    final Content[] old = elementData;
    final int oldSize = size;
    final int oldModCount = getModCount();
    final int oldDataModCount = getDataModCount();

    // clear the current system
    // we need to detach before we add so that we don't run in to a problem
    // where a content in the to-add list is one that we are 'clearing'
    // first.
    while (size > 0) {
      old[--size].setParent(null);
    }
    size = 0;
    elementData = null;

    boolean ok = false;
    try {
      addAll(0, collection);
      ok = true;
    }
    finally {
      if (!ok) {
        // we have an exception pending....
        // restore the old system.
        // we do not need to worry about the added content
        // because the failed addAll will clear it up.
        // re-attach the old stuff
        elementData = old;
        while (size < oldSize) {
          elementData[size++].setParent(parent);
        }
        setModCount(oldModCount, oldDataModCount);
      }
    }
  }

  /**
   * Increases the capacity of this <code>ContentList</code> instance, if
   * necessary, to ensure that it can hold at least the number of items
   * specified by the minimum capacity argument.
   *
   * @param minCapacity the desired minimum capacity.
   */
  void ensureCapacity(final int minCapacity) {
    if (elementData == null) {
      elementData = new Content[Math.max(minCapacity, INITIAL_ARRAY_SIZE)];
      return;
    }
    else if (minCapacity < elementData.length) {
      return;
    }
    // use algorithm Wilf suggests which is essentially the same
    // as algorithm as ArrayList.ensureCapacity....
    // typically the minCapacity is only slightly larger than
    // the current capacity.... so grow from the current capacity
    // with a double-check.
    final int newcap = ((size * 3) / 2) + 1;
    final int len = (Math.max(newcap, minCapacity));
    elementData = Arrays.copyOf(elementData, len);
  }

  /**
   * Return the object at the specified offset.
   *
   * @param index The offset of the object.
   * @return The Object which was returned.
   */
  @Override
  public Content get(final int index) {
    checkIndex(index, true);
    return elementData[index];
  }

  /**
   * Return a view of this list based on the given filter.
   *
   * @param <E>    The Generic type of the content as set by the Filter.
   * @param filter <code>Filter</code> for this view.
   * @return a list representing the rules of the <code>Filter</code>.
   */
  <E extends Content> List<E> getView(Filter<E> filter) {
    return new FilterList<>(filter);
  }

  public Stream<Content> content() {
    return Arrays.stream(elementData);
  }

  /**
   * Return the index of the first Element in the list. If the parent is a
   * <code>Document</code> then the element is the root element. If the list
   * contains no Elements, it returns -1.
   *
   * @return index of first element, or -1 if one doesn't exist
   */
  int indexOfFirstElement() {
    if (elementData != null) {
      for (int i = 0; i < size; i++) {
        if (elementData[i] instanceof Element) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Return the index of the DocType element in the list. If the list contains
   * no DocType, it returns -1.
   *
   * @return index of the DocType, or -1 if it doesn't exist
   */
  int indexOfDocType() {
    if (elementData != null) {
      for (int i = 0; i < size; i++) {
        if (elementData[i] instanceof DocType) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Remove the object at the specified offset.
   *
   * @param index The offset of the object.
   * @return The Object which was removed.
   */
  @Override
  public Content remove(final int index) {
    checkIndex(index, true);

    final Content old = elementData[index];
    removeParent(old);
    System.arraycopy(elementData, index + 1, elementData, index, size - index - 1);
    elementData[--size] = null; // Let gc do its work
    incModCount();
    return old;
  }

  /**
   * Remove the parent of a Object
   */
  private static void removeParent(final Content c) {
    c.setParent(null);
  }

  /**
   * Set the object at the specified location to the supplied object.
   *
   * @param index The location to set the value to.
   * @param child The location to set the value to.
   * @return The object which was replaced. throws IndexOutOfBoundsException
   * if index < 0 || index >= size()
   */
  @Override
  public Content set(final int index, final Content child) {
    // Confirm basic sanity of child.
    checkPreConditions(child, index, true);

    // Ensure the detail checks out OK too.
    parent.canContainContent(child, index, true);

    /*
     * Do a special case of set() where we don't do a remove() then add()
     * because that affects the modCount. We want to do a true set(). See
     * issue #15
     */

    final Content old = elementData[index];
    removeParent(old);
    child.setParent(parent);
    elementData[index] = child;
    // for set method we increment dataModCount, but not modCount
    // set does not change the structure of the List (size())
    incDataModOnly();
    return old;
  }

  /**
   * Return the number of items in this list
   *
   * @return The number of items in this list.
   */
  @Override
  public int size() {
    return size;
  }

  @Override
  public @NotNull Iterator<Content> iterator() {
    return new CLIterator();
  }

  @Override
  public @NotNull ListIterator<Content> listIterator() {
    return new CLListIterator(0);
  }

  @Override
  public @NotNull ListIterator<Content> listIterator(final int start) {
    return new CLListIterator(start);
  }

  /**
   * Return this list as a <code>String</code>
   *
   * @return The String representation of this list.
   */
  @Override
  public String toString() {
    return super.toString();
  }

  private void sortInPlace(final int[] indexes) {
    // the indexes are a discrete set of values that have no duplicates,
    // and describe the relative order of each of them.
    // as a result, we can do some tricks....
    final int[] unsorted = indexes.clone();
    Arrays.sort(unsorted);
    final Content[] usc = new Content[unsorted.length];
    for (int i = 0; i < usc.length; i++) {
      usc[i] = elementData[indexes[i]];
    }
    // usc contains the content in their pre-sorted order....
    for (int i = 0; i < indexes.length; i++) {
      elementData[unsorted[i]] = usc[i];
    }
  }

  /**
   * Unlike the Arrays.binarySearch, this method never expects an
   * "already exists" condition, we only ever add, thus there will never
   * be a negative insertion-point.
   *
   * @param indexes THe pointers to search within
   * @param len     The number of pointers to search within
   * @param val     The pointer we are checking for.
   * @param comp    The Comparator to compare with
   * @return the insertion point.
   */
  private int binarySearch(final int[] indexes, final int len,
                           final int val, final Comparator<? super Content> comp) {
    int left = 0, mid, right = len - 1, cmp;
    final Content base = elementData[val];
    while (left <= right) {
      mid = (left + right) >>> 1;
      cmp = comp.compare(base, elementData[indexes[mid]]);
      if (cmp == 0) {
        while (mid < right && comp.compare(
          base, elementData[indexes[mid + 1]]) == 0) {
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

  /**
   * Sorts this list using the supplied Comparator to compare elements.
   *
   * @param comp - the Comparator used to compare list elements. A null value indicates that the elements' natural ordering should be used
   * @since 2.0.6
   */
  @Override
  public void sort(Comparator<? super Content> comp) {

    if (comp == null) {
      // sort by the 'natural order', which, there is none.
      // options, throw exception, or let the current-order represent the natural order.
      // do nothing is the better alternative.
      return;
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

  /* * * * * * * * * * * * * ContentListIterator * * * * * * * * * * * * * * * */
  /* * * * * * * * * * * * * ContentListIterator * * * * * * * * * * * * * * * */

  /**
   * A fast implementation of Iterator.
   * <p>
   * It is fast because it is tailored to the ContentList, and not the
   * flexible implementation used by AbstractList. It needs to be fast because
   * iterator() is used extensively in the for-each type loop.
   *
   * @author Rolf Lear
   */
  private final class CLIterator implements Iterator<Content> {
    private int expect;
    private int cursor = 0;
    private boolean canremove = false;

    private CLIterator() {
      expect = getModCount();
    }

    @Override
    public boolean hasNext() {
      return cursor < size;
    }

    @Override
    public Content next() {
      if (getModCount() != expect) {
        throw new ConcurrentModificationException("ContentList was " +
                                                  "modified outside of this Iterator");
      }
      if (cursor >= size) {
        throw new NoSuchElementException("Iterated beyond the end of " +
                                         "the ContentList.");
      }
      canremove = true;
      return elementData[cursor++];
    }

    @Override
    public void remove() {
      if (getModCount() != expect) {
        throw new ConcurrentModificationException("ContentList was " +
                                                  "modified outside of this Iterator");
      }
      if (!canremove) {
        throw new IllegalStateException("Can only remove() content " +
                                        "after a call to next()");
      }
      canremove = false;
      ContentList.this.remove(--cursor);
      expect = getModCount();
    }
  }

  /* * * * * * * * * * * * * ContentListIterator * * * * * * * * * * * * * * * */
  /* * * * * * * * * * * * * ContentListIterator * * * * * * * * * * * * * * * */

  /**
   * A fast implementation of Iterator.
   * <p>
   * It is fast because it is tailored to the ContentList, and not the
   * flexible implementation used by AbstractList. It needs to be fast because
   * iterator() is used extensively in the for-each type loop.
   *
   * @author Rolf Lear
   */
  private final class CLListIterator implements ListIterator<Content> {
    /**
     * Whether this iterator is in forward or reverse.
     */
    private boolean forward;
    /**
     * Whether a call to remove() is valid
     */
    private boolean canremove = false;
    /**
     * Whether a call to set() is valid
     */
    private boolean canset = false;

    /**
     * Expected modCount in our backing list
     */
    private int expectedmod;

    private int cursor;

    /**
     * Default constructor
     *
     * @param start where in the FilterList to start iteration.
     */
    private CLListIterator(final int start) {
      expectedmod = getModCount();
      // always start list iterators in backward mode ....
      // it makes sense... really.
      forward = false;

      checkIndex(start, false);

      cursor = start;
    }

    private void checkConcurrent() {
      if (expectedmod != getModCount()) {
        throw new ConcurrentModificationException("The ContentList " +
                                                  "supporting this iterator has been modified by" +
                                                  "something other than this Iterator.");
      }
    }

    /**
     * Returns <code>true</code> if this list iterator has a next element.
     */
    @Override
    public boolean hasNext() {
      return (forward ? cursor + 1 : cursor) < size;
    }

    /**
     * Returns <code>true</code> if this list iterator has more elements
     * when traversing the list in the reverse direction.
     */
    @Override
    public boolean hasPrevious() {
      return (forward ? cursor : cursor - 1) >= 0;
    }

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to <code>next</code>.
     */
    @Override
    public int nextIndex() {
      return forward ? cursor + 1 : cursor;
    }

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to <code>previous</code>. (Returns -1 if the list
     * iterator is at the beginning of the list.)
     */
    @Override
    public int previousIndex() {
      return forward ? cursor : cursor - 1;
    }

    /**
     * Returns the next element in the list.
     */
    @Override
    public Content next() {
      checkConcurrent();
      final int next = forward ? cursor + 1 : cursor;

      if (next >= size) {
        throw new NoSuchElementException("next() is beyond the end of the Iterator");
      }

      cursor = next;
      forward = true;
      canremove = true;
      canset = true;
      return elementData[cursor];
    }

    /**
     * Returns the previous element in the list.
     */
    @Override
    public Content previous() {
      checkConcurrent();
      final int prev = forward ? cursor : cursor - 1;

      if (prev < 0) {
        throw new NoSuchElementException("previous() is beyond the beginning of the Iterator");
      }

      cursor = prev;
      forward = false;
      canremove = true;
      canset = true;
      return elementData[cursor];
    }

    /**
     * Inserts the specified element into the list .
     */
    @Override
    public void add(final Content obj) {
      checkConcurrent();
      // always add before what would normally be returned by next();
      final int next = forward ? cursor + 1 : cursor;

      ContentList.this.add(next, obj);

      expectedmod = getModCount();

      canremove = canset = false;

      // a call to next() should be unaffected, so, whatever was going to
      // be next will still be next, remember, what was going to be next
      // has been shifted 'right' by our insert.
      // we ensure this by setting the cursor to next(), and making it
      // forward
      cursor = next;
      forward = true;
    }

    /**
     * Removes from the list the last element that was returned by the last
     * call to <code>next</code> or <code>previous</code>.
     */
    @Override
    public void remove() {
      checkConcurrent();
      if (!canremove) {
        throw new IllegalStateException("Can not remove an "
                                        + "element unless either next() or previous() has been called "
                                        + "since the last remove()");
      }
      // we are removing the last entry returned by either next() or
      // previous().
      // the idea is to remove it, and pretend that we used to be at the
      // entry that happened *after* the removed entry.
      // so, get what would be the next entry (set at tmpcursor).
      // so call nextIndex to set tmpcursor to what would come after.
      ContentList.this.remove(cursor);
      forward = false;
      expectedmod = getModCount();

      canremove = false;
      canset = false;
    }

    /**
     * Replaces the last element returned by <code>next</code> or
     * <code>previous</code> with the specified element.
     */
    @Override
    public void set(final Content obj) {
      checkConcurrent();
      if (!canset) {
        throw new IllegalStateException("Can not set an element "
                                        + "unless either next() or previous() has been called since the "
                                        + "last remove() or set()");
      }

      ContentList.this.set(cursor, obj);
      expectedmod = getModCount();
    }
  }

  /* * * * * * * * * * * * * FilterList * * * * * * * * * * * * * * * */
  /* * * * * * * * * * * * * FilterList * * * * * * * * * * * * * * * */

  /**
   * <code>FilterList</code> represents legal JDOM content, including content
   * for <code>Document</code>s or <code>Element</code>s.
   * <p>
   * FilterList represents a dynamic view of the backing ContentList, changes
   * to the backing list are reflected in the FilterList, and visa-versa.
   *
   * @param <F> The Generic type of content accepted by the underlying Filter.
   */

  final class FilterList<F extends Content> extends AbstractList<F> {
    // The filter to apply
    final Filter<F> filter;
    // correlate the position in the filtered list to the index in the
    // backing ContentList.
    int[] backingpos = new int[size + INITIAL_ARRAY_SIZE];
    int backingsize = 0;
    // track data modifications in the backing ContentList.
    int xdata = -1;

    /**
     * Create a new instance of the FilterList with the specified Filter.
     *
     * @param filter The underlying Filter to use for filtering the content.
     */
    FilterList(final Filter<F> filter) {
      this.filter = filter;
    }

    /**
     * Returns true if there is no content in this FilterList.
     *
     * @return true if there is no content in this FilterList
     */
    @Override
    public boolean isEmpty() {
      // More efficient implementation than default size() == 0
      // we use resync() to accomplish the task. If there is an
      // element 0 in this FilterList, then it is not empty!
      // we may already have resync'd 0, which will be a fast return then,
      // or, if we have not resync'd 0, then we only have to filter up to
      // the first matching element to get a result (or the whole list
      // if isEmpty() is true).
      return resync(0) == size;
    }

    /**
     * Synchronise our view to the backing list. Only synchronise the first
     * <code>index</code> view elements. For want of a better word, we'll
     * call this a 'Lazy' implementation.
     *
     * @param index how much we want to sync. Set to -1 to synchronise everything.
     * @return the index in the backing array of the <i>index'th</i> match.
     * or the backing data size if there is no match for the index.
     */
    private int resync(final int index) {
      if (xdata != getDataModCount()) {
        // The underlying list was modified somehow...
        // we need to invalidate our research...
        xdata = getDataModCount();
        backingsize = 0;
        if (size >= backingpos.length) {
          backingpos = new int[size + 1];
        }
      }

      if (index >= 0 && index < backingsize) {
        // we have already indexed far enough...
        // return the backing index.
        return backingpos[index];
      }

      // the index in the backing list of the next value to check.
      int bpi = 0;
      if (backingsize > 0) {
        bpi = backingpos[backingsize - 1] + 1;
      }

      while (bpi < size) {
        final F gotit = filter.filter(elementData[bpi]);
        if (gotit != null) {
          backingpos[backingsize] = bpi;
          if (backingsize++ == index) {
            return bpi;
          }
        }
        bpi++;
      }
      return size;
    }

    /**
     * Inserts the specified object at the specified position in this list.
     * Shifts the object currently at that position (if any) and any
     * subsequent objects to the right (adds one to their indices).
     *
     * @param index The location to set the value to.
     * @param obj   The object to insert into the list. throws
     *              IndexOutOfBoundsException if index < 0 || index > size()
     */
    @Override
    public void add(final int index, final Content obj) {
      if (index < 0) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      int adj = resync(index);
      if (adj == size && index > size()) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      if (filter.matches(obj)) {
        ContentList.this.add(adj, obj);

        // we can optimise the laziness now by doing a partial reset on
        // the backing list... invalidate everything *after* the added
        // content
        if (backingpos.length <= size) {
          backingpos = Arrays.copyOf(backingpos, backingpos.length + 1);
        }
        backingpos[index] = adj;
        backingsize = index + 1;
        xdata = getDataModCount();
      }
      else {
        throw new IllegalAddException("Filter won't allow the " +
                                      obj.getClass().getName() +
                                      " '" + obj + "' to be added to the list");
      }
    }

    @Override
    public boolean addAll(final int index,
                          final Collection<? extends F> collection) {
      if (collection == null) {
        throw new NullPointerException("Cannot add a null collection");
      }

      if (index < 0) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }

      final int adj = resync(index);
      if (adj == size && index > size()) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }

      final int addcnt = collection.size();
      if (addcnt == 0) {
        return false;
      }

      ContentList.this.ensureCapacity(ContentList.this.size() + addcnt);

      final int tmpmodcount = getModCount();
      final int tmpdmc = getDataModCount();
      boolean ok = false;

      int count = 0;

      try {
        for (Content c : collection) {
          if (c == null) {
            throw new NullPointerException(
              "Cannot add null content");
          }
          if (filter.matches(c)) {
            ContentList.this.add(adj + count, c);
            // we can optimise the laziness now by doing a partial
            // reset on
            // the backing list... invalidate everything *after* the
            // added
            // content
            if (backingpos.length <= size) {
              backingpos = Arrays.copyOf(backingpos, backingpos.length + addcnt);
            }
            backingpos[index + count] = adj + count;
            backingsize = index + count + 1;
            xdata = getDataModCount();

            count++;
          }
          else {
            throw new IllegalAddException("Filter won't allow the " +
                                          c.getClass().getName() +
                                          " '" + c + "' to be added to the list");
          }
        }
        ok = true;
      }
      finally {
        if (!ok) {
          // something failed... remove all added content
          while (--count >= 0) {
            ContentList.this.remove(adj + count);
          }
          // restore the mod-counts.
          setModCount(tmpmodcount, tmpdmc);
          // reset the cache... will need to redo some work on another
          // call maybe....
          backingsize = index;
          xdata = tmpmodcount;
        }
      }

      return true;
    }

    /**
     * Return the object at the specified offset.
     *
     * @param index The offset of the object.
     * @return The Object which was returned.
     */
    @Override
    public F get(final int index) {
      if (index < 0) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      final int adj = resync(index);
      if (adj == size) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      return filter.filter(ContentList.this.get(adj));
    }

    @Override
    public @NotNull Iterator<F> iterator() {
      return new FilterListIterator<>(this, 0);
    }

    @Override
    public @NotNull ListIterator<F> listIterator() {
      return new FilterListIterator<>(this, 0);
    }

    @Override
    public @NotNull ListIterator<F> listIterator(final int index) {
      return new FilterListIterator<>(this, index);
    }

    /**
     * Remove the object at the specified offset.
     *
     * @param index The offset of the object.
     * @return The Object which was removed.
     */
    @Override
    public F remove(final int index) {
      if (index < 0) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      final int adj = resync(index);
      if (adj == size) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      final Content oldc = ContentList.this.remove(adj);
      // optimise the backing cache.
      backingsize = index;
      xdata = getDataModCount();
      // use Filter to ensure the cast is right.
      return filter.filter(oldc);
    }

    /**
     * Set the object at the specified location to the supplied object.
     *
     * @param index The location to set the value to.
     * @param obj   The location to set the value to.
     * @return The object which was replaced. throws
     * IndexOutOfBoundsException if index < 0 || index >= size()
     */
    @Override
    public F set(final int index, final F obj) {
      if (index < 0) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      final int adj = resync(index);
      if (adj == size) {
        throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
      }
      final F ins = filter.filter(obj);
      if (ins != null) {
        final F oldc = filter.filter(ContentList.this.set(adj, ins));
        // optimize the backing....
        xdata = getDataModCount();
        return oldc;
      }
      throw new IllegalAddException("Filter won't allow index " +
                                    index + " to be set to " +
                                    (obj.getClass()).getName());
    }

    /**
     * Return the number of items in this list
     *
     * @return The number of items in this list.
     */
    @Override
    public int size() {
      resync(-1);
      return backingsize;
    }

    /**
     * Unlike the Arrays.binarySearch, this method never expects an
     * "already exists" condition, we only ever add, thus there will never
     * be a negative insertion-point.
     *
     * @param indexes THe pointers to search within
     * @param len     The number of pointers to search within
     * @param val     The pointer we are checking for.
     * @param comp    The Comparator to compare with
     * @return the insertion point.
     */
    @SuppressWarnings("unchecked")
    private int fbinarySearch(final int[] indexes, final int len,
                              final int val, final Comparator<? super F> comp) {
      int left = 0, mid, right = len - 1, cmp;
      final F base = (F)elementData[backingpos[val]];
      while (left <= right) {
        mid = (left + right) >>> 1;
        cmp = comp.compare(base, (F)elementData[indexes[mid]]);
        if (cmp == 0) {
          while (mid < right && comp.compare(
            base, (F)elementData[indexes[mid + 1]]) == 0) {
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


    /**
     * Sorts this list using the supplied Comparator to compare elements.
     *
     * @param comp - the Comparator used to compare list elements. A null value indicates that the elements' natural ordering should be used
     * @since 2.0.6
     */
    //Not till Java8 @Override
    @Override
    public void sort(final Comparator<? super F> comp) {
      // this size() forces a full scan/update of the list.
      if (comp == null) {
        // sort by the 'natural order', which, there is none.
        // options, throw exception, or let the current-order represent the natural order.
        // do nothing is the better alternative.
        return;
      }
      final int sz = size();
      final int[] indexes = new int[sz];
      for (int i = 0; i < sz; i++) {
        final int ip = fbinarySearch(indexes, i, i, comp);
        if (ip < i) {
          System.arraycopy(indexes, ip, indexes, ip + 1, i - ip);
        }
        indexes[ip] = backingpos[i];
      }
      sortInPlace(indexes);
    }
  }

  /* * * * * * * * * * * * * FilterListIterator * * * * * * * * * * * */
  /* * * * * * * * * * * * * FilterListIterator * * * * * * * * * * * */

  final class FilterListIterator<F extends Content> implements ListIterator<F> {

    /**
     * The Filter that applies
     */
    private final FilterList<F> filterlist;

    /**
     * Whether this iterator is in forward or reverse.
     */
    private boolean forward;
    /**
     * Whether a call to remove() is valid
     */
    private boolean canremove = false;
    /**
     * Whether a call to set() is valid
     */
    private boolean canset = false;

    /**
     * Expected modCount in our backing list
     */
    private int expectedmod;

    private int cursor;

    /**
     * Default constructor
     *
     * @param flist The FilterList over which we will iterate.
     * @param start where in the FilterList to start iteration.
     */
    FilterListIterator(final FilterList<F> flist, final int start) {
      filterlist = flist;
      expectedmod = getModCount();
      // always start list iterators in backward mode ....
      // it makes sense... really.
      forward = false;

      if (start < 0) {
        throw new IndexOutOfBoundsException("Index: " + start + " Size: " + filterlist.size());
      }

      final int adj = filterlist.resync(start);

      if (adj == size && start > filterlist.size()) {
        // the start point is after the end of the list.
        // it is only allowed to be the same as size(), no larger.
        throw new IndexOutOfBoundsException("Index: " + start + " Size: " + filterlist.size());
      }

      cursor = start;
    }

    private void checkConcurrent() {
      if (expectedmod != getModCount()) {
        throw new ConcurrentModificationException("The ContentList " +
                                                  "supporting the FilterList this iterator is " +
                                                  "processing has been modified by something other " +
                                                  "than this Iterator.");
      }
    }

    /**
     * Returns <code>true</code> if this list iterator has a next element.
     */
    @Override
    public boolean hasNext() {
      return filterlist.resync(forward ? cursor + 1 : cursor) < size;
    }

    /**
     * Returns <code>true</code> if this list iterator has more elements
     * when traversing the list in the reverse direction.
     */
    @Override
    public boolean hasPrevious() {
      return (forward ? cursor : cursor - 1) >= 0;
    }

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to <code>next</code>.
     */
    @Override
    public int nextIndex() {
      return forward ? cursor + 1 : cursor;
    }

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to <code>previous</code>. (Returns -1 if the list
     * iterator is at the beginning of the list.)
     */
    @Override
    public int previousIndex() {
      return forward ? cursor : cursor - 1;
    }

    /**
     * Returns the next element in the list.
     */
    @Override
    public F next() {
      checkConcurrent();
      final int next = forward ? cursor + 1 : cursor;

      if (filterlist.resync(next) >= size) {
        throw new NoSuchElementException("next() is beyond the end of the Iterator");
      }

      cursor = next;
      forward = true;
      canremove = true;
      canset = true;
      return filterlist.get(cursor);
    }

    /**
     * Returns the previous element in the list.
     */
    @Override
    public F previous() {
      checkConcurrent();
      final int prev = forward ? cursor : cursor - 1;

      if (prev < 0) {
        throw new NoSuchElementException("previous() is beyond the beginning of the Iterator");
      }

      cursor = prev;
      forward = false;
      canremove = true;
      canset = true;
      return filterlist.get(cursor);
    }

    /**
     * Inserts the specified element into the list .
     */
    @Override
    public void add(final Content obj) {
      checkConcurrent();
      // always add before what would normally be returned by next();
      final int next = forward ? cursor + 1 : cursor;

      filterlist.add(next, obj);

      expectedmod = getModCount();

      canremove = canset = false;

      // a call to next() should be unaffected, so, whatever was going to
      // be next will still be next, remember, what was going to be next
      // has been shifted 'right' by our insert.
      // we ensure this by setting the cursor to next(), and making it
      // forward
      cursor = next;
      forward = true;
    }

    /**
     * Removes from the list the last element that was returned by the last
     * call to <code>next</code> or <code>previous</code>.
     */
    @Override
    public void remove() {
      checkConcurrent();
      if (!canremove) {
        throw new IllegalStateException("Can not remove an "
                                        + "element unless either next() or previous() has been called "
                                        + "since the last remove()");
      }
      // we are removing the last entry returned by either next() or
      // previous().
      // the idea is to remove it, and pretend that we used to be at the
      // entry that happened *after* the removed entry.
      // so, get what would be the next entry (set at tmpcursor).
      // so call nextIndex to set tmpcursor to what would come after.
      filterlist.remove(cursor);
      forward = false;
      expectedmod = getModCount();

      canremove = false;
      canset = false;
    }

    /**
     * Replaces the last element returned by <code>next</code> or
     * <code>previous</code> with the specified element.
     */
    @Override
    public void set(final F obj) {
      checkConcurrent();
      if (!canset) {
        throw new IllegalStateException("Can not set an element "
                                        + "unless either next() or previous() has been called since the "
                                        + "last remove() or set()");
      }

      filterlist.set(cursor, obj);
      expectedmod = getModCount();
    }
  }
}
