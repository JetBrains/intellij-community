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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

/**
 * @author Konstantin Bulenkov
 */
public class DTree {
  private static final Comparator<DTree> COMPARATOR = new Comparator<DTree>() {
    @Override
    public int compare(DTree o1, DTree o2) {
      final boolean b1 = o1.isContainer();
      final boolean b2 = o2.isContainer();
      return (b1 && b2) || (!b1 && !b2)
             ? o1.getName().compareToIgnoreCase(o2.getName())
             : b1 ? 1 : -1;
    }
  };

  private boolean expanded = true;
  @Nullable private final DTree myParent;
  private HashMap<String, DTree> children;
  private String myName;
  private final boolean isContainer;
  private SortedList<DTree> myChildrenList;
  private DiffElement<?> mySource;
  private DiffElement<?> myTarget;
  private DType type;
  private boolean myVisible = true;

  public DTree(@Nullable DTree parent, @NotNull String name, boolean container) {
    this.myParent = parent;
    this.myName = name;
    isContainer = container;
  }

  public Collection<DTree> getChildren() {
    init();
    if (myChildrenList == null) {
      myChildrenList = new SortedList<DTree>(COMPARATOR);
      myChildrenList.addAll(children.values());
    }
    return myChildrenList;
  }

  public DTree addChild(@NotNull DiffElement element, boolean source) {
    init();
    myChildrenList = null;
    final DTree node;
    final String name = element.getName();
    if (children.containsKey(name)) {
      node = children.get(name);
    } else {
      node = new DTree(this, name, element.isContainer());
      children.put(name, node);
    }

    if (source) {
      node.setSource(element);
    } else {
      node.setTarget(element);
    }

    return node;
  }

  public DiffElement<?> getSource() {
    return mySource;
  }

  public void setSource(DiffElement<?> source) {
    mySource = source;
  }

  public DiffElement<?> getTarget() {
    return myTarget;
  }

  public void setTarget(DiffElement<?> target) {
    myTarget = target;
  }

  private void init() {
    if (children == null) {
      children = new HashMap<String, DTree>();
    }
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public DTree getParent() {
    return myParent;
  }

  public boolean isExpanded() {
    return expanded;
  }

  public void setExpanded(boolean expanded) {
    this.expanded = expanded;
  }

  public boolean isContainer() {
    return isContainer;
  }

  @Override
  public String toString() {
    return myName;
  }

  public void update(DirDiffSettings settings) {
    for (DTree tree : getChildren()) {
      final DiffElement<?> src = tree.getSource();
      final DiffElement<?> trg = tree.getTarget();
      if (src == null && trg != null) {
        tree.setType(DType.TARGET);
      } else if (src != null && trg == null) {
        tree.setType(DType.SOURCE);
      } else {
        assert src != null;
        DType dtype = src.getSize() == trg.getSize() ? DType.EQUAL : DType.CHANGED;
        if (dtype == DType.EQUAL && settings.compareByContent) {
          dtype = isEqual(src, trg) ? DType.EQUAL : DType.CHANGED;
        }
        tree.setType(dtype);
      }
      tree.update(settings);
    }
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void updateVisibility(DirDiffSettings settings) {
    if (children.isEmpty()) {
      switch (type) {
        case SOURCE:
          myVisible = settings.showNewOnSource;
          break;
        case TARGET:
          myVisible = settings.showNewOnTarget;
          break;
        case SEPARATOR:
          myVisible = true;
          break;
        case CHANGED:
          myVisible = settings.showDifferent;
          break;
        case EQUAL:
          myVisible = settings.showEqual;
          break;
      }
    } else {
      myVisible = false;
      for (DTree child : children.values()) {
        child.updateVisibility(settings);
        myVisible = myVisible || child.isVisible();
      }
    }
  }

  private static boolean isEqual(DiffElement file1, DiffElement file2) {
    if (file1.isContainer() || file2.isContainer()) return false;
    if (file1.getSize() != file2.getSize()) return false;
    try {
      return Arrays.equals(file1.getContent(), file2.getContent());
    }
    catch (IOException e) {
      return false;
    }
  }

  public DType getType() {
    return type;
  }

  public void setType(DType type) {
    this.type = type;
  }

  public String getPath() {
    final DTree parent = getParent();
    if (parent != null) {
      return parent.getPath() + getName() + (isContainer ? getSeparator() : "");
    } else {
      return getName() + (isContainer ? getSeparator() : "");
    }
  }

  private String getSeparator() {
    final String s = mySource != null ? mySource.getSeparator() : myTarget != null ? myTarget.getSeparator() : "";
    return s;
  }
}
