// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author yole
 */
@ApiStatus.Internal
public class FileEditorOpenOptions {
  private boolean myCurrentTab = false;
  private boolean myFocusEditor = false;
  private Boolean myPin = null;
  private int myIndex = -1;
  private boolean myExactState = false;
  private boolean myReopeningEditorsOnStartup = false;

  public FileEditorOpenOptions withCurrentTab(boolean current) {
    myCurrentTab = current;
    return this;
  }

  public FileEditorOpenOptions withFocusEditor(boolean focusEditor) {
    myFocusEditor = focusEditor;
    return this;
  }

  public FileEditorOpenOptions withPin(Boolean pin) {
    myPin = pin;
    return this;
  }

  public FileEditorOpenOptions withIndex(int index) {
    myIndex = index;
    return this;
  }

  public FileEditorOpenOptions withExactState() {
    myExactState = true;
    return this;
  }

  public FileEditorOpenOptions withReopeningEditorsOnStartup() {
    myReopeningEditorsOnStartup = true;
    return this;
  }

  public boolean isCurrentTab() {
    return myCurrentTab;
  }

  public boolean isFocusEditor() {
    return myFocusEditor;
  }

  public Boolean getPin() {
    return myPin;
  }

  public int getIndex() {
    return myIndex;
  }

  public boolean isExactState() {
    return myExactState;
  }

  public boolean isReopeningEditorsOnStartup() {
    return myReopeningEditorsOnStartup;
  }
}
