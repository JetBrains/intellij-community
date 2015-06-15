/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.junit;

import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;

public class JUnitConfigurationTest extends LightPlatformTestCase {

  public void testSearchScope() throws Exception {

    JUnitConfiguration foo =
      new JUnitConfiguration("foo", getProject(), JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    Element element =
      JDOMUtil.loadDocument("<configuration default=\"false\" name=\"DjangoTests (1.6)\" type=\"JUnit\" factoryName=\"JUnit\">\n" +
                            "    <option name=\"TEST_SEARCH_SCOPE\">\n" +
                            "      <value defaultName=\"moduleWithDependencies\" />\n" +
                            "    </option>\n" +
                            "  </configuration>").getRootElement();
    foo.readExternal(element);
    assertEquals(TestSearchScope.MODULE_WITH_DEPENDENCIES, foo.getPersistentData().getScope());
  }
}
