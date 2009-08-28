package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;

public interface DiffVersionComponent {
  void addDisposable(Disposable disposable);
  void removeContent();
}
