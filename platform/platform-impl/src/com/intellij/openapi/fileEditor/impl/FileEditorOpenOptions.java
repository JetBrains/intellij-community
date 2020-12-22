// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author yole
 */
@ApiStatus.Internal
public final class FileEditorOpenOptions {
  private boolean myCurrentTab = false;
  private boolean myFocusEditor = false;
  private Boolean myPin = null;
  private int myIndex = -1;
  private boolean myExactState = false;
  private boolean myReopeningEditorsOnStartup = false;

  FileEditorOpenOptions withCurrentTab(boolean current) {
    myCurrentTab = current;
    return this;
  }

  FileEditorOpenOptions withFocusEditor(boolean focusEditor) {
    myFocusEditor = focusEditor;
    return this;
  }

  FileEditorOpenOptions withPin(Boolean pin) {
    myPin = pin;
    return this;
  }

  FileEditorOpenOptions withIndex(int index) {
    myIndex = index;
    return this;
  }

  FileEditorOpenOptions withExactState() {
    myExactState = true;
    return this;
  }

  FileEditorOpenOptions withReopeningEditorsOnStartup() {
    myReopeningEditorsOnStartup = true;
    return this;
  }

  boolean isCurrentTab() {
    return myCurrentTab;
  }

  public boolean isFocusEditor() {
    return myFocusEditor;
  }

  Boolean getPin() {
    return myPin;
  }

  int getIndex() {
    return myIndex;
  }

  boolean isExactState() {
    return myExactState;
  }

  boolean isReopeningEditorsOnStartup() {
    return myReopeningEditorsOnStartup;
  }
}
