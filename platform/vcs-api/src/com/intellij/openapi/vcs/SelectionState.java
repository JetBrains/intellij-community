package com.intellij.openapi.vcs;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 30.05.2010
 * Time: 11:02:36
 * To change this template use File | Settings | File Templates.
 */
public interface SelectionState<T> {
  SelectionResult<T> getSelected();

  SelectionResult<T> getUnselected();

  boolean isSelected(T t);
}
