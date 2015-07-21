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
package com.intellij.openapi.util.registry;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class RegistryTest {
  private static final String INTEGER_KEY = "editor.mouseSelectionStateResetDeadZone";

  @Test
  public void testInvalidInteger() {
    int originalValue = Registry.intValue(INTEGER_KEY);
    Registry.get(INTEGER_KEY).setValue("invalidNumber");
    assertEquals(originalValue, Registry.intValue(INTEGER_KEY));
  }
}