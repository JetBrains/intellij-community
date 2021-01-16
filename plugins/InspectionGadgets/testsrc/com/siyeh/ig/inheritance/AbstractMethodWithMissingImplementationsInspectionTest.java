// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbstractMethodWithMissingImplementationsInspectionTest extends LightJavaInspectionTestCase {

  public void testAbstractMethodWithMissingImplementations() {
    doTest();
  }

  public void testDuplicateClass() {
    doTest();
  }

  public void testSearchReturnsUnrelatedClass() {
    ClassInheritorsSearch.EP_NAME.getPoint().registerExtension(new QueryExecutor<>() {
      @Override
      public boolean execute(@NotNull ClassInheritorsSearch.SearchParameters p, @NotNull Processor<? super PsiClass> consumer) {
        return ContainerUtil.process(((PsiClassOwner)p.getClassToProcess().getContainingFile()).getClasses(), consumer);
      }
    }, getTestRootDisposable());
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AbstractMethodWithMissingImplementationsInspection();
  }
}
