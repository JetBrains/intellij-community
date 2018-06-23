// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.JdomKt;
import org.jdom.Element;

public class JUnitConfigurationTest extends LightPlatformTestCase {
  public void testSearchScope() throws Exception {
    JUnitConfiguration foo = new JUnitConfiguration("foo", getProject());
    Element element =
      JdomKt.loadElement("<configuration default=\"false\" name=\"DjangoTests (1.6)\" type=\"JUnit\" factoryName=\"JUnit\">\n" +
                         "    <option name=\"TEST_SEARCH_SCOPE\">\n" +
                         "      <value defaultName=\"moduleWithDependencies\" />\n" +
                         "    </option>\n" +
                         "  </configuration>");
    foo.readExternal(element);
    assertEquals(TestSearchScope.MODULE_WITH_DEPENDENCIES, foo.getPersistentData().getScope());
  }
}
