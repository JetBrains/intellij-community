/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions

/**
 * @author Andreas Arledal
 */
class ReplaceTernaryWithIfElseTest extends GrIntentionTestCase {

  String intentionName = GroovyIntentionsBundle.message("replace.ternary.with.if.else.intention.name")

//  public void testDoNotTriggerOnIncompleteIf() throws Exception {
//    doAntiTest '''
//i<caret>f () {
//  succes
//} else {
//  no_succes
//}
//''', intentionName
//
//  }

  public void testDoNotTriggerOnIncompleteTernary() throws Exception {
    doAntiTest '''
return aaa ? <caret>bbb
''', intentionName
  }

  private void doTest(String before, String after) {

    doTextTest before, intentionName, after
  }

  public void testSimpleCondition() throws Exception {

    doTest '''
return aaa <caret>? bbb : ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  public void testCaretAfterQuestionMark() throws Exception {

    doTest '''
return aaa ?<caret> bbb : ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  public void testCaretInfrontOfConditional() throws Exception {

    doTest '''
return <caret>aaa ? bbb : ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  public void testCaretInfrontOfElse() throws Exception {

    doTest '''
return aaa ? bbb <caret>: ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  public void testCaretAfterElse() throws Exception {

    doTest '''
return aaa ? bbb :<caret> ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  public void testCaretBeforeElseReturn() throws Exception {

    doTest '''
return aaa ? bbb : <caret>ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  public void testCaretBeforeReturnStatement() throws Exception {

    doTest '''
<caret>return aaa ? bbb : ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }


}
