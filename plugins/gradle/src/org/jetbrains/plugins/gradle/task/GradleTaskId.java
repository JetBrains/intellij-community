package org.jetbrains.plugins.gradle.task;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents id of the task enqueued to Gradle API for execution. 
 *
 * @author Denis Zhdanov
 * @since 11/10/11 9:09 AM
 */
public class GradleTaskId implements Serializable {

  private static final long       serialVersionUID = 1L;
  private static final AtomicLong COUNTER          = new AtomicLong();

  private final GradleTaskType myType;
  private final long myId;

  private GradleTaskId(@NotNull GradleTaskType type, long id) {
    myType = type;
    myId = id;
  }

  /**
   * Allows to retrieve distinct task id object of the given type.
   *
   * @param type  target task type
   * @return      distinct task id object of the given type
   */
  @NotNull
  public static GradleTaskId create(@NotNull GradleTaskType type) {
    return new GradleTaskId(type, COUNTER.getAndIncrement());
  }

  @NotNull
  public GradleTaskType getType() {
    return myType;
  }

  @Override
  public int hashCode() {
    return 31 * myType.hashCode() + (int)(myId ^ (myId >>> 32));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleTaskId that = (GradleTaskId)o;
    return myId == that.myId && myType == that.myType;
  }

  @Override
  public String toString() {
    return myType + ":" + myId;
  }
}
