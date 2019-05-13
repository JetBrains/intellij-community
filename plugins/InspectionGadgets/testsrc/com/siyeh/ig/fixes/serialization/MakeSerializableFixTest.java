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
package com.siyeh.ig.fixes.serialization;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.serialization.ComparatorNotSerializableInspection;

/**
 * @author Bas Leijdekkers
 */
public class MakeSerializableFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ComparatorNotSerializableInspection());
    myRelativePath = "serialization/comparator";
  }

  public void testExtendsInterface() { doTest(QuickFixBundle.message("add.class.to.extends.list", "ExtendsInterface", "java.io.Serializable")); }
  public void testImplementsClass() { doTest(QuickFixBundle.message("add.interface.to.implements.list", "ImplementsClass", "java.io.Serializable")); }
}
