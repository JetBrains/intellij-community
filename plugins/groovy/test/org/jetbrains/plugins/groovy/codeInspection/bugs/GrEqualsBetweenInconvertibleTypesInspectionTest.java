// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

public class GrEqualsBetweenInconvertibleTypesInspectionTest extends GrHighlightingTestBase {
  @Override
  public final String getBasePath() {
    return super.getBasePath() + "bugs/";
  }

  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0;
  }

  public void testEqualsBetweenInconvertibleTypes() {
    myFixture.enableInspections(GrEqualsBetweenInconvertibleTypesInspection.class);
    myFixture.testHighlighting(getTestName() + ".groovy");
  }
}
