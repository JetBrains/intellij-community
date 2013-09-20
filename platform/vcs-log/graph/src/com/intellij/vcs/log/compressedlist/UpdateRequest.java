package com.intellij.vcs.log.compressedlist;

/**
 * @author erokhins
 */
public class UpdateRequest {
  public static UpdateRequest ID_UpdateRequest = new UpdateRequest(0, 0, 1);

  /**
   * This class describe replace in list or another ordered set's
   * Elements [from, to] will be remove, and add addedElementCount new's element in this interval
   * Elements from and to should be exist in list.
   * Elements from - 1, to + 1, if they exist, shouldn't change after this replace
   * <p/>
   * Example:
   * UpdateRequest(1, 3, 2)
   * before:   10, 20, 30, 40, 50, ...
   * after:    10, 20,  1,  2, 40, 50, ...
   */

  public static UpdateRequest buildFromToInterval(int oldFrom, int oldTo, int newFrom, int newTo) {
    if (oldFrom != newFrom || oldFrom > oldTo || newFrom > newTo) {
      throw new IllegalArgumentException("oldFrom: " + oldFrom + ", oldTo: " + oldTo +
                                         ", newFrom: " + newFrom + ", newTo: " + newTo);
    }
    return new UpdateRequest(oldFrom, oldTo, newTo - newFrom + 1);
  }

  private final int from;
  private final int to;
  private final int addedElementCount;

  public UpdateRequest(int from, int to, int addedElementCount) {
    if (from < 0 || from > to || addedElementCount < 0) {
      throw new IllegalArgumentException("from: " + from + "to: " + to +
                                         "addedElementCount: " + addedElementCount);
    }
    this.from = from;
    this.to = to;
    this.addedElementCount = addedElementCount;
  }

  public int from() {
    return from;
  }

  public int to() {
    return to;
  }

  public int addedElementCount() {
    return addedElementCount;
  }

  public int removedElementCount() {
    return to - from + 1;
  }

  @Override
  public String toString() {
    return "UpdateRequest{" +
           "from=" + from +
           ", to=" + to +
           ", addedElementCount=" + addedElementCount +
           '}';
  }
}
