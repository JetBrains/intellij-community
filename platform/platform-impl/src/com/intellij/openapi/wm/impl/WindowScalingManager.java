/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PlatformScalingUtil;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;
import java.util.Map;

public final class WindowScalingManager {
  /**
   * The resource sizes as they were set at startup.
   */
  private ResourceSizes myInitialResourceSizes;

  public WindowScalingManager() {
    PlatformScalingUtil.getInstance().addListener(e -> rescaleFrames(e.getScaleFactor()));
  }

  public void rescaleFrames(float scaleFactor) {
    float currentScaleFactor = PlatformScalingUtil.getInstance().getActiveScaleFactor();
    if (currentScaleFactor == scaleFactor) {
      return;
    }
    ResourceSizes resourceSizes = computeResourceSizes(currentScaleFactor, scaleFactor);
    tweakUIDefaults(resourceSizes);
    tweakEditorAndFireUpdateUI(resourceSizes);
  }

  private ResourceSizes computeResourceSizes(float oldScaleFactor, float newScaleFactor) {
    if (myInitialResourceSizes == null) {
      myInitialResourceSizes = new ResourceSizes(oldScaleFactor);

      UIDefaults defaults = UIManager.getDefaults();
      Enumeration<Object> keys = defaults.keys();
      while (keys.hasMoreElements()) {
        Object key = keys.nextElement();
        if (key instanceof String) {
          String name = (String)key;
          if (name.endsWith(".font")) {
            Font font = defaults.getFont(key);
            myInitialResourceSizes.setValue(name, font);
          }
          else if (name.endsWith(".rowHeight")) {
            myInitialResourceSizes.setValue(name, defaults.getInt(key));
          }
        }
      }

      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      myInitialResourceSizes.setEditorFontSize(globalScheme.getEditorFontSize());
      myInitialResourceSizes.setConsoleFontSize(globalScheme.getConsoleFontSize());
    }

    float factor = newScaleFactor / myInitialResourceSizes.getScaleFactor();
    ResourceSizes result = new ResourceSizes(newScaleFactor);
    for (Map.Entry<String, Object> entry : myInitialResourceSizes.getValues()) {
      Object value = entry.getValue();
      if (value instanceof Font) {
        Font font = (Font)value;
        result.setValue(entry.getKey(), new FontUIResource(font.getName(), font.getStyle(), JBUI.scaleEx(factor, font.getSize())));
      } else if (value instanceof Integer) {
        result.setValue(entry.getKey(), JBUI.scaleEx(factor, (Integer)value));
      }
    }
    result.setEditorFontSize(JBUI.scaleEx(factor, myInitialResourceSizes.getEditorFontSize()));
    result.setConsoleFontSize(JBUI.scaleEx(factor, myInitialResourceSizes.getConsoleFontSize()));
    return result;
  }

  private void tweakUIDefaults(ResourceSizes resourceSizes) {
    UIDefaults defaults = UIManager.getDefaults();
    for (Map.Entry<String, Object> entry : resourceSizes.getValues()) {
      defaults.put(entry.getKey(), entry.getValue());
    }
    PlatformScalingUtil.getInstance().setActiveScaleFactor(resourceSizes.getScaleFactor());
  }

  private void tweakEditorAndFireUpdateUI(ResourceSizes resourceSizes) {
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    globalScheme.setConsoleFontSize(resourceSizes.getConsoleFontSize());
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor instanceof EditorEx) {
        ((EditorEx)editor).setFontSize(resourceSizes.getEditorFontSize());
      }
    }
    UISettings.getInstance().fireUISettingsChanged();
    LafManager.getInstance().updateUI();
    EditorUtil.reinitSettings();
  }

  /**
   * Store a snapshot of all UI resources that need to be updated
   * when the global scaling factor is updated.
   */
  private static class ResourceSizes {
    private float myScaleFactor;
    private final Map<String, Object> myResourceSizes = ContainerUtil.newLinkedHashMap();
    private int myEditorFontSize;
    private int myConsoleFontSize;

    public ResourceSizes(float scaleFactor) {
      myScaleFactor = scaleFactor;
    }

    public void setValue(String key, Object value) {
      myResourceSizes.put(key, value);
    }

    public Iterable<Map.Entry<String, Object>> getValues() {
      return myResourceSizes.entrySet();
    }

    public int getConsoleFontSize() {
      return myConsoleFontSize;
    }

    public void setConsoleFontSize(int consoleFontSize) {
      myConsoleFontSize = consoleFontSize;
    }

    public float getScaleFactor() {
      return myScaleFactor;
    }

    public int getEditorFontSize() {
      return myEditorFontSize;
    }

    public void setEditorFontSize(int editorFontSize) {
      myEditorFontSize = editorFontSize;
    }
  }
}
