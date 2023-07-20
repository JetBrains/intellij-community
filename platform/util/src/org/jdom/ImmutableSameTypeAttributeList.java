// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jdom;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class ImmutableSameTypeAttributeList implements List<Attribute> {
  private static final String[] EMPTY_STRING_ARRAY = ArrayUtilRt.EMPTY_STRING_ARRAY;
  private final String[] myNameValues;
  private final AttributeType myType;
  private final Namespace myNs;

  ImmutableSameTypeAttributeList(String @NotNull [] nameValues, AttributeType type, @NotNull Namespace ns) {
    myNameValues = nameValues.length == 0 ? EMPTY_STRING_ARRAY : nameValues;
    myType = type;
    myNs = ns;
  }

  @Override
  public Attribute get(int index) {
    return new ImmutableAttribute(myNameValues[index * 2], myNameValues[index * 2 + 1], myType, myNs);
  }

  Attribute get(String name, Namespace namespace) {
    if (!myNs.equals(namespace)) return null;
    for (int i = 0; i < myNameValues.length; i += 2) {
      String aname = myNameValues[i];
      if (aname.equals(name)) {
        return get(i / 2);
      }
    }
    return null;
  }

  String getValue(String name, Namespace namespace, String def) {
    if (!myNs.equals(namespace)) return def;
    for (int i = 0; i < myNameValues.length; i += 2) {
      String aname = myNameValues[i];
      if (aname.equals(name)) {
        return myNameValues[i + 1];
      }
    }
    return def;
  }

  @Override
  public int size() {
    return myNameValues.length / 2;
  }

  @Override
  public String toString() {
    return toList().toString();
  }

  @Override
  public int indexOf(Object o) {
    for (int i = 0; i < size(); i++) {
      if (Comparing.equal(0, get(i))) return i;
    }
    return -1;
  }

  @Override
  public int lastIndexOf(Object o) {
    for (int i = size() - 1; i >= 0; i--) {
      if (Comparing.equal(0, get(i))) return i;
    }
    return -1;
  }

  @Override
  public @NotNull Iterator<Attribute> iterator() {
    if (isEmpty()) return Collections.emptyIterator();
    return new Iterator<Attribute>() {
      int i;

      @Override
      public boolean hasNext() {
        return i < size();
      }

      @Override
      public Attribute next() {
        return get(i++);
      }

      @Override
      public void remove() {
        throw ImmutableElement.immutableError(ImmutableSameTypeAttributeList.this);
      }
    };
  }

  @Override
  public @NotNull List<Attribute> subList(int fromIndex, int toIndex) {
    return toList().subList(fromIndex, toIndex);
  }

  private List<Attribute> toList() {
    List<Attribute> list = new ArrayList<>(size());
    for (int i = 0; i < size(); i++) {
      //noinspection UseBulkOperation -- ArrayList.addAll() will delegate to toArray(), but toArray() delegates to this method
      list.add(get(i));
    }
    return list;
  }


  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof List) || size() != ((List<?>)o).size()) {
      return false;
    }
    if (size() == 0) {
      return true;
    }
    if (o instanceof ImmutableSameTypeAttributeList) {
      ImmutableSameTypeAttributeList io = (ImmutableSameTypeAttributeList)o;
      return myType == io.myType && myNs.equals(io.myNs) && Arrays.equals(myNameValues, io.myNameValues);
    }

    List<Attribute> l = (List<Attribute>)o;
    for (int i = 0; i < myNameValues.length; i += 2) {
      String name = myNameValues[i];
      String value = myNameValues[i + 1];

      Attribute a2 = l.get(i / 2);

      if (!Objects.equals(name, a2.getName()) ||
          !Objects.equals(value, a2.getValue()) ||
          !Comparing.equal(myType, a2.getAttributeType()) ||
          !Comparing.equal(myNs, a2.getNamespace())
      ) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int i = 0; i < myNameValues.length; i += 2) {
      String name = myNameValues[i];
      String value = myNameValues[i + 1];
      result = result * 31 + JDOMInterner.computeAttributeHashCode(name, value);
    }
    return result;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean contains(Object o) {
    return indexOf(o) != -1;
  }

  @Override
  public Object @NotNull [] toArray() {
    return toList().toArray(new Attribute[0]);
  }

  @Override
  public <T> T @NotNull [] toArray(T @NotNull [] a) {
    return (T[])toArray();
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    for (Object o : c) {
      if (!contains(o)) return false;
    }
    return true;
  }
  ///////////////////////////////////////////////////

  @Override
  public boolean add(Attribute obj) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public void add(int index, Attribute obj) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends Attribute> collection) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public boolean addAll(int index, @NotNull Collection<? extends Attribute> collection) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public void clear() {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public Attribute remove(int index) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public Attribute set(int index, Attribute obj) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public @NotNull ListIterator<Attribute> listIterator() {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public @NotNull ListIterator<Attribute> listIterator(int index) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public boolean remove(Object o) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw ImmutableElement.immutableError(this);
  }
}
