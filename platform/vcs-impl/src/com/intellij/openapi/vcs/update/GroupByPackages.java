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
package com.intellij.openapi.vcs.update;

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class GroupByPackages {

  private final Map<File, Collection<File>> myParentToChildrenMap
      = new HashMap<File, Collection<File>>();
  private final Collection<File> myRoots = new HashSet<File>();

  public GroupByPackages(Collection<File> fiels) {
    for (Iterator each = fiels.iterator(); each.hasNext();) {
      process((File)each.next());
    }

    splitRoots();
  }

  private void splitRoots() {
    for (Iterator each = new ArrayList(myRoots).iterator(); each.hasNext();) {
      File oldRoot = (File)each.next();
      File newRoot = splitRoot(oldRoot);
      if (!oldRoot.equals(newRoot)) replaceRoot(oldRoot, newRoot);
    }
  }

  private void replaceRoot(File oldRoot, File newRoot) {
    myRoots.remove(oldRoot);
    myRoots.add(newRoot);
  }

  private File splitRoot(File oldRoot) {
    Collection<File> children = getChildren(oldRoot);
    if (children == null) return oldRoot;
    if (children.size() == 1)
      return splitRoot(children.iterator().next());
    else {
      return oldRoot;
    }

  }

  private void process(File file) {
    File parent = file.getParentFile();
    if (parent == null) {
      myRoots.add(file);
      return;
    }

    if (!myParentToChildrenMap.containsKey(parent)) myParentToChildrenMap.put(parent, new HashSet<File>());
    myParentToChildrenMap.get(parent).add(file);

    process(parent);
  }

  public List<File> getRoots() {
    return new ArrayList<File>(myRoots);
  }

  public List<File> getChildren(File file) {
    Collection<File> collection = myParentToChildrenMap.get(file);
    if (collection == null)
      return new ArrayList<File>();
    else
      return new ArrayList<File>(collection);
  }
}
