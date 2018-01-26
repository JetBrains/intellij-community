/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.resources;

import com.intellij.codeInspection.resources.ImplicitResourceCloser;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiVariable;
import com.intellij.testFramework.PlatformTestUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ImplicitCloserTest extends LightInspectionTestCase {
  private static final ImplicitResourceCloser NAME_RESOURCE_CLOSER = new ImplicitResourceCloser() {
    @Override
    public boolean isSafelyClosed(@NotNull PsiVariable variable) {
      return "closed".equals(variable.getName());
    }
  };

  public void testImplicitCloser() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X {\n" +
           "  private static void example(int a) throws IOException {\n" +
           "    FileInputStream closed = new FileInputStream(\"file1\");\n" +
           "    FileInputStream another = new <warning descr=\"'FileInputStream' used without 'try'-with-resources statement\">FileInputStream</warning>(\"file2\");\n" +
           "  }\n" +
           "}");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), ImplicitResourceCloser.EP_NAME, NAME_RESOURCE_CLOSER, getTestRootDisposable());
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new AutoCloseableResourceInspection();
  }


}
