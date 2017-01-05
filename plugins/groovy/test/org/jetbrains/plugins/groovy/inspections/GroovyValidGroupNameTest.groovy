/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection
import com.siyeh.ig.dataflow.UnnecessaryLocalVariableInspection

/**
 * @author Sergey Evdokimov
 */
class GroovyValidGroupNameTest extends LightCodeInsightFixtureTestCase {

  void testGroupNamesIsSame() {
    assert new UnusedDefInspection().getGroupDisplayName() == new UnnecessaryLocalVariableInspection().getGroupDisplayName()
  }

}
