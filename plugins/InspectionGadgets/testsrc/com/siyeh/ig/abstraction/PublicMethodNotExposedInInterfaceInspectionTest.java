// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PublicMethodNotExposedInInterfaceInspectionTest extends LightJavaInspectionTestCase {

  public void testPublicMethodNotExposedInInterface() { doTest(); }
  public void testPublicMethodNotExposedInInterfaceEnum() { doTest(); }
  public void testRecursiveError() {
    final GlobalSearchScope scope = ProjectScope.getLibrariesScope(getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    assertNotNull(facade.findClass("java.io.DataInput", scope));
    assertNotNull(facade.findClass("java.io.DataInputStream", scope));
    assertNotNull(facade.findClass("java.io.FilterInputStream", scope));
    assertNotNull(facade.findClass("java.io.InputStream", scope));
    // without these classes present the following test will always succeed
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new PublicMethodNotExposedInInterfaceInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }
}
