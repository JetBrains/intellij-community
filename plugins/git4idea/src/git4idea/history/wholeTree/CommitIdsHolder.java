/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;

import java.util.*;

/**
 * @author irengrig
 */
public class CommitIdsHolder<Item> {
  private final MyLruCache<Item, Item> myRequests;
  private final Object myLock;

  public CommitIdsHolder() {
    myRequests = new MyLruCache<Item, Item>(50, 30);
    myLock = new Object();
  }

  public void add(final Collection<Item> s) {
    synchronized (myLock) {
      for (Item hash : s) {
        myRequests.put(hash, hash);
      }
    }
  }

  public boolean haveData() {
    synchronized (myLock) {
      return ! myRequests.isEmpty();
    }
  }

  public Collection<Item> get(final int size) {
    final Set<Item> result = new HashSet<Item>();
    synchronized (myLock) {
      final Iterator<Item> iterator = myRequests.iterator();
      int cnt = 0;
      for (; iterator.hasNext() && (cnt < size);) {
        final Item hash = iterator.next();
        iterator.remove();
        result.add(hash);
        ++ cnt;
      }
    }
    return result;
  }

  private static class MyLruCache<Key, Val> extends SLRUMap<Key, Val> {
    private MyLruCache(int protectedQueueSize, int probationalQueueSize) {
      super(protectedQueueSize, probationalQueueSize);
    }

    public boolean isEmpty() {
      return myProtectedQueue.keySet().isEmpty() && myProbationalQueue.keySet().isEmpty();
    }

    public Iterator<Key> iterator() {
      final List<Iterator<Key>> iterators = new ArrayList<Iterator<Key>>(2);
      iterators.add(myProbationalQueue.keySet().iterator());
      iterators.add(myProtectedQueue.keySet().iterator());
      return ContainerUtil.concatIterators(iterators);
    }
  }
}
