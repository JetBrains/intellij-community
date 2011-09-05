/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/1/11
 * Time: 12:37 PM
 */
public class SortListsByFirst<T> {
  private final List<T> myFirst;
  private final List<Integer> myOrder;
  private final Comparator<T> myComparator;

  public SortListsByFirst(List<T> first, Comparator<T> comparator) {
    myFirst = first;
    myComparator = comparator;
    myOrder = new ArrayList<Integer>(myFirst.size());
    for (int i = 0; i < myFirst.size(); i++) {
      myOrder.add(i);
    }
    Collections.sort(myOrder, new Comparator<Integer>() {
      @Override
      public int compare(Integer o1, Integer o2) {
        return myComparator.compare(myFirst.get(o1), myFirst.get(o2));
      }
    });
    sortAnyOther(myFirst);
  }

  public List<T> getFirst() {
    return myFirst;
  }

  public<S> void sortAnyOther(final List<S> other) {
    final ArrayList<S> copy = new ArrayList<S>(other);
    other.clear();
    for (Integer i : myOrder) {
      other.add(copy.get(i));
    }
  }
}
