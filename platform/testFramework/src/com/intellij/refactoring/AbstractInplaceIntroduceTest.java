// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class AbstractInplaceIntroduceTest extends LightPlatformCodeInsightTestCase {
  protected abstract String getBasePath();

  protected void doTestEscape() {
    doTestEscape(null);
  }

  protected void doTestEscape(@Nullable Consumer<? super AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      AbstractInplaceIntroducer introducer = invokeRefactoring();
      if (pass != null) {
        pass.accept(introducer);
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(true);
      UIUtil.dispatchAllInvocationEvents();
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  protected abstract String getExtension();

  protected void doTest(@Nullable Consumer<? super AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      AbstractInplaceIntroducer introducer = invokeRefactoring();
      if (pass != null) {
        pass.accept(introducer);
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(InjectedLanguageEditorUtil.getTopLevelEditor(getEditor()));
      assert state != null;
      state.gotoEnd(false);
      UIUtil.dispatchAllInvocationEvents();
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  protected abstract AbstractInplaceIntroducer invokeRefactoring();
}