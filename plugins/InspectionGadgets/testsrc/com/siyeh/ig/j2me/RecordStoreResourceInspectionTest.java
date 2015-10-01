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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class RecordStoreResourceInspectionTest extends LightInspectionTestCase {

  public void testRecordStoreResource() {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package javax.microedition.rms;" +
      "public class RecordStore {" +
      "  public byte[] getRecord(int recordId) {" +
      "    return null;" +
      "  }" +
      "  public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary) {" +
      "    return null;" +
      "  }" +
      "  public void closeRecordStore() {}" +
      "}"
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final RecordStoreResourceInspection inspection = new RecordStoreResourceInspection();
    inspection.insideTryAllowed = true;
    return inspection;
  }
}