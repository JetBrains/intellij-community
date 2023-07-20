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

import com.intellij.openapi.util.Pair;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class RegistryTest {
  private static final String INTEGER_KEY = "editor.mouseSelectionStateResetDeadZone";

  @After
  public void tearDown(){
    Registry.setValueChangeListener(null);
  }


  @Test
  public void testInvalidInteger() {
    int originalValue = Registry.intValue(INTEGER_KEY);
    Registry.get(INTEGER_KEY).setValue("invalidNumber");
    assertEquals(originalValue, Registry.intValue(INTEGER_KEY));
  }

  @Test
  public void checkListenerCalledOnReload() {
    Registry.getInstance().reset();
    String key = "first.key1";
    String firstVal = "first.value1";
    String secondValue = "second.value1";
    final AtomicReference<Pair<String, String>> lastChangedPair = new AtomicReference<>();
    Registry.setValueChangeListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        if (!value.getKey().equals(key))
          return;
        lastChangedPair.set(Pair.create(value.getKey(), value.asString()));
      }
    });

    Map<String, String> firstMap = new LinkedHashMap<>();
    firstMap.put(key, firstVal);
    Registry.loadState(registryElementFromMap(firstMap), null);
    assertEquals(firstVal, Registry.get(key).asString());
    assertNull(lastChangedPair.get());

    Map<String, String> secondMap = new LinkedHashMap<>();
    secondMap.put(key, secondValue);
    Registry.loadState(registryElementFromMap(secondMap), null);
    assertEquals(secondValue, Registry.get(key).asString());
    assertEquals(Pair.create(key, secondValue), lastChangedPair.get());
  }

  @Test
  public void checkEarlyAccessOverwriteOnReload() {
    Registry.getInstance().reset();
    String key = "first.key2";
    String firstVal = "first.value2";

    String eaKey = "ea.first.key";
    String eaFirstVal = "ea.first.value";
    String eaSecondValue = "ea.second.value";
    final AtomicReference<Pair<String, String>> lastChangedPair = new AtomicReference<>();
    Registry.setValueChangeListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        if (!(value.getKey().equals(key) || value.getKey().equals(eaKey)))
          return;
        lastChangedPair.set(Pair.create(value.getKey(), value.asString()));
      }
    });

    Registry.loadState(registryElementFromMap(Map.of(key, firstVal)), Map.of(eaKey, eaFirstVal));
    assertEquals(firstVal, Registry.get(key).asString());
    assertEquals(eaFirstVal, Registry.get(eaKey).asString());
    assertNull(lastChangedPair.get());

    Registry.loadState(registryElementFromMap(Map.of(key, firstVal, eaKey, eaSecondValue)), null);
    assertEquals(firstVal, Registry.get(key).asString());
    assertEquals(eaSecondValue, Registry.get(eaKey).asString());
    assertEquals(Pair.create(eaKey, eaSecondValue), lastChangedPair.get());
  }

  private Element registryElementFromMap(Map<String, String> map){
    Element registryElement = new Element("registry");
    for (Map.Entry<String, String> entry : map.entrySet()) {
      Element entryElement = new Element("entry");
      entryElement.setAttribute("key", entry.getKey());
      entryElement.setAttribute("value", entry.getValue());
      registryElement.addContent(entryElement);
    }
    return registryElement;
  }
}