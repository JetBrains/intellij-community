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
package org.jetbrains.plugins.groovy.lang.psi

import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl

/**
 * Created by Max Medvedev on 12/4/13
 */
class GrStubAstSwitchTest extends LightCodeInsightFixtureTestCase {
  void testDontLoadContentWhenProcessingImports() {
    GroovyFileImpl file = (GroovyFileImpl) myFixture.addFileToProject("A.groovy", """
import java.util.concurrent.ConcurrentHashMap

class MyMap extends ConcurrentHashMap {}
class B extends ConcurrentHashMap {
  void foo() {
    print 4
  }
}
""")
    assert !file.contentsLoaded
    PsiClass bClass = file.classes[1]
    assert !file.contentsLoaded

    def fooMethod = bClass.methods[0]
    assert !file.contentsLoaded

    fooMethod.findDeepestSuperMethods()
    assert !file.contentsLoaded
  }
}
