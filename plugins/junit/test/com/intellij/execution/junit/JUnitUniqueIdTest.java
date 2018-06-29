// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.testframework.sm.FileUrlProvider;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JUnitUniqueIdTest extends LightCodeInsightFixtureTestCase {
  public void testValidateUniqueId() {
    PsiFile file = myFixture.addFileToProject("some.txt", "");
    SMTestProxy proxy = new SMTestProxy("test1", false, "file://" + file.getVirtualFile().getPath());
    proxy.setLocator(new FileUrlProvider());
    proxy.putUserData(SMTestProxy.NODE_ID, "nodeId");
    assertEquals("nodeId", TestUniqueId.getEffectiveNodeId(proxy, getProject(), GlobalSearchScope.projectScope(getProject())));
  }
}
