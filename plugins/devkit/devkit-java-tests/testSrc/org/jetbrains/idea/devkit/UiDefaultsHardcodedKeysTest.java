// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit;

import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.idea.devkit.completion.UiDefaultsHardcodedKeys;

import javax.swing.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class UiDefaultsHardcodedKeysTest extends LightPlatformTestCase {
  public void testKeys() {
    Enumeration<Object> keysEnum = UIManager.getDefaults().keys();
    Set<String> keys = new HashSet<>();
    while (keysEnum.hasMoreElements()) {
      String key = keysEnum.nextElement().toString();
      if (key.startsWith("class ")) continue;
      keys.add(key);
    }

    Set<String> absentKeys = new HashSet<>();
    keys.forEach(k -> {
      if (!UiDefaultsHardcodedKeys.ALL_KEYS.contains(k)) {
        absentKeys.add(k);
      }
    });

    assertEmpty(absentKeys);
  }
}
