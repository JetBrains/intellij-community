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
package org.jetbrains.plugins.groovy.intentions

import org.jetbrains.plugins.groovy.intentions.control.GrRedundantElseIntention

/**
 * @author Max Medvedev
 */
class GrRedundantElseTest extends GrIntentionTestCase {
  GrRedundantElseTest() {
    super(GrRedundantElseIntention.HINT)
  }

  public void testBlock() {
    doTextTest('''\
def foo() {
    if (cond) {
        print 2
        return
    }
    e<caret>lse {
        print 3
        print 4
    }
}
''', '''\
def foo() {
    if (cond) {
        print 2
        return
    }
    <caret>print 3
        print 4
}
''')
  }

  public void testSingleStatement() throws Exception {
    doTextTest('''\
def foo() {
    if (cond) {
        print 2
        return
    }
    e<caret>lse print 3
}
''', '''\
def foo() {
    if (cond) {
        print 2
        return
    }
    <caret>print 3
}
''')
  }

  public void testInsideIf() throws Exception {
    doTextTest('''\
def foo() {
    if (abc)
        if (cond) {
            print 2
            return
        }
        e<caret>lse print 3
}
''', '''\
def foo() {
    if (abc) {
        if (cond) {
            print 2
            return
        }
        <caret>print 3
    }
}
''')
  }


  public void testInsideFor() throws Exception {
    doTextTest('''\
def foo() {
    for (i in 1..2) {
        if (cond) {
            print 2
            break
        }
        e<caret>lse {
            print 3
        }
    }
}
''', '''\
def foo() {
    for (i in 1..2) {
        if (cond) {
            print 2
            break
        }
        <caret>print 3
    }
}
''')
  }


  public void testInsideFor2() throws Exception {
    doTextTest('''\
def foo() {
    for (i in 1..2) {
        if (cond) {
            print 2
            if (abc) {
                break
            }
            else {
                continue
            }
        }
        e<caret>lse {
            print 3
        }
    }
}
''', '''\
def foo() {
    for (i in 1..2) {
        if (cond) {
            print 2
            if (abc) {
                break
            }
            else {
                continue
            }
        }
        <caret>print 3
    }
}
''')
  }

  public void testInsideFor3() throws Exception {
    doAntiTest('''\
def foo() {
    for (i in 1..2) {
        if (cond) {
            print 2
        }
        e<caret>lse {
            print 3
        }
    }
}
''')
  }

  public void testInsideFor4() throws Exception {
    doTextTest('''\
def foo() {
    for (i in 1..2) {
        if (cond) {
            print 2
            if (abc) {
                break
            }
            else {
                continue
            }
        }
        e<caret>lse {
            print 3
            break
        }
    }
}
''', '''\
def foo() {
    for (i in 1..2) {
        if (cond) {
            print 2
            if (abc) {
                break
            }
            else {
                continue
            }
        }
        <caret>print 3
            break
    }
}
''')
  }

}
