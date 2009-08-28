package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.DiffVersionComponent;

public class ContentDocumentListener implements DiffContent.Listener {
  private final DiffVersionComponent myDiffComponent;

  private ContentDocumentListener(DiffVersionComponent diffComponent) {
    myDiffComponent = diffComponent;
  }

  public static void install(final DiffContent content, DiffVersionComponent component) {
    final ContentDocumentListener listener = new ContentDocumentListener(component);
    content.onAssigned(true);
    content.addListener(listener);
    Disposable disposable = new Disposable() {
        public void dispose() {
          content.onAssigned(false);
        }
      };
    component.addDisposable(disposable);
  }

  public void contentInvalid() {
    myDiffComponent.removeContent();
  }
}
