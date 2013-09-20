package com.intellij.vcs.log.compressedlist;

import com.intellij.util.containers.SLRUCache;
import com.intellij.vcs.log.compressedlist.generator.Generator;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 *         postion is position in CompressedList
 *         index is index positionElement in positionItems
 */
public class RuntimeGenerateCompressedList<T> implements CompressedList<T> {
  private final SLRUCache<Integer, T> cache = new SLRUCache<Integer, T>(200, 200) {

    @NotNull
    @Override
    public T createValue(Integer key) {
      return RuntimeGenerateCompressedList.this.get(key);
    }
  };

  private final Generator<T> generator;
  private final int intervalSave;
  private final List<PositionItem> positionItems = new ArrayList<PositionItem>();
  private int size;

  public RuntimeGenerateCompressedList(Generator<T> generator, int size, int intervalSave) {
    this.generator = generator;
    this.intervalSave = intervalSave;
    this.size = size;
    T firstT = generator.generateFirst();
    positionItems.add(new PositionItem(0, firstT));
    int curPosition = intervalSave;
    T prevT = firstT;
    while (curPosition < size) {
      prevT = generator.generate(prevT, intervalSave);
      positionItems.add(new PositionItem(curPosition, prevT));
      curPosition = curPosition + intervalSave;
    }
  }

  public RuntimeGenerateCompressedList(Generator<T> generator, int size) {
    this(generator, size, 20);
  }

  // returned index k, which is max from k: positionItems.get(k).getPosition() <= position
  private int binarySearch(int position) {
    assert positionItems.size() > 0;
    int x = 0;
    int y = positionItems.size() - 1;
    while (y - x > 1) {
      int z = (x + y) / 2;
      if (positionItems.get(z).getPosition() <= position) {
        x = z;
      }
      else {
        y = z;
      }
    }
    if (positionItems.get(y).getPosition() <= position) {
      return y;
    }
    return x;
  }

  @NotNull
  @Override
  public List<T> getList() {
    return new AbstractList<T>() {
      @Override
      public T get(int index) {
        return cache.get(index);
      }

      @Override
      public int size() {
        return size;
      }
    };
  }


  private void fixPositionsTail(int startIndex, int deltaSize) {
    for (int i = startIndex; i < positionItems.size(); i++) {
      PositionItem positionItem = positionItems.get(i);
      positionItem.setPosition(positionItem.getPosition() + deltaSize);
    }
  }

  private List<PositionItem> regenerateMediate(PositionItem prevSavePositionItem, int downSavePosition) {
    List<PositionItem> mediateSave = new ArrayList<PositionItem>();
    T prevT = prevSavePositionItem.getT();
    int curTPosition = prevSavePositionItem.getPosition() + intervalSave;

    while (curTPosition < downSavePosition - intervalSave) {
      prevT = generator.generate(prevT, intervalSave);
      mediateSave.add(new PositionItem(curTPosition, prevT));
      curTPosition = curTPosition + intervalSave;
    }
    return mediateSave;
  }

  private void checkReplace(UpdateRequest updateRequest) {
    if (updateRequest.to() >= size) {
      throw new IllegalArgumentException("size= " + size + "Bad updateRequest: " + updateRequest);
    }
  }

  @Override
  public void recalculate(@NotNull UpdateRequest updateRequest) {
    if (updateRequest == UpdateRequest.ID_UpdateRequest) {
      return;
    }
    checkReplace(updateRequest);
    cache.clear();
    int deltaSize = updateRequest.addedElementCount() - updateRequest.removedElementCount();

    int upSaveIndex = binarySearch(updateRequest.from());
    if (upSaveIndex > 0) { // update started from updateRequest.from()
      upSaveIndex--;
    }
    else {
      positionItems.set(0, new PositionItem(0, generator.generateFirst()));
    }
    PositionItem upSavePositionItem = positionItems.get(upSaveIndex);

    int downSaveIndex = upSaveIndex;
    while (downSaveIndex < positionItems.size() && positionItems.get(downSaveIndex).getPosition() <= updateRequest.to()) {
      downSaveIndex++;
    }

    size = size + deltaSize;
    fixPositionsTail(downSaveIndex, deltaSize);

    int downSavePosition = size;
    if (downSaveIndex < positionItems.size()) {
      downSavePosition = positionItems.get(downSaveIndex).getPosition();
    }
    List<PositionItem> mediate = regenerateMediate(upSavePositionItem, downSavePosition);

    positionItems.subList(upSaveIndex + 1, downSaveIndex).clear();
    positionItems.addAll(upSaveIndex + 1, mediate);
  }

  @NotNull
  private T get(int position) {
    if (position < 0 || position >= size) {
      throw new IllegalArgumentException();
    }
    int saveIndex = binarySearch(position);
    final PositionItem positionItem = positionItems.get(saveIndex);
    assert position >= positionItem.getPosition();
    return generator.generate(positionItem.getT(), position - positionItem.getPosition());
  }

  private class PositionItem {
    private int position;
    private final T t;

    private PositionItem(int position, @NotNull T t) {
      this.position = position;
      this.t = t;
    }

    public int getPosition() {
      return position;
    }

    @NotNull
    public T getT() {
      return t;
    }

    public void setPosition(int position) {
      this.position = position;
    }
  }

}
