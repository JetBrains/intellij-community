// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry;

import com.intellij.idea.TestFor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class RegistryTest {
  private static final String INTEGER_KEY = "editor.mouseSelectionStateResetDeadZone";
  private static final String INT_KEY_REQUIRE_RESTART = "editor.caret.width";

  @After
  public void tearDown(){
    Registry.Companion.setValueChangeListener(null);
  }

  @Test
  public void testInvalidInteger() {
    int originalValue = Registry.intValue(INTEGER_KEY);
    Registry.get(INTEGER_KEY).setValue("invalidNumber");
    assertThat(Registry.intValue(INTEGER_KEY)).isEqualTo(originalValue);
  }

  @Test
  public void booleanValueFalse() {
    RegistryValue handle = Registry.get("ide.tree.experimental.layout.cache");
    try {
      assertThat(handle.asString()).isEqualTo("true");
      assertThat(handle.asBoolean()).isTrue();
    }
    finally {
      handle.resetToDefault();
    }
  }

  @Test
  public void booleanValueTrue() {
    RegistryValue handle = Registry.get("ide.tree.painter.classic.compact");
    try {
      assertThat(handle.asString()).isEqualTo("false");
      assertThat(handle.asBoolean()).isFalse();
    }
    finally {
      handle.resetToDefault();
    }
  }

  @Test
  public void checkListenerCalledOnReload() {
    Registry.getInstance().reset();
    String key = "first.key1";
    String firstVal = "first.value1";
    String secondValue = "second.value1";
    final AtomicReference<Pair<String, String>> lastChangedPair = new AtomicReference<>();
    Registry.Companion.setValueChangeListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        if (!value.getKey().equals(key))
          return;
        lastChangedPair.set(Pair.create(value.getKey(), value.asString()));
      }
    });

    Map<String, String> firstMap = new LinkedHashMap<>();
    firstMap.put(key, firstVal);
    Registry.Companion.loadState(registryElementFromMap(firstMap), null);
    assertEquals(firstVal, Registry.get(key).asString());
    assertNull(lastChangedPair.get());

    Map<String, String> secondMap = new LinkedHashMap<>();
    secondMap.put(key, secondValue);
    Registry.Companion.loadState(registryElementFromMap(secondMap), null);
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
    Registry.Companion.setValueChangeListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        if (!(value.getKey().equals(key) || value.getKey().equals(eaKey)))
          return;
        lastChangedPair.set(Pair.create(value.getKey(), value.asString()));
      }
    });

    Registry.Companion.loadState(registryElementFromMap(Map.of(key, firstVal)), Map.of(eaKey, eaFirstVal));
    assertEquals(firstVal, Registry.get(key).asString());
    assertEquals(eaFirstVal, Registry.get(eaKey).asString());
    assertNull(lastChangedPair.get());

    Registry.Companion.loadState(registryElementFromMap(Map.of(key, firstVal, eaKey, eaSecondValue)), null);
    assertEquals(firstVal, Registry.get(key).asString());
    assertEquals(eaSecondValue, Registry.get(eaKey).asString());
    assertEquals(Pair.create(eaKey, eaSecondValue), lastChangedPair.get());
  }

  @Test
  @TestFor(issues = "IDEA-324916")
  public void ignoreUnknownRegistryValues(){
    Registry.getInstance().reset();
    String key = "first.key1";
    String firstVal = "first.value1";
    String secondValue = "second.value1";

    String newKey = "another.key";
    String newValue = "another.value";
    final AtomicReference<Pair<String, String>> lastChangedPair = new AtomicReference<>();
    final List<Pair<String, String>> changedPairs = new ArrayList<>();
    Registry.Companion.setValueChangeListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        if (!(value.getKey().equals(key)))
          return;
        Pair<String, String> pair = Pair.create(value.getKey(), value.asString());
        lastChangedPair.set(pair);
        changedPairs.add(pair);
      }
    });

    Map<String, String> firstMap = new LinkedHashMap<>();
    firstMap.put(key, firstVal);
    Registry.Companion.loadState(registryElementFromMap(firstMap), null);
    assertEquals(firstVal, Registry.get(key).asString());
    assertNull(lastChangedPair.get());

    Map<String, String> secondMap = new LinkedHashMap<>();
    secondMap.put(key, secondValue);
    secondMap.put(newKey, newValue);
    Registry.Companion.loadState(registryElementFromMap(secondMap), null);
    assertEquals(secondValue, Registry.get(key).asString());
    RegistryValue newRegistryValue = Registry.get(newKey);
    String loadedNewValue = newRegistryValue.resolveNotRequiredValue(newRegistryValue.getKey());
    assertNull(loadedNewValue);
    assertThat(changedPairs).hasSize(1);
  }

  @Test
  public void checkValueResetToDefault() {
    Registry.getInstance().reset();
    String firstKey = "first.key1";
    String firstKeyInitValue = "first.initVal";
    String firstKeyChangedVal = "first.value2";

    String secondKey = "second.key";
    String secondKeyInitValue = "second.initValue";
    String secondKeyChangedValue = "second.initValue";

    // initialize registry
    Registry.Companion.loadState(registryElementFromMap(new LinkedHashMap<>(){{
      put(firstKey, firstKeyInitValue);
        put(secondKey, secondKeyInitValue);
    }}), null);
    assertEquals(firstKeyInitValue, Registry.get(firstKey).asString());
    assertEquals(secondKeyInitValue, Registry.get(secondKey).asString());

    // add custom values
    Registry.Companion.loadState(registryElementFromMap(new LinkedHashMap<>(){{
      put(firstKey, firstKeyChangedVal);
      put(secondKey, secondKeyChangedValue);
    }}), null);
    assertThat(Registry.get(firstKey).asString()).isEqualTo(firstKeyChangedVal);
    assertThat(Registry.get(secondKey).asString()).isEqualTo(secondKeyChangedValue);

    // drop key - reset to original
    Registry.Companion.loadState(registryElementFromMap(new LinkedHashMap<>(){{
      put(firstKey, firstKeyChangedVal);
    }}), null);
    assertThat(Registry.get(firstKey).asString()).isEqualTo(firstKeyChangedVal);
    assertThat(Registry.get(secondKey).asString()).isEqualTo(secondKeyInitValue);
  }

  @Test
  public void dontPersistDefaultValue(){
    int originalValue = Registry.intValue(INT_KEY_REQUIRE_RESTART);
    Registry.get(INT_KEY_REQUIRE_RESTART).setValue("2222");
    assertTrue(JDOMUtil.writeElement(Registry.getInstance().getState()).contains(
      "<entry key=\"%s\" value=\"2222\"".formatted(INT_KEY_REQUIRE_RESTART))
    );
    Registry.get(INT_KEY_REQUIRE_RESTART).setValue(String.valueOf(originalValue));
    assertFalse(JDOMUtil.writeElement(Registry.getInstance().getState()).contains(
      INT_KEY_REQUIRE_RESTART)
    );
  }

  @Test
  public void beforeListenerDoesNotChangeValueWhenSetting() {
    String registryValue = "testBoolean";
    RegistryValue regValue = new RegistryValue(Registry.getInstance(), registryValue, null);
    regValue.setValue(false);
    Registry.Companion.setValueChangeListener(new RegistryValueListener() {
      @Override
      public void beforeValueChanged(@NotNull RegistryValue value) {
        regValue.asBoolean();
      }
    });
    regValue.setValue(true);
    assertThat(regValue.asBoolean()).isTrue();
  }

  @Test
  public void checkElementOrderIsStable() {
    Registry.getInstance().reset();

    {
      Map<String, String> map = populateMap(Comparator.naturalOrder(), "value");
      Element state2load = registryElementFromMap(map);
      Registry.Companion.loadState(state2load, null);
      assertEquals(JDOMUtil.writeElement(state2load), JDOMUtil.writeElement(Registry.getInstance().getState()));
    }
    // load state with elements reversed
    Registry.Companion.loadState(registryElementFromMap(populateMap(Comparator.reverseOrder(), "AnotherValue1111")), null);

    assertEquals(JDOMUtil.writeElement(registryElementFromMap(populateMap(Comparator.naturalOrder(), "AnotherValue1111"))),
                 JDOMUtil.writeElement(Registry.getInstance().getState()));
  }

  @Test
  public void checkOptionsUpdatedProperly() {
    String registryName = "testOptions";
    String registryValue = "[option1*|option2|option3]";
    RegistryValue registry = new RegistryValue(Registry.getInstance(), registryName, new RegistryKeyDescriptor(registryName, "", registryValue, false, false, null));
    assertEquals("option1", registry.getSelectedOption());
    registry.setSelectedOption("option2");
    assertThat(registry.getSelectedOption()).isEqualTo("option2");
  }

  private static Map<String, String> populateMap(Comparator<String> comparator, String valueBase) {
    Map<String, String> map = new TreeMap<>(comparator);
    map.put("first.key", "first." + valueBase);
    for (int i = 0; i < 20; i++) {
      map.put("Key#" + i, valueBase + "." + i);
    }
    map.put("second.key", "second." + valueBase);
    map.put("third.key", "third." + valueBase);
    map.put("forth.key", "forth." + valueBase);
    map.put("fifth.key", "fifth." + valueBase);
    return map;
  }


  private static Element registryElementFromMap(Map<String, String> map){
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