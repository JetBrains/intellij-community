package com.intellij.tasks;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class CustomTaskState {
  private String myId = "";
  private String myPresentableName = "";
  private boolean myPredefined;

  /**
   * For serialization purposes only.
   */
  public CustomTaskState() {
  }

  public CustomTaskState(@NotNull String id, @NotNull String name) {
    myId = id;
    myPresentableName = name;
  }

  /**
   * Unique ID (e.g. number or unique name) of this state that can be used later to update state of an issue
   * in {@link TaskRepository#setTaskState(Task, CustomTaskState)}.
   *
   * @see TaskRepository#setTaskState(Task, CustomTaskState)
   */
  @Attribute("id")
  @NotNull
  public String getId() {
    return myId;
  }

  /**
   * For serialization purposes only.
   */
  public void setId(@NotNull String id) {
    myId = id;
  }

  /**
   * Text that describes this state and will be shown to user in UI (unlike ID it's not necessarily unique).
   */
  @Attribute("name")
  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  /**
   * For serialization purposes only.
   */
  public void setPresentableName(@NotNull String name) {
    myPresentableName = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CustomTaskState)) return false;

    final CustomTaskState state = (CustomTaskState)o;

    return myId.equals(state.myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  /**
   * Creates custom state for which ID is identical to {@link TaskState#name} of the given predefined state and {@link #isPredefined()}
   * returns true. If your repository provides only fixed set of available states, that are one of those described in {@link TaskState},
   * you may find this methods useful to implement {@link TaskRepository#getAvailableTaskStates(Task)}.
   *
   * @return custom task state that represents given predefined state
   * @see #asPredefined()
   * @see #isPredefined()
   * @see TaskRepository#getAvailableTaskStates(Task)
   */
  @NotNull
  public static CustomTaskState fromPredefined(@NotNull TaskState state) {
    final CustomTaskState result = new CustomTaskState(state.name(), state.getPresentableName());
    result.setPredefined(true);
    return result;
  }

  /**
   * Returns corresponding value of {@link TaskState} that has the same name as ID of this state.
   * It's intended to be used for custom states created using {@link #fromPredefined}.
   *
   * @return predefined task state as described or {@code null} if such state doesn't exists or {@link #isPredefined()} returns false
   * @see #isPredefined()
   */
  @Nullable
  public TaskState asPredefined() {
    if (isPredefined()) {
      try {
        return TaskState.valueOf(getId());
      }
      catch (IllegalArgumentException ignored) {
      }
    }
    return null;
  }

  /**
   * Means that this custom state can be directly mapped to legacy {@link TaskState}. If it's false, {@link #asPredefined()} returns {@code null}.
   * It's intended to be used mainly for compatibility with existing repositories.
   *
   * @see #asPredefined()
   */
  @Attribute("predefined")
  public boolean isPredefined() {
    return myPredefined;
  }

  /**
   * For serialization purposes only.
   */
  public void setPredefined(boolean predefined) {
    myPredefined = predefined;
  }

  @Override
  public String toString() {
    return "CustomTaskState(id='" + myId + '\'' + ", name='" + myPresentableName + '\'' + ", predefined=" + myPredefined + ')';
  }
}
