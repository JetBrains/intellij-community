// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.swing;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.LightProjectTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Assert;
import org.junit.Test;

public final class SwingBuilderTest extends LightProjectTest implements ResolveTest {
  @Override
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_1;
  }

  @Test
  public void swingBuilderMethod() {
    PsiMethod method = resolveTest("new groovy.swing.SwingBuilder().<caret>frame()", PsiMethod.class);
    Assert.assertFalse(method.isPhysical());
  }

  @Test
  public void swingProperty() {
    PsiMethod method = resolveTest("new groovy.swing.SwingBuilder().table(<caret>autoscrolls: false)", PsiMethod.class);
    Assert.assertTrue(PropertyUtil.isSimplePropertySetter(method));
    Assert.assertEquals("javax.swing.JComponent", method.getContainingClass().getQualifiedName());
  }
}
