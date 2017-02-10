/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.dbCommitted;

import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/9/12
 * Time: 3:18 PM
 */
public class KnownRepositoryLocations {
  private final static int ourMaxAuthorsCached = 400;

  private final Map<String, Long> myJustVcs;
  private final MultiMap<String, String> myMap;
  private final Map<Couple<String>, Long> myLocations;
  private final Map<Long, RevisionId> myLastRevision;
  private final Map<Long, RevisionId> myFirstRevision;
  private final Map<String, Long> myAuthors;

  public KnownRepositoryLocations() {
    myMap = MultiMap.createSet();
    myLocations = new HashMap<>();
    myLastRevision = new HashMap<>();
    myFirstRevision = new HashMap<>();
    myJustVcs = new HashMap<>();
    myAuthors = new HashMap<>();
  }

  public Map<String, Long> filterKnownAuthors(final Set<String> names) {
    if (names.isEmpty()) return Collections.emptyMap();
    synchronized (myMap) {
      final Map<String, Long> result = new HashMap<>();
      for (Iterator<String> iterator = names.iterator(); iterator.hasNext(); ) {
        final String name = iterator.next();
        final Long pk = myAuthors.get(name);
        if (pk != null) {
          iterator.remove();
          result.put(name, pk);
        }
      }
      return result;
    }
  }

  public void addKnownAuthor(final String name, final long pk) {
    synchronized (myMap) {
      if (myAuthors.size() > ourMaxAuthorsCached) {
        // random?
        final Iterator<Map.Entry<String, Long>> iterator = myAuthors.entrySet().iterator();
        int cnt = 10;
        while (iterator.hasNext() && cnt > 0) {
          Map.Entry<String, Long> next = iterator.next();
          iterator.remove();
          -- cnt;
        }
      }
      myAuthors.put(name, pk);
    }
  }

  public boolean exists(final String key) {
    synchronized (myMap) {
      return myJustVcs.containsKey(key);
    }
  }

  public long getVcsKey(final String key) {
    synchronized (myMap) {
      final Long aLong = myJustVcs.get(key);
      assert aLong != null;
      return aLong;
    }
  }

  public boolean exists(final String key, final String path) {
    synchronized (myMap) {
      final Collection<String> strings = myMap.get(key);
      return strings != null && strings.contains(path);
    }
  }

  public void addVcs(final String key, final long id) {
    synchronized (myMap) {
      myJustVcs.put(key, id);
    }
  }

  public long getLocationId(final String key, final String path) {
    synchronized (myMap) {
      final Long id = myLocations.get(Couple.of(key, path));
      assert  id != null;
      return id;
    }
  }

  public void add(final String key, final String path, final long id) {
    synchronized (myMap) {
      myMap.putValue(key, path);
      myLocations.put(Couple.of(key, path), id);
    }
  }

  public RevisionId getLastRevision(final Long rootId) {
    synchronized (myMap) {
      return myLastRevision.get(rootId);
    }
  }

  public void setLastRevision(final Long rootId, final RevisionId number) {
    synchronized (myMap) {
      myLastRevision.put(rootId, number);
    }
  }

  public RevisionId getFirstRevision(final Long rootId) {
    synchronized (myMap) {
      return myFirstRevision.get(rootId);
    }
  }

  public void setFirstRevision(final Long rootId, final RevisionId number) {
    synchronized (myMap) {
      myFirstRevision.put(rootId, number);
    }
  }
}
