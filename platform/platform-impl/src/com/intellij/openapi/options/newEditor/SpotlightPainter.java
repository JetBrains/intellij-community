// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.ComponentHighlightingListener;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

@ApiStatus.Internal
public class SpotlightPainter extends AbstractPainter implements ComponentHighlightingListener {
  private final HashMap<String, String> myConfigurableOption = new HashMap<>();
  private final MergingUpdateQueue myQueue;
  private final SpotlightPainterUpdater myUpdater;
  private final GlassPanel myGlassPanel;
  private final JComponent myTarget;
  boolean myVisible;

  public SpotlightPainter(@NotNull JComponent target, @NotNull Disposable parent, @NotNull SpotlightPainterUpdater updater) {
    myQueue = new MergingUpdateQueue("SettingsSpotlight", 200, false, target, parent, target);
    myUpdater = updater;
    myGlassPanel = new GlassPanel(target);
    myTarget = target;
    IdeGlassPaneUtil.installPainter(target, this, parent);
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(parent);
    connection.subscribe(ComponentHighlightingListener.TOPIC, this);
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

  public final void updateLater() {
    myQueue.queue(new Update(this) {
      @Override
      public void run() {
        updateNow();
      }
    });
  }

  public final void updateNow() {
    myUpdater.updateNow(this);
  }

  public void update(SettingsFilter filter, Configurable configurable, JComponent component) {
    if (configurable == null) {
      myGlassPanel.clear();
      myVisible = false;
    }
    else if (component != null) {
      myGlassPanel.clear();
      String text = filter.getSpotlightFilterText();
      myVisible = !text.isEmpty();
      SearchableConfigurable searchable = new SearchableConfigurable.Delegate(configurable);
      try {
        SearchUtil.lightOptions(searchable, component, text);
        Runnable search = searchable.enableSearch(text); // execute for empty string too
        if (search != null && !filter.contains(configurable) && !text.equals(myConfigurableOption.get(searchable.getId()))) {
          search.run();
        }
      }
      finally {
        myConfigurableOption.put(searchable.getId(), text);
      }
    }
    else if (!ApplicationManager.getApplication().isUnitTestMode()) {
      updateLater();
      return;
    }
    fireNeedsRepaint(myGlassPanel);
  }

  @Override
  public void highlight(@NotNull JComponent component, @NotNull String searchString) {
    // If several spotlight painters exist, they will receive each other updates,
    // because they share one message bus (ComponentHighlightingListener.TOPIC).
    // The painter should only draw spotlights for components in the hierarchy of `myTarget`
    if (UIUtil.isAncestor(myTarget, component)) {
      myGlassPanel.addSpotlight(component);
    }
  }

  @ApiStatus.Internal
  public interface SpotlightPainterUpdater {
    void updateNow(SpotlightPainter painter);
  }
}
