// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;

public class JUnitConfigurationTest extends LightPlatformTestCase {
  public void testSearchScope() throws Exception {
    JUnitConfiguration foo = new JUnitConfiguration("foo", getProject());
    Element element = JDOMUtil.load("""
                                      <configuration default="false" name="DjangoTests (1.6)" type="JUnit" factoryName="JUnit">
                                          <option name="TEST_SEARCH_SCOPE">
                                            <value defaultName="moduleWithDependencies" />
                                          </option>
                                        </configuration>""");
    foo.readExternal(element);
    assertEquals(TestSearchScope.MODULE_WITH_DEPENDENCIES, foo.getPersistentData().getScope());
  }
}
