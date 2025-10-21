// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * This class is a
 * <ul>
 * <li> lock-free (CAS instead of ReentrantLock)
 * <li> less-memory (no lock field)
 * <li> less-garbage (does not create Object[0] arrays)
 * <li> non-cloneable, non-serializable, no-subList-method
 * </ul>
 * re-implementation of {@link java.util.concurrent.CopyOnWriteArrayList}.
 * It generally is faster than {@link java.util.concurrent.CopyOnWriteArrayList} in case of low write-contention.
 * (Note that it is not advisable to use {@link java.util.concurrent.CopyOnWriteArrayList}
 *  in high write-contention code anyway, consider using {@link java.util.concurrent.ConcurrentHashMap} instead).
 */
final class LockFreeCopyOnWriteArrayList<E> implements List<E>, RandomAccess, ConcurrentList<E> {
  @SuppressWarnings({"FieldMayBeFinal"})
  private volatile Object @NotNull [] array;
  private static final VarHandleWrapper ARRAY_HANDLE = VarHandleWrapper.getFactory().create(LockFreeCopyOnWriteArrayList.class, "array", Object[].class);
  LockFreeCopyOnWriteArrayList() {
    array = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }
  LockFreeCopyOnWriteArrayList(@NotNull Collection<? extends E> c) {
    array = c.isEmpty() ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : c.toArray();
  }

  private boolean replaceArray(Object @NotNull [] oldArray, Object @NotNull [] newArray) {
    return ARRAY_HANDLE.compareAndSet(this, oldArray, newArray);
  }

  /**
   * Returns the number of elements in this list.
   *
   * @return the number of elements in this list
   */
  @Override
  public int size() {
    return get().length;
  }

  /**
   * @return <tt>true</tt> if this list contains no elements
   */
  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  /**
   * static version of indexOf, to allow repeated calls without
   * needing to re-acquire array each time.
   *
   * @param o        element to search for
   * @param elements the array
   * @param index    first index to search
   * @param fence    one past last index to search
   * @return index of element, or -1 if absent
   */
  private static int indexOf(Object o, Object @NotNull [] elements, int index, int fence) {
    return ArrayUtilRt.indexOf(elements, o, index, fence);
  }

  /**
   * static version of lastIndexOf.
   *
   * @param o        element to search for
   * @param elements the array
   * @param index    first index to search
   * @return index of element, or -1 if absent
   */
  private static int lastIndexOf(Object o, Object @NotNull [] elements, int index) {
    if (o == null) {
      for (int i = index; i >= 0; i--) {
        if (elements[i] == null) {
          return i;
        }
      }
    }
    else {
      for (int i = index; i >= 0; i--) {
        if (o.equals(elements[i])) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Returns <tt>true</tt> if this list contains the specified element.
   * More formally, returns <tt>true</tt> if and only if this list contains
   * at least one element <tt>e</tt> such that
   * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
   *
   * @param o element whose presence in this list is to be tested
   * @return <tt>true</tt> if this list contains the specified element
   */
  @Override
  public boolean contains(Object o) {
    Object[] elements = get();
    return indexOf(o, elements, 0, elements.length) >= 0;
  }

  private Object[] get() {
    return array;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int indexOf(Object o) {
    Object[] elements = get();
    return indexOf(o, elements, 0, elements.length);
  }

  /**
   * Returns the index of the first occurrence of the specified element in
   * this list, searching forwards from <tt>index</tt>, or returns -1 if
   * the element is not found.
   * More formally, returns the lowest index <tt>i</tt> such that
   * <tt>(i&nbsp;&gt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(e==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;e.equals(get(i))))</tt>,
   * or -1 if there is no such index.
   *
   * @param e     element to search for
   * @param index index to start searching from
   * @return the index of the first occurrence of the element in
   *         this list at position <tt>index</tt> or later in the list;
   *         <tt>-1</tt> if the element is not found.
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public int indexOf(E e, int index) {
    Object[] elements = get();
    return indexOf(e, elements, index, elements.length);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int lastIndexOf(Object o) {
    Object[] elements = get();
    return lastIndexOf(o, elements, elements.length - 1);
  }

  /**
   * Returns the index of the last occurrence of the specified element in
   * this list, searching backwards from <tt>index</tt>, or returns -1 if
   * the element is not found.
   * More formally, returns the highest index <tt>i</tt> such that
   * <tt>(i&nbsp;&lt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(e==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;e.equals(get(i))))</tt>,
   * or -1 if there is no such index.
   *
   * @param e     element to search for
   * @param index index to start searching backwards from
   * @return the index of the last occurrence of the element at position
   *         less than or equal to <tt>index</tt> in this list;
   *         -1 if the element is not found.
   * @throws IndexOutOfBoundsException if the specified index is greater
   *                                   than or equal to the current size of this list
   */
  public int lastIndexOf(E e, int index) {
    Object[] elements = get();
    return lastIndexOf(e, elements, index);
  }

  /**
   * Returns an array containing all the elements in this list
   * in proper sequence (from first to last element).
   * <p/>
   * <p>The returned array will be "safe" in that no references to it are
   * maintained by this list.  (In other words, this method must allocate
   * a new array).  The caller is thus free to modify the returned array.
   * <p/>
   * <p>This method acts as bridge between array-based and collection-based
   * APIs.
   *
   * @return an array containing all the elements in this list
   */
  @Override
  public Object @NotNull [] toArray() {
    Object[] elements = get();
    if (elements.length == 0) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

    return Arrays.copyOf(elements, elements.length, Object[].class);
  }

  /**
   * Returns an array containing all the elements in this list in
   * proper sequence (from first to last element); the runtime type of
   * the returned array is that of the specified array.  If the list fits
   * in the specified array, it is returned therein.  Otherwise, a new
   * array is allocated with the runtime type of the specified array and
   * the size of this list.
   * <p/>
   * <p>If this list fits in the specified array with room to spare
   * (i.e., the array has more elements than this list), the element in
   * the array immediately following the end of the list is set to
   * <tt>null</tt>.  (This is useful in determining the length of this
   * list <i>only</i> if the caller knows that this list does not contain
   * any null elements.)
   * <p/>
   * <p>Like the {@link #toArray()} method, this method acts as bridge between
   * array-based and collection-based APIs.  Further, this method allows
   * precise control over the runtime type of the output array, and may,
   * under certain circumstances, be used to save allocation costs.
   * <p/>
   * <p>Suppose <tt>x</tt> is a list known to contain only strings.
   * The following code can be used to dump the list into a newly
   * allocated array of <tt>String</tt>:
   * <p/>
   * <pre>
   *     String[] y = x.toArray(new String[0]);</pre>
   *
   * Note that <tt>toArray(new Object[0])</tt> is identical in function to
   * <tt>toArray()</tt>.
   *
   * @param a the array into which the elements of the list are to
   *          be stored, if it is big enough; otherwise, a new array of the
   *          same runtime type is allocated for this purpose.
   * @return an array containing all the elements in this list
   * @throws ArrayStoreException  if the runtime type of the specified array
   *                              is not a supertype of the runtime type of every element in
   *                              this list
   * @throws NullPointerException if the specified array is null
   */
  @Override
  public <T> T @NotNull [] toArray(T @NotNull [] a) {
    Object[] elements = get();
    int len = elements.length;
    if (a.length < len) {
      //noinspection unchecked
      return (T[])Arrays.copyOf(elements, len, a.getClass());
    }
    //noinspection SuspiciousSystemArraycopy
    System.arraycopy(elements, 0, a, 0, len);
    if (a.length > len) {
      a[len] = null;
    }
    return a;
  }

  // Positional Access Operations

  private E get(Object @NotNull [] a, int index) {
    //noinspection unchecked
    return (E)a[index];
  }

  /**
   * {@inheritDoc}
   *
   * @throws IndexOutOfBoundsException {@inheritDoc}
   */
  @Override
  public E get(int index) {
    return get(get(), index);
  }

  /**
   * Replaces the element at the specified position in this list with the
   * specified element.
   *
   */
  @Override
  public E set(int index, E element) throws IndexOutOfBoundsException {
    E oldValue;
    Object[] elements;
    Object[] newElements;
    do {
      elements = get();
      oldValue = get(elements, index);

      if (oldValue == element) {
        // Not quite a no-op; ensures volatile write semantics
        newElements = elements;
      }
      else {
        newElements = createArraySet(elements, index, element);
      }
    } while(!replaceArray(elements, newElements));
    return oldValue;
  }

  private static Object @NotNull [] createArraySet(Object @NotNull [] elements, int index, Object element) {
    int len = elements.length;
    Object[] newElements = Arrays.copyOf(elements, len, Object[].class);
    newElements[index] = element;
    return newElements;
  }

  /**
   * Appends the specified element to the end of this list.
   *
   * @param e element to be appended to this list
   * @return <tt>true</tt> (as specified by {@link Collection#add})
   */
  @Override
  public boolean add(E e) {
    return changeAndReplace(elements -> createArrayAdd(elements, e));
  }

  private static <E> Object @NotNull [] createArrayAdd(Object @NotNull [] elements, E e) {
    int len = elements.length;
    Object[] newElements = Arrays.copyOf(elements, len + 1);
    newElements[len] = e;
    return newElements;
  }

  /**
   * Inserts the specified element at the specified position in this
   * list. Shifts the element currently at that position (if any) and
   * any subsequent elements to the right (adds one to their indices).
   *
   */
  @Override
  public void add(int index, E element) throws IndexOutOfBoundsException {
    changeAndReplace(elements -> createArrayAdd(elements, index, element));
  }

  private static <E> Object @NotNull [] createArrayAdd(Object @NotNull [] elements, int index, E element) {
    int len = elements.length;
    if (index > len || index < 0) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + len);
    }
    Object[] newElements = new Object[len + 1];
    if (index != 0) {
      System.arraycopy(elements, 0, newElements, 0, index);
    }
    int numMoved = len - index;
    if (numMoved != 0) {
      System.arraycopy(elements, index, newElements, index + 1, numMoved);
    }
    newElements[index] = element;
    return newElements;
  }

  /**
   * Removes the element at the specified position in this list.
   * Shifts any subsequent elements to the left (subtracts one from their
   * indices).  Returns the element that was removed from the list.
   *
   */
  @Override
  public E remove(int index) throws IndexOutOfBoundsException {
    E oldValue;
    while (true) {
      Object[] elements = get();
      Object[] newElements = createArrayRemove(elements, index);
      if (replaceArray(elements, newElements)) {
        oldValue = get(elements, index);
        break;
      }
    }
    return oldValue;
  }

  private static Object @NotNull [] createArrayRemove(Object @NotNull [] elements, int index) {
    int len = elements.length;
    Object[] newElements = len == 1 ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : new Object[len - 1];
    if (index != 0) {
      System.arraycopy(elements, 0, newElements, 0, index);
    }
    int numMoved = len - index - 1;
    if (numMoved != 0) {
      System.arraycopy(elements, index + 1, newElements, index, numMoved);
    }
    return newElements;
  }

  /**
   * Removes the first occurrence of the specified element from this list,
   * if it is present.  If this list does not contain the element, it is
   * unchanged.  More formally, removes the element with the lowest index
   * <tt>i</tt> such that
   * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
   * (if such an element exists).  Returns <tt>true</tt> if this list
   * contained the specified element (or equivalently, if this list
   * changed as a result of the call).
   *
   * @param o element to be removed from this list, if present
   * @return <tt>true</tt> if this list contained the specified element
   */
  @Override
  public boolean remove(Object o) {
    return changeAndReplace(elements -> createArrayRemove(elements, o));
  }

  // null means not found
  private static Object @Nullable [] createArrayRemove(Object @NotNull [] elements, Object o) {
    int len = elements.length;
    if (len == 0) {
      return null;
    }
    // Copy while searching for element to remove
    // This wins in the normal case of element being present
    int newLen = len - 1;
    Object[] newElements = newLen == 0 ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : new Object[newLen];

    int i;
    for (i = newLen; i != 0; --i) {
      Object element = elements[i];
      if (Objects.equals(o, element)) {
        // found one;  copy remaining and exit
        System.arraycopy(elements, 0, newElements, 0, i);
        break;
      }
      newElements[i-1] = element;
    }

    // special handling for last cell
    if (i == 0 && !Objects.equals(o, elements[0])) {
      return null;
    }
    return newElements;
  }

  /**
   * Append the element if not present.
   *
   * @param e element to be added to this list, if absent
   * @return <tt>true</tt> if the element was added
   */
  @Override
  public boolean addIfAbsent(E e) {
    return changeAndReplace(elements -> createArrayAddIfAbsent(elements, e));
  }

  private static <E> Object @Nullable [] createArrayAddIfAbsent(Object @NotNull [] elements, E e) {
    // Copy while checking if already present.
    // This wins in the most common case where it is not present
    int len = elements.length;
    Object[] newElements = new Object[len + 1];
    for (int i = 0; i < len; ++i) {
      if (Objects.equals(e, elements[i])) {
        // exit, throwing away copy
        return null;
      }
      newElements[i] = elements[i];
    }
    newElements[len] = e;
    return newElements;
  }

  /**
   * Returns <tt>true</tt> if this list contains all the elements of the
   * specified collection.
   *
   * @param c collection to be checked for containment in this list
   * @return <tt>true</tt> if this list contains all the elements of the
   *         specified collection
   * @throws NullPointerException if the specified collection is null
   * @see #contains(Object)
   */
  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    Object[] elements = get();
    int len = elements.length;
    for (Object e : c) {
      if (indexOf(e, elements, 0, len) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Removes from this list all of its elements that are contained in
   * the specified collection. This is a particularly expensive operation
   * in this class because of the need for an internal temporary array.
   *
   * @param c collection containing elements to be removed from this list
   * @return <tt>true</tt> if this list changed as a result of the call
   * @throws ClassCastException   if the class of an element of this list
   *                              is incompatible with the specified collection
   *                              (<a href="../Collection.html#optional-restrictions">optional</a>)
   * @throws NullPointerException if this list contains a null element and the
   *                              specified collection does not permit null elements
   *                              (<a href="../Collection.html#optional-restrictions">optional</a>),
   *                              or if the specified collection is null
   * @see #remove(Object)
   */
  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    return changeAndReplace(elements -> createArrayRemoveAll(elements, c));
  }

  // null means not found
  private static Object @Nullable [] createArrayRemoveAll(Object @NotNull [] elements, @NotNull Collection<?> c) {
    int len = elements.length;
    if (len == 0) {
      return null;
    }
    // temp array holds those elements we know we want to keep
    int newLen = 0;
    Object[] temp = new Object[len];
    for (Object element : elements) {
      if (!c.contains(element)) {
        temp[newLen++] = element;
      }
    }
    if (newLen == len) {
      return null;
    }
    return Arrays.copyOf(temp, newLen, Object[].class);
  }

  /**
   * Retains only the elements in this list that are contained in the
   * specified collection.  In other words, removes from this list all of
   * its elements that are not contained in the specified collection.
   *
   * @param c collection containing elements to be retained in this list
   * @return <tt>true</tt> if this list changed as a result of the call
   * @throws ClassCastException   if the class of an element of this list
   *                              is incompatible with the specified collection
   *                              (<a href="../Collection.html#optional-restrictions">optional</a>)
   * @throws NullPointerException if this list contains a null element and the
   *                              specified collection does not permit null elements
   *                              (<a href="../Collection.html#optional-restrictions">optional</a>),
   *                              or if the specified collection is null
   * @see #remove(Object)
   */
  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    return changeAndReplace(elements -> createArrayRetainAll(elements, c));
  }

  private static Object @Nullable [] createArrayRetainAll(Object @NotNull [] elements, @NotNull Collection<?> c) {
    int len = elements.length;
    if (len == 0) {
      return null;
    }
    // temp array holds those elements we know we want to keep
    int newLength = 0;
    Object[] temp = new Object[len];
    for (Object element : elements) {
      if (c.contains(element)) {
        temp[newLength++] = element;
      }
    }
    if (newLength == len) {
      return null;
    }
    return Arrays.copyOf(temp, newLength, Object[].class);
  }

  /**
   * Appends all the elements in the specified collection that
   * are not already contained in this list, to the end of
   * this list, in the order that they are returned by the
   * specified collection's iterator.
   *
   * @param c collection containing elements to be added to this list
   * @return the number of elements added
   * @throws NullPointerException if the specified collection is null
   * @see #addIfAbsent(Object)
   */
  @Override
  public int addAllAbsent(@NotNull Collection<? extends E> c) {
    // optimization: faster than calling .add() one by one
    if (c.isEmpty()) {
      return 0;
    }

    Object[] elements;
    Object[] newElements;
    int added;
    do {
      elements = get();
      Set<Object> existing = ContainerUtil.map2Set(elements, Functions.identity());
      List<Object> toAddList = new ArrayList<>(c.size());
      for (E e : c) {
        if (!existing.contains(e)) toAddList.add(e);
      }

      added = toAddList.size();
      if (added == 0) {
        return 0;
      }

      newElements = ArrayUtil.mergeArrays(elements, toAddList.toArray());
    }
    while (!replaceArray(elements, newElements));
    return added;
  }

  /**
   * Removes all the elements from this list.
   * The list will be empty after this call returns.
   */
  @Override
  public void clear() {
    array = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  /**
   * Appends all the elements in the specified collection to the end
   * of this list, in the order that they are returned by the specified
   * collection's iterator.
   *
   * @param c collection containing elements to be added to this list
   * @return <tt>true</tt> if this list changed as a result of the call
   * @throws NullPointerException if the specified collection is null
   * @see #add(Object)
   */
  @Override
  public boolean addAll(@NotNull Collection<? extends E> c) {
    changeAndReplace(elements -> createArrayAddAll(elements, c));
    return true;
  }

  private static Object @Nullable [] createArrayAddAll(Object @NotNull [] elements, @SuppressWarnings("rawtypes") @NotNull Collection c) {
    if (c.isEmpty()) {
      return null;
    }
    Object[] cs = c.toArray();
    //could still be empty for concurrent collection
    //noinspection ConstantConditions
    if (cs.length == 0) {
      return null;
    }
    int len = elements.length;
    Object[] newElements = Arrays.copyOf(elements, len + cs.length, Object[].class);
    System.arraycopy(cs, 0, newElements, len, cs.length);
    return newElements;
  }
  /**
   * Inserts all the elements in the specified collection into this
   * list, starting at the specified position.  Shifts the element
   * currently at that position (if any) and any subsequent elements to
   * the right (increases their indices).  The new elements will appear
   * in this list in the order that they are returned by the
   * specified collection's iterator.
   *
   * @param index index at which to insert the first element
   *              from the specified collection
   * @param c     collection containing elements to be added to this list
   * @return <tt>true</tt> if this list changed as a result of the call
   * @throws NullPointerException      if the specified collection is null
   * @see #add(int, Object)
   */
  @Override
  public boolean addAll(int index, @NotNull Collection<? extends E> c) throws IndexOutOfBoundsException {
    return changeAndReplace(elements -> createArrayAddAll(elements, index, c));
  }

  private static Object @Nullable [] createArrayAddAll(Object @NotNull [] elements, int index, @SuppressWarnings("rawtypes") @NotNull Collection c) {
    if (c.isEmpty()) return null;
    Object[] cs = c.toArray();
    //noinspection ConstantValue
    if (cs.length == 0) {
      return null;
    }
    int len = elements.length;
    if (index > len || index < 0) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + len);
    }
    int numMoved = len - index;
    Object[] newElements;
    if (numMoved == 0) {
      newElements = Arrays.copyOf(elements, len + cs.length, Object[].class);
    }
    else {
      newElements = new Object[len + cs.length];
      System.arraycopy(elements, 0, newElements, 0, index);
      System.arraycopy(elements, index, newElements, index + cs.length, numMoved);
    }
    System.arraycopy(cs, 0, newElements, index, cs.length);
    return newElements;
  }

  /**
   * Returns a string representation of this list.  The string
   * representation consists of the string representations of the list's
   * elements in the order they are returned by its iterator, enclosed in
   * square brackets (<tt>"[]"</tt>).  Adjacent elements are separated by
   * the characters <tt>", "</tt> (comma and space).  Elements are
   * converted to strings as by {@link String#valueOf(Object)}.
   *
   * @return a string representation of this list
   */
  @Override
  public @NotNull String toString() {
    return Arrays.toString(get());
  }

  /**
   * Compares the specified object with this list for equality.
   * Returns {@code true} if the specified object is the same object
   * as this object, or if it is also a {@link List} and the sequence
   * of elements returned by an {@linkplain List#iterator() iterator}
   * over the specified list is the same as the sequence returned by
   * an iterator over this list.  The two sequences are considered to
   * be the same if they have the same length and corresponding
   * elements at the same position in the sequence are <em>equal</em>.
   * Two elements {@code e1} and {@code e2} are considered
   * <em>equal</em> if {@code (e1 == null ? e2 == null : e1.equals(e2))}.
   *
   * @param o the object to be compared for equality with this list
   * @return {@code true} if the specified object is equal to this list
   */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof List)) {
      return false;
    }

    List<?> list = (List<?>)o;
    Iterator<?> it = list.iterator();
    for (Object element : get()) {
      if (!it.hasNext() || !Objects.equals(element, it.next())) {
        return false;
      }
    }
    return !it.hasNext();
  }

  /**
   * Returns the hash code value for this list.
   * <p/>
   * <p>This implementation uses the definition in {@link List#hashCode}.
   *
   * @return the hash code value for this list
   */
  @Override
  public int hashCode() {
    int hashCode = 1;
    for (Object obj : get()) {
      hashCode = 31 * hashCode + (obj == null ? 0 : obj.hashCode());
    }
    return hashCode;
  }

  /**
   * Returns an iterator over the elements in this list in proper sequence.
   * <p/>
   * <p>The returned iterator provides a snapshot of the state of the list
   * when the iterator was constructed. No synchronization is needed while
   * traversing the iterator. The iterator does <em>NOT</em> support the
   * <tt>remove</tt> method.
   *
   * @return an iterator over the elements in this list in proper sequence
   */
  @Override
  public @NotNull Iterator<E> iterator() {
    Object[] elements = get();
    return elements.length == 0 ? Collections.emptyIterator() : new COWIterator(elements, 0);
  }

  @Override
  public void forEach(@NotNull Consumer<? super E> action) {
    Object[] snapshot = get();
    for (Object element : snapshot) {
      //noinspection unchecked
      action.accept((E)element);
    }
  }

  /**
   * {@inheritDoc}
   * <p/>
   * <p>The returned iterator provides a snapshot of the state of the list
   * when the iterator was constructed. No synchronization is needed while
   * traversing the iterator. The iterator does <em>NOT</em> support the
   * <tt>remove</tt>, <tt>set</tt> or <tt>add</tt> methods.
   */
  @Override
  public @NotNull ListIterator<E> listIterator() {
    return listIterator(0);
  }

  /**
   * {@inheritDoc}
   * <p/>
   * <p>The returned iterator provides a snapshot of the state of the list
   * when the iterator was constructed. No synchronization is needed while
   * traversing the iterator. The iterator does <em>NOT</em> support the
   * <tt>remove</tt>, <tt>set</tt> or <tt>add</tt> methods.
   *
   * @throws IndexOutOfBoundsException {@inheritDoc}
   */
  @Override
  public @NotNull ListIterator<E> listIterator(final int index) {
    Object[] elements = get();
    int len = elements.length;
    if (index < 0 || index > len) {
      throw new IndexOutOfBoundsException("Index: " + index);
    }

    return elements.length == 0 ? Collections.emptyListIterator() : new COWIterator(elements, index);
  }

  private final class COWIterator implements ListIterator<E> {
    /**
     * Snapshot of the array
     */
    private final Object[] snapshot;
    /**
     * Index of element to be returned by subsequent call to next.
     */
    private int cursor;
    private int lastRet = -1; // index of last element returned; -1 if no such

    private COWIterator(Object @NotNull [] elements, int initialCursor) {
      cursor = initialCursor;
      snapshot = elements;
    }

    @Override
    public boolean hasNext() {
      return cursor < snapshot.length;
    }

    @Override
    public boolean hasPrevious() {
      return cursor > 0;
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      lastRet = cursor;
      //noinspection unchecked
      return (E)snapshot[cursor++];
    }

    @Override
    public E previous() {
      if (!hasPrevious()) {
        throw new NoSuchElementException();
      }
      //noinspection unchecked
      return (E)snapshot[lastRet = --cursor];
    }

    @Override
    public int nextIndex() {
      return cursor;
    }

    @Override
    public int previousIndex() {
      return cursor - 1;
    }

    @Override
    public void remove() {
      if (lastRet < 0) {
        throw new NoSuchElementException();
      }

      //noinspection unchecked
      E e = (E)snapshot[lastRet];
      lastRet = -1;
      LockFreeCopyOnWriteArrayList.this.remove(e);
    }

    @Override
    public void set(E e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(E e) {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public @NotNull List<E> subList(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Spliterator<E> spliterator() {
    //noinspection unchecked
    return (Spliterator<E>)Arrays.spliterator(get());
  }

  @Override
  public void replaceAll(@NotNull UnaryOperator<E> operator) {
    changeAndReplace(elements -> createArrayMap(elements, operator));
  }

  private Object @NotNull [] createArrayMap(Object @NotNull [] elements, @NotNull UnaryOperator<? super E> operator) {
    Object[] newElements = ArrayUtil.newObjectArray(elements.length);
    for (int i = 0; i < elements.length; i++) {
      Object element = elements[i];
      //noinspection unchecked
      newElements[i] = operator.apply((E)element);
    }
    return newElements;
  }

  @Override
  public void sort(Comparator<? super E> c) {
    changeAndReplace(elements -> {
      Object[] sorted = elements.clone();
      //noinspection rawtypes,unchecked
      Arrays.sort(sorted, (Comparator)c);
      return sorted;
    });
  }

  @Override
  public boolean removeIf(@NotNull Predicate<? super E> filter) {
    return changeAndReplace(elements -> createArrayRemoveIf(elements, filter));
  }

  private boolean changeAndReplace(@NotNull UnaryOperator<Object[]> change) {
    while (true) {
      Object[] elements = get();
      Object[] newArray = change.apply(elements);
      if (newArray == null) return false;
      if (replaceArray(elements, newArray)) {
        return true;
      }
    }
  }

  private static Object @Nullable [] createArrayRemoveIf(Object @NotNull [] elements, @SuppressWarnings("rawtypes") @NotNull Predicate filter) {
    int i;
    for (i = 0; i < elements.length; i++) {
      Object element = elements[i];
      //noinspection unchecked
      if (filter.test(element)) {
        break;
      }
    }
    if (i == elements.length) {
      return null;
    }
    Object[] newElements = elements.clone();
    int o = i;
    for (int j = i+1; j < elements.length; j++) {
      Object element = elements[j];
      //noinspection unchecked
      if (!filter.test(element)) {
        newElements[o++] = element;
      }
    }
    return ArrayUtil.realloc(newElements, o, ArrayUtil.OBJECT_ARRAY_FACTORY);
  }
}
