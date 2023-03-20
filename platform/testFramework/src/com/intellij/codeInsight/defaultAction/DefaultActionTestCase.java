// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public abstract class DefaultActionTestCase extends LightPlatformCodeInsightTestCase {
  protected void performAction(char c) {
    EditorActionManager.getInstance();
    TypedAction action = TypedAction.getInstance();
    action.actionPerformed(getEditor(), c, DataManager.getInstance().getDataContext());
  }
}