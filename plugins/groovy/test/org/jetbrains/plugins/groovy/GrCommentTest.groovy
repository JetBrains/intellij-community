/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class GrCommentTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    TestUtils.testDataPath + 'grComment/'
  }

  void testLine() {
    lineTest('''\
<caret>print 2
''', '''\
//print 2
<caret>''')
  }


  void testUncommentLine() {
    lineTest('''\
//<caret>print 2
''', '''\
print 2
<caret>''')
  }


  void testBlock0() {
    blockTest('''\
<selection>print 2</selection>
''', '''\
<selection>/*print 2*/</selection>
''')
  }

  void testUncommentBlock0() {
    blockTest('''\
<selection>/*print 2*/</selection>
''', '''\
<selection>print 2</selection>
''')
  }

  void testBlock1() {
    blockTest('''\
<selection>print 2
</selection>
''', '''\
<selection>/*
print 2
*/
</selection>
''')
  }

  void testUncommentBlock1() {
    blockTest('''\
<selection>/*
print 2
*/
</selection>
''', '''\
<selection>print 2
</selection>
''')
  }

  void lineTest(String before, String after) {
    doTest(before, after, new CommentByLineCommentAction())
  }

  void blockTest(String before, String after) {
    doTest(before, after, new CommentByBlockCommentAction())
  }

  private void doTest(@NotNull String before, @NotNull String after, final AnAction action) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before)
    final DataContext dataContext = DataManager.instance.dataContextFromFocus.result
    action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", dataContext));
    myFixture.checkResult(after)
  }
}
