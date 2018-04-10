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
package org.jetbrains.plugins.groovy.completion

import com.intellij.application.options.CodeStyle
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class GrDocCompletionTest extends GroovyCompletionTestBase {
  @Override
  protected String getBasePath() {
    "${TestUtils.testDataPath}groovy/completion/gdoc"
  }

  void testLinkCompletion() { doBasicTest() }
  void testLinkCompletion1() {
    CodeStyle.getSettings(project).getCustomSettings(GroovyCodeStyleSettings.class).USE_FQ_CLASS_NAMES_IN_JAVADOC = false
    doBasicTest()
  }
}
