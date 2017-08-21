/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.siyeh.ig.dataflow.UnnecessaryLocalVariableInspection
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection

/**
 * @author Sergey Evdokimov
 */
class GroovyValidGroupNameTest extends LightCodeInsightFixtureTestCase {

  void testGroupNamesIsSame() {
    def tools = InspectionTestUtil.instantiateTools([UnusedDefInspection, UnnecessaryLocalVariableInspection])
    assert tools.collect { it.groupDisplayName }.toSet().size() == 1 // all tools have same groupDisplayName
  }
}
