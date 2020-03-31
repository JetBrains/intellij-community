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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
public class AtomicFieldUpdaterNotStaticFinalInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("import java.util.concurrent.atomic.AtomicLongFieldUpdater;" +
           "class A {" +
           "  private volatile long l = 0;" +
           "  private AtomicLongFieldUpdater /*AtomicLongFieldUpdater field 'updater' is not declared 'static final'*/updater/**/ = AtomicLongFieldUpdater.newUpdater(A.class, \"l\");" +
           "}");
  }

  public void testNoWarning() {
    doTest("import java.util.concurrent.atomic.AtomicLongFieldUpdater;" +
           "class A {" +
           "  private volatile long l = 0;" +
           "  private static final AtomicLongFieldUpdater updater = AtomicLongFieldUpdater.newUpdater(A.class, \"l\");" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AtomicFieldUpdaterNotStaticFinalInspection();
  }
}