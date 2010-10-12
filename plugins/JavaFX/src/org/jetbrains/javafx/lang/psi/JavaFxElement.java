package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.NavigatablePsiElement;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:21:17
 */
public interface JavaFxElement extends NavigatablePsiElement {
  JavaFxElement[] EMPTY_ARRAY = new JavaFxElement[0];
}
