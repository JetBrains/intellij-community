package org.jetbrains.debugger;

import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;

public class ObjectValuePresentation extends XValuePresentation {
  private final String myValue;

  public ObjectValuePresentation(@NotNull String value) {
    myValue = value;
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer) {
    renderer.renderComment(myValue);
  }
}