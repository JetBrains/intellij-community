// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class AccessToStaticFieldLockedOnInstanceInspectionTest extends LightJavaInspectionTestCase {

  public void testAccessToStaticFieldLockedOnInstanceData() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final AccessToStaticFieldLockedOnInstanceInspection inspection = new AccessToStaticFieldLockedOnInstanceInspection();
    inspection.ignoredClasses.add("java.util.List");
    return inspection;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/threading/access_to_static_field_locked_on_instance_data";
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package javax.annotation.concurrent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ThreadSafe {
}"""
    };
  }
}