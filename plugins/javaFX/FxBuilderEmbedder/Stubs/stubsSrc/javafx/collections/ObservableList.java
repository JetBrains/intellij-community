package javafx.collections;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public interface ObservableList<E> extends List<E> {
  public boolean addAll(E... elements);
}