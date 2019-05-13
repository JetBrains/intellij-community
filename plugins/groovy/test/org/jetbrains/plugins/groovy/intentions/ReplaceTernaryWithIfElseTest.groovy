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
package org.jetbrains.plugins.groovy.intentions

/**
 * @author Andreas Arledal
 */
class ReplaceTernaryWithIfElseTest extends GrIntentionTestCase {
  ReplaceTernaryWithIfElseTest() {
    super(GroovyIntentionsBundle.message("replace.ternary.with.if.else.intention.name"))
  }

//  public void testDoNotTriggerOnIncompleteIf() {
//    doAntiTest '''
//i<caret>f () {
//  succes
//} else {
//  no_succes
//}
//''', intentionName
//
//  }

  void testDoNotTriggerOnIncompleteTernary() {
    doAntiTest '''
return aaa ? <caret>bbb
'''
  }


  void testSimpleCondition() {

    doTextTest '''
return aaa <caret>? bbb : ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  void testCaretAfterQuestionMark() {

    doTextTest '''
return aaa ?<caret> bbb : ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  void testCaretInfrontOfConditional() {

    doTextTest '''
return <caret>aaa ? bbb : ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  void testCaretInfrontOfElse() {

    doTextTest '''
return aaa ? bbb <caret>: ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  void testCaretAfterElse() {

    doTextTest '''
return aaa ? bbb :<caret> ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  void testCaretBeforeElseReturn() {

    doTextTest '''
return aaa ? bbb : <caret>ccc
''', '''\
if (aaa)<caret> {
    return bbb
} else {
    return ccc
}
'''
  }

  void testCaretBeforeReturnStatement() {

    doTextTest '''
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
