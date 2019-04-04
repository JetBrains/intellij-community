// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public abstract class AbstractInplaceIntroduceTest extends LightPlatformCodeInsightTestCase {

  protected abstract String getBasePath();

  protected void doTestEscape() {
    doTestEscape(null);
  }

  protected void doTestEscape(Pass<AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final AbstractInplaceIntroducer introducer = invokeRefactoring();
      if (pass != null) {
        pass.pass(introducer);
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(true);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  protected abstract String getExtension();

  protected void doTest(final Pass<AbstractInplaceIntroducer> pass)  {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final AbstractInplaceIntroducer introducer = invokeRefactoring();
      pass.pass(introducer);
      TemplateState state = TemplateManagerImpl.getTemplateState(InjectedLanguageUtil.getTopLevelEditor(getEditor()));
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  protected abstract AbstractInplaceIntroducer invokeRefactoring();
}
