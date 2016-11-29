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
package com.intellij.structureView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TestGrouper implements Grouper {
  private final String[] mySubStrings;

  public TestGrouper(String[] subString) {
    mySubStrings = subString;
  }

  @Override
  @NotNull
  public ActionPresentation getPresentation() {
    throw new RuntimeException();
  }

  @Override
  @NotNull
  public String getName() {
    throw new RuntimeException();
  }

  private class StringGroup implements Group {
    private final String myString;
    private final Collection<TreeElement> myChildren;
    private final Collection<String> myChildrenUsedStrings;

    public StringGroup(String string, final Collection<TreeElement> children, Collection<String> childrenStrings) {
      myString = string;
      myChildrenUsedStrings = childrenStrings;
      myChildren = new ArrayList<>(children);
    }

    @NotNull
    @Override
    public Collection<TreeElement> getChildren() {
      Collection<TreeElement> result = new LinkedHashSet<>();
      for (TreeElement object : myChildren) {
        if (object.toString().indexOf(myString) >= 0) {
          result.add(object);
        }
      }
      return result;
    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
      return null;
    }

    public String toString() {
      return "Group:" + myString;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Group)) return false;

      final StringGroup group = (StringGroup)o;

      if (myString != null ? !myString.equals(group.myString) : group.myString != null) return false;

      return true;
    }

    public int hashCode() {
      return (myString != null ? myString.hashCode() : 0);
    }
  }

  @Override
  @NotNull
  public Collection<Group> group(@NotNull final AbstractTreeNode parent, @NotNull Collection<TreeElement> children) {
    List<Group> result = new ArrayList<>();
    Collection<String> parentGroupUsedStrings = parent.getValue() instanceof StringGroup ?
                                                ((StringGroup)parent.getValue()).myChildrenUsedStrings :
                                                Collections.<String>emptySet();
    Collection<TreeElement> elements = new LinkedHashSet<>(children);
    for (String subString : mySubStrings) {
      if (parentGroupUsedStrings.contains(subString)) continue;
      Set<String> childrenStrings = new THashSet<>(parentGroupUsedStrings);
      ContainerUtil.addAll(childrenStrings, mySubStrings);
      StringGroup group = new StringGroup(subString, elements, childrenStrings);
      Collection<TreeElement> groupChildren = group.getChildren();
      if (!groupChildren.isEmpty()) {
        elements.removeAll(groupChildren);
        result.add(group);
      }
    }
    return result;
  }
}
