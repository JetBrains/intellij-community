// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.idea.Bombed
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrPermitsClauseInspection
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
class GroovySealedHighlightingTest  extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK

  void 'test permits without sealed'() {
    highlightingTest '''
class A <error>permits</error> B {}
class B extends A {}
'''
  }

  void 'test exclusiveness'() {
    highlightingTest '''
<error>sealed</error> <error>non-sealed</error> class A {}
class B extends A {}'''
  }

  void 'test sealed enum'() {
    highlightingTest '''
<error>sealed</error> enum A {}'''
  }

  void 'test sealed class without permitted subclasses'() {
    highlightingTest '''
<error>sealed</error> interface A {}'''
  }

  void 'test permits with non-extending reference'() {
    highlightingTest '''
sealed class A permits <error>B</error> {}
class B {}
''', GrPermitsClauseInspection
  }

  void 'test extending without permission'() {
    highlightingTest '''
sealed class A permits B {}
class B extends A {}
class C extends <error>A</error> {}'''
  }

  void 'test implementing without permission'() {
    highlightingTest '''
sealed trait A permits B, C {}
class B implements A {}
interface C extends A {}
class D implements <error>A</error> {}'''
  }

  void 'test non-sealed class without sealed superclass'() {
    highlightingTest '''
<error>non-sealed</error> class A {}
'''
  }

  void 'test sealed annotation'() {
    highlightingTest '''
import groovy.transform.Sealed

<error>@Sealed</error>
<error>non-sealed</error> class A {}
'''
  }


  @Bombed(year = 2021, month = 10, day = 1, user = "knisht", description = "Waiting for Groovy 4.0.0-beta-1 release")
  void 'test mention class in annotation'() {
    highlightingTest '''
import groovy.transform.Sealed


@Sealed(permittedSubclasses = [B])
class A {}

class B extends A {}
class C extends <error>A</error> {}'''
  }

  @Bombed(year = 2021, month = 10, day = 1, user = "knisht", description = "Waiting for Groovy 4.0.0-beta-1 release")
  void 'test non-extending class in annotation'() {
    highlightingTest '''
import groovy.transform.Sealed


@Sealed(permittedSubclasses = [<error>B</error>])
class A {}

class B {}
'''
  }
}
