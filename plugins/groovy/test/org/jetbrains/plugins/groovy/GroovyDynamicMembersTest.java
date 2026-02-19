// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class GroovyDynamicMembersTest extends GroovyLatestTest implements ResolveTest {
  @Before
  public void init_state() {
    DynamicElementSettings methodDescriptor = new DynamicElementSettings();
    methodDescriptor.setContainingClassName(JAVA_LANG_STRING);
    methodDescriptor.setName("foo");
    methodDescriptor.setType("void");
    methodDescriptor.setMethod(true);
    methodDescriptor.setParams(new ArrayList<>());// Collections.emptyList() // https://issues.apache.org/jira/browse/GROOVY-8961

    final DynamicElementSettings propertyDescriptor = new DynamicElementSettings();
    propertyDescriptor.setContainingClassName(JAVA_LANG_STRING);
    propertyDescriptor.setName("foo");

    DynamicManager manager = DynamicManager.getInstance(getProject());
    manager.addMethod(methodDescriptor);
    manager.addProperty(propertyDescriptor);
  }

  @After
  public void clear_state() {
    DynamicManager.getInstance(getProject()).loadState(new DRootElement());
  }

  @Test
  public void resolve_to_dynamic_method() {
    resolveTest("\"hi\".<caret>foo()", PsiMethod.class);
  }

  @Test
  public void resolve_to_dynamic_property() {
    resolveTest("\"hi\".<caret>foo", PsiField.class);
  }
}
