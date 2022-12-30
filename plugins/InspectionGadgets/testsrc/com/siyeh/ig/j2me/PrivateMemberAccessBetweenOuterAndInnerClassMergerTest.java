/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;

public class PrivateMemberAccessBetweenOuterAndInnerClassMergerTest extends LightJavaCodeInsightFixtureTestCase {
  private InspectionProfileImpl myProfile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    myProfile = new InspectionProfileImpl("Test", InspectionToolRegistrar.getInstance(), new InspectionProfileImpl("base"));
  }

  @Override
  protected void tearDown() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  public void testMerged() throws IOException, JDOMException {
    final Element in = JDOMUtil.load(
      """
        <profile version="1.0">
          <option name="myName" value="Test" />
          <inspection_tool class="LocalVariableOfConcreteClass" enabled="true" level="WARNING" enabled_by_default="true" />
          <inspection_tool class="InstanceVariableOfConcreteClass" />
          <inspection_tool class="ParameterOfConcreteClass" />
          <inspection_tool class="MethodReturnOfConcreteClass" />
          <inspection_tool class="CastToConcreteClass" />
          <inspection_tool class="StaticVariableOfConcreteClass" />
          <inspection_tool class="InstanceofConcreteClass" />
        </profile>""");
    myProfile.readExternal(in);
    assertTrue(myProfile.getToolsOrNull("UseOfConcreteClass", null).isEnabled());
  }
}
