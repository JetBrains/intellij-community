package com.intellij.openapi.vcs.changes;

public interface ChangesViewPreview {
  void updatePreview(boolean fromModelRefresh);

  void setAllowExcludeFromCommit(boolean value);

  void setDiffPreviewVisible(boolean isVisible);
}
