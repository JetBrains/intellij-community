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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class MismatchedCollectionQueryUpdateInspectionTest extends LightInspectionTestCase {

  private static final ImplicitUsageProvider TEST_PROVIDER = new ImplicitUsageProvider() {
    @Override
    public boolean isImplicitUsage(PsiElement element) {
      return false;
    }

    @Override
    public boolean isImplicitRead(PsiElement element) {
      return false;
    }

    @Override
    public boolean isImplicitWrite(PsiElement element) {
      return element instanceof PsiField && ((PsiField)element).getName().equals("injected");
    }
  };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), ImplicitUsageProvider.EP_NAME, TEST_PROVIDER, myFixture.getTestRootDisposable());
  }

  public void testMismatchedCollectionQueryUpdate() {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public class HashSet<E> implements Set<E> {" +
      "  public HashSet() {}" +
      "  public HashSet(Collection<? extends E> collection) {}" +
      "}",
      "package java.util.concurrent;" +
      "public interface BlockingDeque<E> {" +
      "  E takeFirst() throws InterruptedException;" +
      "  void putLast(E e) throws InterruptedException;" +
      "}",
      "package java.util.concurrent;" +
      "public class LinkedBlockingDeque<E> implements BlockingDeque {}",
      "package java.lang;" +
      "public class InterruptedException extends Exception {}",
      "package java.util.concurrent;" +
      "public interface BlockingQueue<E> {" +
      "  int drainTo(java.util.Collection<? super E> c);" +
      "}"
    };
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    MismatchedCollectionQueryUpdateInspection inspection = new MismatchedCollectionQueryUpdateInspection();
    inspection.ignoredClasses.add("com.siyeh.igtest.bugs.mismatched_collection_query_update.ConstList");
    return inspection;
  }
}