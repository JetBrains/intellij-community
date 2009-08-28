package com.intellij.openapi.editor.ex;

import java.util.EventListener;

public interface ErrorStripeListener extends EventListener {
  void errorMarkerClicked(ErrorStripeEvent e);
}
