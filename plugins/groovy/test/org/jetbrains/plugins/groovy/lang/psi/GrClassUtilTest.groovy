/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.util.ClassUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class GrClassUtilTest extends LightCodeInsightFixtureTestCase {
  public void testFindClassByName() {
    myFixture.configureByText("a.groovy", '''\
      public class InnerClasses {
        static class Bar { }
        static class Bar$ {
          static class $Foo { }
        }
      }'''.stripIndent())

    assertNotNull(ClassUtil.findPsiClassByJVMName(psiManager, 'InnerClasses$Bar'))
    assertNotNull(ClassUtil.findPsiClassByJVMName(psiManager, 'InnerClasses$Bar$'))
    assertNotNull(ClassUtil.findPsiClassByJVMName(psiManager, 'InnerClasses$Bar$$$Foo'))
  }
}