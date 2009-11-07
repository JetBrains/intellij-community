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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.File;
import java.util.*;

public class FilesForRefresh {
  private final List<File> myRecursively;
  private final List<File> myPoints;

  public FilesForRefresh() {
    myRecursively = new ArrayList<File>();
    myPoints = new ArrayList<File>();
  }

  public void addPoint(final File vf) {
    if (vf == null) return;
    myPoints.add(vf);
  }

  public void addRecursively(final File vf) {
    if (vf == null) return;
    myRecursively.add(vf);
  }

  public void filter() {
    filterEqual(myRecursively);
    filterEqual(myPoints);
    
    final ParentsFirstVFComparator comparator = new ParentsFirstVFComparator();
    Collections.sort(myRecursively, comparator);

    /*final Set<String> superfluous = new HashSet<String>();
    for (int i = (myRecursively.size() - 1); i > 0; -- i) {
      final File file = myRecursively.get(i);
      for (int j = i - 1; j >= 0; -- j) {
        final File parent = myRecursively.get(j);
        if (VfsUtil.isAncestor(parent, file, true)) {
          superfluous.add(file.getAbsolutePath());
          break;
        }
      }
    }

    for (Iterator<File> iterator = myRecursively.iterator(); iterator.hasNext();) {
      final File f = iterator.next();
      if (superfluous.contains(f.getAbsolutePath())) {
        iterator.remove();
      }
    }*/

    for (Iterator<File> iterator = myPoints.iterator(); iterator.hasNext();) {
      final File f = iterator.next();
      for (File recursive : myRecursively) {
        if (VfsUtil.isAncestor(recursive, f, false)) {
          iterator.remove();
        }
      }
    }

    // todo remove
    /*
    System.out.println("recursive");
    for (File file : myRecursively) {
      System.out.println(file.getAbsolutePath());
    }
    System.out.println("points");
    for (File file : myPoints) {
      System.out.println(file.getAbsolutePath());
    }
    */
  }

  private void filterEqual(final List<File> files) {
    final Set<String> set = new HashSet<String>();
    for (File file : files) {
      set.add(file.getAbsolutePath());
    }
    files.clear();
    for (String s : set) {
      files.add(new File(s));
    }
  }

  public List<File> getRecursively() {
    return myRecursively;
  }

  public List<File> getPoints() {
    return myPoints;
  }

  private static class ParentsFirstVFComparator implements Comparator<File> {
    public int compare(File o1, File o2) {
      if (Comparing.equal(o1.getAbsolutePath(), o2.getAbsolutePath())) return 0;
      if (VfsUtil.isAncestor(o1, o2, true)) {
        return -1;
      }
      if (VfsUtil.isAncestor(o2, o1, true)) {
        return 1;
      }
      return 0;
    }
  }
}
