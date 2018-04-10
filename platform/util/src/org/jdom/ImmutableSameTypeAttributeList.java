// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.jdom;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.EmptyIterator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class ImmutableSameTypeAttributeList implements List<Attribute> {
  private static final String[] EMPTY_STRING_ARRAY = new String[0];
  private final String[] myNameValues;
  private final int myType;
  private final Namespace myNs;

  ImmutableSameTypeAttributeList(@NotNull String[] nameValues, int type, @NotNull Namespace ns) {
    myNameValues = nameValues.length == 0 ? EMPTY_STRING_ARRAY : nameValues;
    myType = type;
    myNs = ns;
  }

  @Override
  public Attribute get(int index) {
    return new ImmutableAttribute(myNameValues[index*2], myNameValues[index*2+1], myType, myNs);
  }

  Attribute get(String name, Namespace namespace) {
    if (!myNs.equals(namespace)) return null;
    for (int i = 0; i < myNameValues.length; i+=2) {
      String aname = myNameValues[i];
      if (aname.equals(name)) {
        return get(i/2);
      }
    }
    return null;
  }

  String getValue(String name, Namespace namespace, String def) {
    if (!myNs.equals(namespace)) return def;
    for (int i = 0; i < myNameValues.length; i+=2) {
      String aname = myNameValues[i];
      if (aname.equals(name)) {
        return myNameValues[i + 1];
      }
    }
    return def;
  }
  
  @Override
  public int size() {
    return myNameValues.length/2;
  }

  @Override
  public String toString() {
    return toList().toString();
  }

  @Override
  public int indexOf(Object o) {
    for (int i = 0; i < size(); i++) {
      if (Comparing.equal(0,get(i))) return i;
    }
    return -1;
  }

  @Override
  public int lastIndexOf(Object o) {
    for (int i = size() - 1; i >= 0; i--) {
      if (Comparing.equal(0,get(i))) return i;
    }
    return -1;
  }

  @NotNull
  @Override
  public Iterator<Attribute> iterator() {
    if (isEmpty()) return EmptyIterator.getInstance();
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

  @NotNull
  @Override
  public List<Attribute> subList(int fromIndex, int toIndex) {
    return toList().subList(fromIndex, toIndex);
  }

  private List<Attribute> toList() {
    List<Attribute> list = new ArrayList<Attribute>(size());
    for (int i = 0; i < size(); i++) {
      list.add(get(i));
    }                  
    return list;
  }


  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof List) || size() != ((List)o).size()) {
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
    for (int i=0; i<myNameValues.length; i+=2) {
      String name = myNameValues[i];
      String value = myNameValues[i+1];

      Attribute a2 = l.get(i/2);

      if (!Comparing.equal(name, a2.getName()) ||
          !Comparing.equal(value, a2.getValue()) ||
          !Comparing.equal(myType, a2.getAttributeType()) ||
          !Comparing.equal(myNs, a2.getNamespace())
        ) return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int i=0; i<myNameValues.length; i+=2) {
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

  @NotNull
  @Override
  public Object[] toArray() {
    return toList().toArray(new Attribute[0]);
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
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

  @NotNull
  @Override
  public ListIterator<Attribute> listIterator() {
    throw ImmutableElement.immutableError(this);
  }

  @NotNull
  @Override
  public ListIterator<Attribute> listIterator(int index) {
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
