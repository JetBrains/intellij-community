// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SerializableHasSerializationMethodsInspectionTest extends LightJavaInspectionTestCase {

  public void testExternalizable() {
    doTest("class Test implements java.io.Externalizable {" +
           "    @Override" +
           "    public void writeExternal(java.io.ObjectOutput out) {" +
           "    }" +
           "    @Override" +
           "    public void readExternal(java.io.ObjectInput in) {" +
           "    }" +
           "}");
  }

  public void testSimple() {
    doTest("class /*Serializable class 'Simple' does not define 'readObject()' or 'writeObject()'*/Simple/**/" +
           " implements java.io.Serializable {}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SerializableHasSerializationMethodsInspection();
  }
}
