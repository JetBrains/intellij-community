/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import java.awt.Component;
import java.awt.Graphics2D;
import java.util.IdentityHashMap;
import javax.swing.JComponent;

/**
 * @author Sergey.Malenkov
 */
abstract class SpotlightPainter extends AbstractPainter {
  private final IdentityHashMap<Configurable, String> myConfigurableOption = new IdentityHashMap<>();
  private final MergingUpdateQueue myQueue;
  private final GlassPanel myGlassPanel;
  private final JComponent myTarget;
  boolean myVisible;

  SpotlightPainter(JComponent target, Disposable parent) {
    myQueue = new MergingUpdateQueue("SettingsSpotlight", 200, false, target, parent, target);
    myGlassPanel = new GlassPanel(target);
    myTarget = target;
    IdeGlassPaneUtil.installPainter(target, this, parent);
  }

  @Override
  public void executePaint(Component component, Graphics2D g) {
    if (myVisible && myGlassPanel.isVisible()) {
      myGlassPanel.paintSpotlight(g, myTarget);
    }
  }

  @Override
  public boolean needsRepaint() {
    return true;
  }

  void updateLater() {
    myQueue.queue(new Update(this) {
      @Override
      public void run() {
        updateNow();
      }
    });
  }

  abstract void updateNow();

  void update(SettingsFilter filter, Configurable configurable, JComponent component) {
    if (configurable == null) {
      myGlassPanel.clear();
      myVisible = false;
    }
    else if (component != null) {
      myGlassPanel.clear();
      String text = filter.getFilterText();
      myVisible = !text.isEmpty();
      try {
        SearchableConfigurable searchable = new SearchableConfigurable.Delegate(configurable);
        SearchUtil.lightOptions(searchable, component, text, myGlassPanel).run();
        Runnable search = searchable.enableSearch(text); // execute for empty string too
        if (search != null && !filter.contains(configurable) && !text.equals(myConfigurableOption.get(configurable))) {
          search.run();
        }
      }
      finally {
        myConfigurableOption.put(configurable, text);
      }
    }
    else if (!ApplicationManager.getApplication().isUnitTestMode()) {
      updateLater();
      return;
    }
    fireNeedsRepaint(myGlassPanel);
  }
}
