package org.jetbrains.intellij.plugins.journey;

import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;

class JourneyUiDataRule implements UiDataRule {
  @Override
  public void uiDataSnapshot(@NotNull DataSink sink, @NotNull DataSnapshot snapshot) {
    // TODO current logic relays on execution com.intellij.uml.UmlFileEditorImpl#uiDataSnapshot before current method
    sink.setNull(PlatformDataKeys.CONTEXT_MENU_LOCATOR);
    sink.setNull(PlatformDataKeys.CONTEXT_MENU_POINT);
  }
}