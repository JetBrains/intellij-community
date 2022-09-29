/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.memory;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class AnonymousInnerClassMayBeStaticInspectionTest extends LightJavaInspectionTestCase {

  public void testAnonymousInnerClassMayBeStatic() { doTest(); }

  public void testAnonymousInnerClassMayBeStaticInsideInterface() {
    doTest();
    String message = InspectionGadgetsBundle.message("anonymous.inner.may.be.named.static.inner.class.quickfix");
    final IntentionAction intention = myFixture.getAvailableIntention(message);
    assertNotNull(intention);
    String text = myFixture.getIntentionPreviewText(intention);
    assertEquals("""
                   interface A {
                       default void sample() {
                           Thread thread = new Thread(new MyRunnable());
                       }
                                      
                       class MyRunnable implements Runnable {
                           @Override
                           public void run() {
                           }
                       }
                   }""", text);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AnonymousInnerClassMayBeStaticInspection();
  }
}