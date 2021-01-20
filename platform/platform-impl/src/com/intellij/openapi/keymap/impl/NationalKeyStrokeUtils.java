// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.actions.KeyWithMods;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class NationalKeyStrokeUtils {
  public static KeyEvent matchForSynonym(@NotNull KeyEvent e) {
    Map<Integer, KeyWithMods> synonyms = SynonymsConfig.getInstance().getSynonymsConfig();

    if (e.getID() != KeyEvent.KEY_PRESSED) {
      return null;
    }

    // TODO change map format to skip full map iteration
    // something like OriginalKey => List{ ResultKey, RequiredMods }
    for (Map.Entry<Integer, KeyWithMods> synToKeyWithMods : synonyms.entrySet()) {
      KeyWithMods keyWithMods = synToKeyWithMods.getValue();
      if (keyWithMods.getKey() == e.getKeyCode() &&
          (keyWithMods.getMods() & e.getModifiersEx()) == keyWithMods.getMods()) {
        int newMods = e.getModifiersEx() & ~keyWithMods.getMods();
        return new KeyEvent(e.getComponent(), e.getID(), e.getWhen(),
                            newMods, synToKeyWithMods.getKey(), KeyEvent.CHAR_UNDEFINED);
      }
    }
    return null;
  }

  public static void setSynonymConfig(@NotNull Map<Integer, KeyWithMods> config) {
    SynonymsConfig.getInstance().setSynonymConfig(config);
  }

  public static @NotNull Map<Integer, KeyWithMods> getSynonymConfig() {
    return SynonymsConfig.getInstance().getSynonymsConfig();
  }
}

@Service
@State(name = "SynonymsConfigState", storages = @Storage("synonymsConfig.xml"))
final class SynonymsConfig implements PersistentStateComponent<SynonymsConfig> {
  @NotNull
  private Map<Integer, KeyWithMods> synonyms;

  @Override
  public @NotNull SynonymsConfig getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull SynonymsConfig state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public SynonymsConfig() {
    synonyms = new HashMap<>();
  }

  public static SynonymsConfig getInstance() {
    return ServiceManager.getService(SynonymsConfig.class);
  }

  @NotNull
  public Map<Integer, KeyWithMods> getSynonymsConfig() {
    return synonyms;
  }

  public void setSynonymConfig(@NotNull Map<Integer, KeyWithMods> config) {
    synonyms = config;
  }
}
