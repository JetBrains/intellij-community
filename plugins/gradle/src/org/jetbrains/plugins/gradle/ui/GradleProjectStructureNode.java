package org.jetbrains.plugins.gradle.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 3:50 PM
 * @param <T>   type of the target entity {@link GradleProjectStructureNodeDescriptor#getElement() associated} with the current node
 */
public class GradleProjectStructureNode<T> extends DefaultMutableTreeNode implements Iterable<GradleProjectStructureNode<?>> {
  
  private final Set<GradleProjectStructureChange> myConflictChanges = new HashSet<GradleProjectStructureChange>();
  
  private final GradleProjectStructureNodeDescriptor<T> myDescriptor;
  private final GradleEntityType                        myType;
  
  public GradleProjectStructureNode(@NotNull GradleProjectStructureNodeDescriptor<T> descriptor, @NotNull GradleEntityType type) {
    super(descriptor);
    myDescriptor = descriptor;
    myType = type;
  }

  @NotNull
  public GradleProjectStructureNodeDescriptor<T> getDescriptor() {
    return myDescriptor;
  }

  @NotNull
  public GradleEntityType getType() {
    return myType;
  }

  @Override
  public GradleProjectStructureNode<?> getChildAt(int index) {
    return (GradleProjectStructureNode)super.getChildAt(index);
  }

  @Override
  public GradleProjectStructureNode<?> getParent() {
    return (GradleProjectStructureNode)super.getParent();
  }

  @Override
  public void add(MutableTreeNode newChild) {
    GradleProjectStructureNode<?> child = (GradleProjectStructureNode)newChild;
    final String newName = child.getDescriptor().getName();
    for (int i = 0; i < getChildCount(); i++) {
      GradleProjectStructureNode<?> node = getChildAt(i);
      if (newName.compareTo(node.getDescriptor().getName()) < 0) {
        insert(newChild, i);
        return;
      }
    }
    super.add(newChild);
  }

  /**
   * Registers given change within the given node assuming that it is
   * {@link GradleTextAttributes#GRADLE_CHANGE_CONFLICT 'conflict change'}. We need to track number of such changes per-node because
   * of the following possible situation:
   * <pre>
   * <ol>
   *   <li>There are two 'conflict changes' for particular node;</li>
   *   <li>
   *     One of those changes is resolved but the node still should be marked as 'conflict' because there is still one conflict change;
   *   </li>
   *   <li>The second conflict change is removed. The node should be marked as 'no change' now;</li>
   * </ol>
   * </pre>
   * 
   * @param change  conflict change to register for the current node
   */
  public void addConflictChange(@NotNull GradleProjectStructureChange change) {
    myConflictChanges.add(change);
    myDescriptor.setAttributes(GradleTextAttributes.GRADLE_CHANGE_CONFLICT);
  }

  /**
   * Performs reverse operation to {@link #addConflictChange(GradleProjectStructureChange)}.
   * 
   * @param change  conflict change to de-register from the current node
   */
  public void removeConflictChange(@NotNull GradleProjectStructureChange change) {
    myConflictChanges.remove(change);
    if (myConflictChanges.isEmpty()) {
      myDescriptor.setAttributes(GradleTextAttributes.GRADLE_NO_CHANGE);
    }
  }
  
  /**
   * Allows to query current node for all children that are associated with the entity of the given type.
   * 
   * @param clazz  target entity type
   * @param <C>    target entity type
   * @return       all children nodes that are associated with the entity of the given type if any;
   *               empty collection otherwise
   */
  @SuppressWarnings("unchecked")
  @NotNull
  public <C> Collection<GradleProjectStructureNode<C>> getChildren(@NotNull Class<C> clazz) {
    List<GradleProjectStructureNode<C>> result = null;
    for (int i = 0; i < getChildCount(); i++) {
      final GradleProjectStructureNode<?> child = getChildAt(i);
      final Object element = child.getDescriptor().getElement();
      if (!clazz.isInstance(element)) {
        continue;
      }
      if (result == null) {
        result = new ArrayList<GradleProjectStructureNode<C>>();
      }
      result.add((GradleProjectStructureNode<C>)child);
    }
    if (result == null) {
      result = Collections.emptyList();
    }
    return result;
  }
  
  @NotNull
  @Override
  public Iterator<GradleProjectStructureNode<?>> iterator() {
    return new Iterator<GradleProjectStructureNode<?>>() {

      private int i;

      @Override
      public boolean hasNext() {
        return i < getChildCount();
      }

      @Override
      public GradleProjectStructureNode<?> next() {
        return getChildAt(i++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
