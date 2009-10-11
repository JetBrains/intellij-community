/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The base class for named git references
 */
public abstract class GitReference implements Comparable<GitReference> {
  /**
   * The name of the reference
   */
  protected final String myName;

  /**
   * The constructor
   *
   * @param name the used name
   */
  public GitReference(@NotNull String name) {
    myName = name;
  }

  /**
   * @return the local name of the reference
   */
  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * @return the full name of the object
   */
  @NotNull
  public abstract String getFullName();

  /**
   * @return the full name for the reference ({@link #getFullName()}.
   */
  @Override
  public String toString() {
    return getFullName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object obj) {
    return obj instanceof GitReference &&
           toString().equals(obj.toString());    //To change body of overridden methods use File | Settings | File Templates.
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(final GitReference o) {
    return o == null ? 1 : getFullName().compareTo(o.getFullName());
  }

  /**
   * Get name clashes for the for the sequence of the collections
   *
   * @param collections the collection list
   * @return the conflict set
   */
  public static Set<String> getNameClashes(Collection<? extends GitReference>... collections) {
    ArrayList<HashSet<String>> individual = new ArrayList<HashSet<String>>();
    // collect individual key sets
    for (Collection<? extends GitReference> c : collections) {
      HashSet<String> s = new HashSet<String>();
      individual.add(s);
      for (GitReference r : c) {
        s.add(r.getName());
      }
    }
    HashSet<String> rc = new HashSet<String>();
    // all pairs from array
    for (int i = 0; i < collections.length - 1; i++) {
      HashSet<String> si = individual.get(i);
      for (int j = i + 1; j < collections.length; j++) {
        HashSet<String> sj = individual.get(i);
        final HashSet<String> copy = new HashSet<String>(si);
        copy.retainAll(sj);
        rc.addAll(copy);
      }
    }
    return rc;
  }
}
