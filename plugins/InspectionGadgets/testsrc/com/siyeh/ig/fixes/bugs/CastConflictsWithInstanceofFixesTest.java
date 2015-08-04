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
package com.siyeh.ig.fixes.bugs;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.CastConflictsWithInstanceofInspection;

/**
 * @author anna
 * @since 16-Jun-2009
 */
public class CastConflictsWithInstanceofFixesTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CastConflictsWithInstanceofInspection());
  }

  public void testReplaceCastInDeclaration() {
    doTest("replaceCastInDeclaration", "Replace 'Integer' with 'String' in cast");
  }

  public void testReplaceInstanceOf() {
    doTest("replaceInstanceOf", "Replace 'String' with 'Integer' in instanceof");
  }

  public void testReplaceCastMethodInDeclaration() {
    doTest("replaceCastMethodInDeclaration", "Replace 'Integer' with 'String' in cast");
  }

  public void testReplaceInstanceofMethod() {
    doTest("replaceInstanceofMethod", "Replace 'String' with 'Integer' in instanceof");
  }

  public void testIgnoreUncheckedCastToTypeParameter() {
    assertQuickfixNotAvailable("Replace 'E' with 'String' in cast");
  }

  public void testReplaceInstanceofInFront() {
    doTest("replaceInstanceofInFront", "Replace 'String' with 'Integer' in instanceof");
  }

  @Override
  protected String getRelativePath() {
    return "bugs/castConflicts";
  }
}
