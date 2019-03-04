// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public abstract class AbstractFieldNamingConventionInspectionTest extends LightInspectionTestCase {
  @Override
  protected Class<? extends InspectionProfileEntry> getInspectionClass() {
    return FieldNamingConventionInspection.class;
  }
}
