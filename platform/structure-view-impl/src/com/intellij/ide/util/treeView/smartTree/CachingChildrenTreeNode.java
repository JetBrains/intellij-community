// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.structureView.impl.StructureViewElementWrapper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class CachingChildrenTreeNode <Value> extends AbstractTreeNode<Value> {
  private static final Logger LOG = Logger.getInstance(CachingChildrenTreeNode.class);
  private List<CachingChildrenTreeNode<?>> myChildren;
  private List<CachingChildrenTreeNode<?>> myOldChildren;
  @NotNull
  protected final TreeModel myTreeModel;

  CachingChildrenTreeNode(Project project, @NotNull Value value, @NotNull TreeModel treeModel) {
    super(project,
          value instanceof StructureViewElementWrapper ? (Value)((StructureViewElementWrapper<?>)value).getWrappedElement() : value);
    myTreeModel = treeModel;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    ensureChildrenAreInitialized();
    return new ArrayList<>(myChildren);
  }

  private void ensureChildrenAreInitialized() {
    if (myChildren == null) {
      try {
        myChildren = new ArrayList<>();
        rebuildSubtree();
      } catch (IndexNotReadyException | ProcessCanceledException pce) {
        myChildren = null;
        throw pce;
      }
    }
  }

  void addSubElement(@NotNull CachingChildrenTreeNode node) {
    JBIterable<AbstractTreeNode<?>> parents = JBIterable.generate(this, o -> o.getParent());
    if (parents.map(o -> o.getValue()).contains(node.getValue())) {
      return;
    }
    ensureChildrenAreInitialized();
    myChildren.add(node);
    node.setParent(this);
  }

  protected void setChildren(@NotNull Collection<? extends AbstractTreeNode<?>> children) {
    clearChildren();
    for (AbstractTreeNode node : children) {
      myChildren.add((CachingChildrenTreeNode)node);
      node.setParent(this);
    }
  }

  private static class CompositeComparator implements Comparator<CachingChildrenTreeNode> {
    private final Sorter[] mySorters;

    CompositeComparator(Sorter @NotNull [] sorters) {
      mySorters = sorters;
    }

    @Override
    public int compare(final CachingChildrenTreeNode o1, final CachingChildrenTreeNode o2) {
      final Object value1 = o1.getValue();
      final Object value2 = o2.getValue();
      for (Sorter sorter : mySorters) {
        int result = sorter.getComparator().compare(value1, value2);
        if (result != 0) {
          return result;
        }
      }
      return 0;
    }
  }

  protected void sortChildren(Sorter @NotNull [] sorters) {
    if (myChildren == null) return;

    myChildren.sort(new CompositeComparator(sorters));

    for (CachingChildrenTreeNode child : myChildren) {
      if (child instanceof GroupWrapper) {
        child.sortChildren(sorters);
      }
    }
  }

  protected void filterChildren(Filter @NotNull [] filters) {
    Collection<AbstractTreeNode<?>> children = getChildren();
    for (Filter filter : filters) {
      for (Iterator<AbstractTreeNode<?>> eachNode = children.iterator(); eachNode.hasNext();) {
        AbstractTreeNode eachChild = eachNode.next();
        Object value = eachChild.getValue();
        if (!(value instanceof TreeElement) || !filter.isVisible((TreeElement)value)) {
          eachNode.remove();
        }
      }
    }
    setChildren(children);
  }

  void groupChildren(Grouper @NotNull [] groupers) {
    for (Grouper grouper : groupers) {
      groupElements(grouper);
    }
    Collection<AbstractTreeNode<?>> children = getChildren();
    for (AbstractTreeNode child : children) {
      if (child instanceof GroupWrapper) {
        ((GroupWrapper)child).groupChildren(groupers);
      }
    }
  }

  private void groupElements(@NotNull Grouper grouper) {
    List<AbstractTreeNode<TreeElement>> ungrouped = new ArrayList<>();
    Collection<AbstractTreeNode<?>> children = getChildren();
    for (AbstractTreeNode child : children) {
      if (child instanceof TreeElementWrapper) {
        //noinspection unchecked
        ungrouped.add(child);
      }
    }

    if (!ungrouped.isEmpty()) {
      processUngrouped(ungrouped, grouper);
    }

    Collection<AbstractTreeNode<?>> result = new LinkedHashSet<>();
    for (AbstractTreeNode child : children) {
      AbstractTreeNode parent = child.getParent();
      if (parent != this) {
        if (!result.contains(parent)) result.add(parent);
      }
      else {
        result.add(child);
      }
    }
    setChildren(result);
  }

  private void processUngrouped(@NotNull List<? extends AbstractTreeNode<TreeElement>> ungrouped, @NotNull Grouper grouper) {
    Map<TreeElement,AbstractTreeNode> ungroupedObjects = collectValues(ungrouped);
    Collection<Group> groups = grouper.group(this, ungroupedObjects.keySet());

    Map<Group, GroupWrapper> groupNodes = createGroupNodes(groups);

    for (Group group : groups) {
      if (group == null) {
        LOG.error(grouper + " returned null group: "+groups);
      }
      GroupWrapper groupWrapper = groupNodes.get(group);
      Collection<TreeElement> children = group.getChildren();
      for (TreeElement node : children) {
        if (node == null) {
          LOG.error(group + " returned null child: " + children);
        }
        CachingChildrenTreeNode child = createChildNode(node);
        groupWrapper.addSubElement(child);
        AbstractTreeNode abstractTreeNode = ungroupedObjects.get(node);
        abstractTreeNode.setParent(groupWrapper);
      }
    }
  }

  @NotNull
  protected TreeElementWrapper createChildNode(@NotNull TreeElement child) {
    return new TreeElementWrapper(getProject(), child, myTreeModel);
  }

  @NotNull
  private static Map<TreeElement, AbstractTreeNode> collectValues(@NotNull List<? extends AbstractTreeNode<TreeElement>> ungrouped) {
    Map<TreeElement, AbstractTreeNode> objects = new LinkedHashMap<>();
    for (final AbstractTreeNode<TreeElement> node : ungrouped) {
      objects.put(node.getValue(), node);
    }
    return objects;
  }

  @NotNull
  private Map<Group, GroupWrapper> createGroupNodes(@NotNull Collection<? extends Group> groups) {
    Map<Group, GroupWrapper> result = CollectionFactory.createSmallMemoryFootprintMap(groups.size());
    for (Group group : groups) {
      result.put(group, createGroupWrapper(getProject(), group, myTreeModel));
    }
    return result;
  }

  @NotNull
  protected GroupWrapper createGroupWrapper(final Project project, @NotNull Group group, @NotNull TreeModel treeModel) {
    return new GroupWrapper(project, group, treeModel);
  }


  private void rebuildSubtree() {
    initChildren();
    performTreeActions();

    synchronizeChildren();

  }

  void synchronizeChildren() {
    List<CachingChildrenTreeNode<?>> children = myChildren;
    if (myOldChildren != null && children != null) {
      HashMap<Object, CachingChildrenTreeNode> oldValuesToChildrenMap = new HashMap<>();
      for (CachingChildrenTreeNode oldChild : myOldChildren) {
        final Object oldValue = oldChild instanceof TreeElementWrapper ? oldChild.getValue() : oldChild;
        if (oldValue != null) {
          oldValuesToChildrenMap.put(oldValue, oldChild);
        }
      }

      for (int i = 0; i < children.size(); i++) {
        CachingChildrenTreeNode newChild = children.get(i);
        final Object newValue = newChild instanceof TreeElementWrapper ? newChild.getValue() : newChild;
        if (newValue != null) {
          final CachingChildrenTreeNode oldChild = oldValuesToChildrenMap.get(newValue);
          if (oldChild != null) {
            oldChild.copyFromNewInstance(newChild);
            oldChild.setValue(newChild.getValue());
            children.set(i, oldChild);
          }
        }
      }

      myOldChildren = null;
    }
  }

  protected abstract void copyFromNewInstance(@NotNull CachingChildrenTreeNode newInstance);

  protected abstract void performTreeActions();

  protected abstract void initChildren();

  @Override
  public void navigate(final boolean requestFocus) {
    ((Navigatable)getValue()).navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return getValue() instanceof Navigatable && ((Navigatable)getValue()).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getValue() instanceof Navigatable && ((Navigatable)getValue()).canNavigateToSource();
  }

  protected void clearChildren() {
    if (myChildren != null) {
      myChildren.clear();
    } else {
      myChildren = new ArrayList<>();
    }
  }

  void rebuildChildren() {
    if (myChildren != null) {
      myOldChildren = myChildren;
      for (final CachingChildrenTreeNode node : myChildren) {
        node.rebuildChildren();
      }
      myChildren = null;
    }
  }

  protected void resetChildren() {
    myChildren = null;
  }
}
