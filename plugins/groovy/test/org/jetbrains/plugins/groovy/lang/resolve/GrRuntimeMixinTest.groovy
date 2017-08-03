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
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

@CompileStatic
class GrRuntimeMixinTest extends GrHighlightingTestBase {

  void 'test is not unnecessarily qualified'() {
    testHighlighting '''\
class SomeCategoryClass {
    static Integer addAmount(Integer self, int amount) { self + amount }
}
Integer.mixin(SomeCategoryClass)
assert 1.addAmount(1) == 2
''', GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection, UnnecessaryQualifiedReferenceInspection
  }
}
