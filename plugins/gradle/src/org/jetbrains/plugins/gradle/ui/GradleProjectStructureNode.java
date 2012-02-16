package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/23/11 3:50 PM
 * @param <T>   type of the target entity {@link GradleProjectStructureNodeDescriptor#getElement() associated} with the current node
 */
public class GradleProjectStructureNode<T extends GradleEntityId> extends DefaultMutableTreeNode
  implements Iterable<GradleProjectStructureNode<?>>
{
  
  public static final Comparator<GradleProjectStructureNode<?>> NODE_COMPARATOR = new Comparator<GradleProjectStructureNode<?>>() {
    @Override
    public int compare(GradleProjectStructureNode<?> n1, GradleProjectStructureNode<?> n2) {
      TextAttributesKey a1 = n1.getDescriptor().getAttributes();
      TextAttributesKey a2 = n2.getDescriptor().getAttributes();
      
      // Put 'gradle-local' nodes at the top.
      if (a1 == GradleTextAttributes.GRADLE_LOCAL_CHANGE && a2 != GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        return -1;
      }
      else if (a1 != GradleTextAttributes.GRADLE_LOCAL_CHANGE && a2 == GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        return 1;
      }
       
      return n1.getDescriptor().getName().compareTo(n2.getDescriptor().getName());
    }
  };

  private final Set<GradleProjectStructureChange> myConflictChanges = new HashSet<GradleProjectStructureChange>();
  private final List<Listener>                    myListeners       = new CopyOnWriteArrayList<Listener>();
  
  private final GradleProjectStructureNodeDescriptor<T> myDescriptor;
  
  public GradleProjectStructureNode(@NotNull GradleProjectStructureNodeDescriptor<T> descriptor) {
    super(descriptor);
    myDescriptor = descriptor;
  }

  @NotNull
  public GradleProjectStructureNodeDescriptor<T> getDescriptor() {
    return myDescriptor;
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
    for (int i = 0; i < getChildCount(); i++) {
      GradleProjectStructureNode<?> node = getChildAt(i);
      if (NODE_COMPARATOR.compare((GradleProjectStructureNode<?>)newChild, node) <= 0) {
        insert(newChild, i);
        onNodeAdded((GradleProjectStructureNode<?>)newChild, i);
        return;
      }
    }
    super.add(newChild);
    onNodeAdded((GradleProjectStructureNode<?>)newChild, getChildCount() - 1);
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    onNodeAdded((GradleProjectStructureNode<?>)newChild, childIndex);
  }

  @Override
  public void remove(int childIndex) {
    final GradleProjectStructureNode<?> child = getChildAt(childIndex);
    super.remove(childIndex);
    onNodeRemoved(child, childIndex);
  }

  @Override
  public void remove(MutableTreeNode aChild) {
    final int index = getIndex(aChild);
    super.remove(aChild);
    onNodeRemoved((GradleProjectStructureNode<?>)aChild, index);
  }

  /**
   * Asks current node to ensure that given child node is at the 'right position' (according to the {@link #NODE_COMPARATOR}.
   * <p/>
   * Does nothing if given node is not a child of the current node.
   * 
   * @param child  target child node
   */
  public void correctChildPositionIfNecessary(@NotNull GradleProjectStructureNode<?> child) {
    int currentPosition = -1;
    int desiredPosition = getChildCount() - 1;
    for (int i = 0; i < getChildCount(); i++) {
      GradleProjectStructureNode<?> node = getChildAt(i);
      if (node == child) {
        currentPosition = i;
        continue;
      }
      if (NODE_COMPARATOR.compare(child, node) <= 0) {
        desiredPosition = i;
      }
    }
    if (currentPosition < 0) {
      // Given node is not a child of the current node.
      return;
    }
    remove(currentPosition);
    insert(child, desiredPosition);
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
  public <C extends GradleEntityId> Collection<GradleProjectStructureNode<C>> getChildren(@NotNull Class<C> clazz) {
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

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  private void onNodeAdded(@NotNull GradleProjectStructureNode<?> node, int index) {
    for (Listener listener : myListeners) {
      listener.onNodeAdded(node, index);
    }
  }

  private void onNodeRemoved(@NotNull GradleProjectStructureNode<?> node, int index) {
    for (Listener listener : myListeners) {
      listener.onNodeRemoved(node, index);
    }
  }
  
  public interface Listener {
    void onNodeAdded(@NotNull GradleProjectStructureNode<?> node, int index);
    void onNodeRemoved(@NotNull GradleProjectStructureNode<?> node, int index);
  }
}
