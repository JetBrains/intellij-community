package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.*;

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

  private final Set<GradleProjectStructureChange> myConflictChanges = new HashSet<GradleProjectStructureChange>();
  private final List<Listener>                    myListeners       = ContainerUtil.createEmptyCOWList();

  @NotNull private final Comparator<GradleProjectStructureNode<?>> myComparator;
  @NotNull private final GradleProjectStructureNodeDescriptor<T>   myDescriptor;
  
  private boolean mySkipNotification;

  /**
   * Creates new <code>GradleProjectStructureNode</code> object with the given descriptor and 'compare-by-name' comparator.
   * 
   * @param descriptor  target node descriptor to use within the current node
   */
  public GradleProjectStructureNode(@NotNull GradleProjectStructureNodeDescriptor<T> descriptor) {
    this(descriptor, new Comparator<GradleProjectStructureNode<?>>() {
      @Override
      public int compare(GradleProjectStructureNode<?> o1, GradleProjectStructureNode<?> o2) {
        return o1.getDescriptor().getName().compareTo(o2.getDescriptor().getName());
      }
    });
  }

  /**
   * Creates new <code>GradleProjectStructureNode</code> object with the given descriptor and comparator to use for organising child nodes.
   * 
   * @param descriptor  target node descriptor to use within the current node
   * @param comparator  comparator to use for organising child nodes of the current node
   */
  public GradleProjectStructureNode(@NotNull GradleProjectStructureNodeDescriptor<T> descriptor,
                                    @NotNull Comparator<GradleProjectStructureNode<?>> comparator)
  {
    super(descriptor);
    myDescriptor = descriptor;
    myComparator = comparator;
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
      if (myComparator.compare((GradleProjectStructureNode<?>)newChild, node) <= 0) {
        insert(newChild, i); // Assuming that the node listeners are notified during the nested call.
        return;
      }
    }
    super.add(newChild); // Assuming that the node listeners are notified during the nested call to 'insert()'.
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
    boolean b = mySkipNotification;
    mySkipNotification = true;
    final int index = getIndex(aChild);
    try {
      super.remove(aChild);
    }
    finally {
      mySkipNotification = b;
    }
    onNodeRemoved((GradleProjectStructureNode<?>)aChild, index);
  }

  /**
   * Asks current node to ensure that given child node is at the 'right position' (according to the {@link #myComparator}.
   * <p/>
   * Does nothing if given node is not a child of the current node.
   * 
   * @param child  target child node
   * @return       <code>true</code> if child position was changed; <code>false</code> otherwise
   */
  public boolean correctChildPositionIfNecessary(@NotNull GradleProjectStructureNode<?> child) {
    int currentPosition = -1;
    int desiredPosition = -1;
    for (int i = 0; i < getChildCount(); i++) {
      GradleProjectStructureNode<?> node = getChildAt(i);
      if (node == child) {
        currentPosition = i;
        continue;
      }
      if (desiredPosition < 0 && myComparator.compare(child, node) <= 0) {
        desiredPosition = i;
        if (currentPosition >= 0) {
          break;
        }
      }
    }
    if (currentPosition < 0) {
      // Given node is not a child of the current node.
      return false;
    }
    if (desiredPosition < 0) {
      desiredPosition = getChildCount();
    }
    if (currentPosition < desiredPosition) {
      desiredPosition--;
    }
    if (currentPosition == desiredPosition) {
      return false;
    }
    remove(currentPosition);
    insert(child, desiredPosition);
    return true;
  }

  /**
   * Asks current module to ensure that its children are ordered in accordance with the {@link #myComparator pre-configured comparator}.
   */
  @SuppressWarnings("unchecked")
  public void sortChildren() {
    List<GradleProjectStructureNode<?>> nodes = new ArrayList<GradleProjectStructureNode<?>>(children);
    Collections.sort(nodes, myComparator);
    if (nodes.equals(children)) {
      return;
    }
    
    mySkipNotification = true;
    try {
      removeAllChildren();
      for (GradleProjectStructureNode<?> node : nodes) {
        add(node);
      }
    }
    finally {
      mySkipNotification = false;
    }
    int[] indices = new int[nodes.size()];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = i;
    }
    onChildrenChange(indices);
  }

  /**
   * Registers given change within the given node assuming that it is
   * {@link GradleTextAttributes#CHANGE_CONFLICT 'conflict change'}. We need to track number of such changes per-node because
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
    if (myConflictChanges.size() != 1) {
      return;
    }
    final TextAttributesKey key = myDescriptor.getAttributes();
    boolean localNode = key == GradleTextAttributes.GRADLE_LOCAL_CHANGE || key == GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
    if (!localNode) {
      myDescriptor.setAttributes(GradleTextAttributes.CHANGE_CONFLICT);
      onNodeChanged(this);
    }
  }

  @NotNull
  public Set<GradleProjectStructureChange> getConflictChanges() {
    return myConflictChanges;
  }

  /**
   * Performs reverse operation to {@link #addConflictChange(GradleProjectStructureChange)}.
   * 
   * @param change  conflict change to de-register from the current node
   */
  public void removeConflictChange(@NotNull GradleProjectStructureChange change) {
    myConflictChanges.remove(change);
    if (myConflictChanges.isEmpty()) {
      myDescriptor.setAttributes(GradleTextAttributes.NO_CHANGE);
      onNodeChanged(this);
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

  public void setAttributes(@NotNull TextAttributesKey key) {
    myDescriptor.setAttributes(key);
    final GradleProjectStructureNode<?> parent = getParent();
    if (parent == null) {
      onNodeChanged(this);
      return;
    }
    boolean positionChanged = parent.correctChildPositionIfNecessary(this);
    if (!positionChanged) {
      onNodeChanged(this);
    }
  }
  
  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  private void onNodeAdded(@NotNull GradleProjectStructureNode<?> node, int index) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeAdded(node, index);
    }
  }

  private void onNodeRemoved(@NotNull GradleProjectStructureNode<?> node, int removedChildIndex) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeRemoved(this, node, removedChildIndex);
    }
  }

  private void onNodeChanged(@NotNull GradleProjectStructureNode<?> node) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeChanged(node);
    }
  }

  private void onChildrenChange(@NotNull int[] indices) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeChildrenChanged(this, indices);
    }
  }
  
  public interface Listener {
    void onNodeAdded(@NotNull GradleProjectStructureNode<?> node, int index);
    void onNodeRemoved(@NotNull GradleProjectStructureNode<?> parent,
                       @NotNull GradleProjectStructureNode<?> removedChild,
                       int removedChildIndex);
    void onNodeChanged(@NotNull GradleProjectStructureNode<?> node);
    void onNodeChildrenChanged(@NotNull GradleProjectStructureNode<?> parent, int[] childIndices);
  }
}
