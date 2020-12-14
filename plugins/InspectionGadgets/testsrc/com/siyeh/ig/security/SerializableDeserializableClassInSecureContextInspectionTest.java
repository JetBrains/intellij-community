// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SerializableDeserializableClassInSecureContextInspectionTest extends LightJavaInspectionTestCase {

  public void testSerializableDeserializableClassInSecureContext() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final SerializableDeserializableClassInSecureContextInspection inspection =
      new SerializableDeserializableClassInSecureContextInspection();
    inspection.ignoreAnonymousInnerClasses = true;
    return inspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }
}