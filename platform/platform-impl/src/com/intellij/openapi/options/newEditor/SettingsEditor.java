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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * @author Sergey.Malenkov
 */
final class SettingsEditor extends AbstractEditor implements DataProvider {
  private static final String SELECTED_CONFIGURABLE = "settings.editor.selected.configurable";
  private static final String SPLITTER_PROPORTION = "settings.editor.splitter.proportion";

  private final PropertiesComponent myProperties;
  private final Settings mySettings;
  private final SettingsSearch mySearch;
  private final SettingsFilter myFilter;
  private final SettingsTreeView myTreeView;
  private final ConfigurableEditor myEditor;
  private final OnePixelSplitter mySplitter;
  private final SpotlightPainter mySpotlightPainter;
  private final Banner myBanner;

  SettingsEditor(Disposable parent, Project project, ConfigurableGroup[] groups, Configurable configurable, String filter) {
    super(parent);

    myProperties = PropertiesComponent.getInstance(project);
    mySettings = new Settings(groups) {
      @Override
      protected ActionCallback selectImpl(Configurable configurable) {
        myFilter.update(null, false, true);
        return myTreeView.select(configurable);
      }
    };
    mySearch = new SettingsSearch() {
      @Override
      void onTextKeyEvent(KeyEvent event) {
        myTreeView.myTree.processKeyEvent(event);
      }
    };
    myFilter = new SettingsFilter(project, groups, mySearch) {
      @Override
      Configurable getConfigurable(SimpleNode node) {
        return SettingsTreeView.getConfigurable(node);
      }

      @Override
      SimpleNode findNode(Configurable configurable) {
        return myTreeView.findNode(configurable);
      }

      @Override
      void updateSpotlight(boolean now) {
        if (!myDisposed && mySpotlightPainter != null) {
          if (!now) {
            mySpotlightPainter.updateLater();
          }
          else {
            mySpotlightPainter.updateNow();
          }
        }
      }
    };
    myFilter.myContext.addColleague(new OptionsEditorColleague() {
      @Override
      public ActionCallback onSelected(@Nullable Configurable configurable, Configurable oldConfigurable) {
        if (configurable != null) {
          myProperties.setValue(SELECTED_CONFIGURABLE, ConfigurableVisitor.ByID.getID(configurable));
        }
        checkModified(oldConfigurable);
        return myEditor.select(configurable);
      }

      @Override
      public ActionCallback onModifiedAdded(Configurable configurable) {
        return updateIfCurrent(configurable);
      }

      @Override
      public ActionCallback onModifiedRemoved(Configurable configurable) {
        return updateIfCurrent(configurable);
      }

      @Override
      public ActionCallback onErrorsChanged() {
        return updateIfCurrent(myFilter.myContext.getCurrentConfigurable());
      }

      private ActionCallback updateIfCurrent(Configurable configurable) {
        if (configurable != null && configurable == myFilter.myContext.getCurrentConfigurable()) {
          updateStatus(configurable);
          return ActionCallback.DONE;
        }
        else {
          return ActionCallback.REJECTED;
        }
      }
    });
    myTreeView = new SettingsTreeView(myFilter, groups);
    myTreeView.myTree.addKeyListener(mySearch);
    myTreeView.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        Dimension size = mySearch.getPreferredSize();
        size.width = myTreeView.getWidth() - 10;
        mySearch.setPreferredSize(size);
        mySearch.setSize(size);
        mySearch.revalidate();
        mySearch.repaint();
      }
    });
    myEditor = new ConfigurableEditor(this, null, true) {
      @Override
      boolean apply() {
        checkModified(myFilter.myContext.getCurrentConfigurable());
        if (myFilter.myContext.getModified().isEmpty()) {
          return true;
        }
        Map<Configurable, ConfigurationException> map = new LinkedHashMap<Configurable, ConfigurationException>();
        for (Configurable configurable : myFilter.myContext.getModified()) {
          ConfigurationException exception = ConfigurableEditor.apply(configurable);
          if (exception != null) {
            map.put(configurable, exception);
          }
          else if (!configurable.isModified()) {
            myFilter.myContext.fireModifiedRemoved(configurable, null);
          }
        }
        myFilter.myContext.fireErrorsChanged(map, null);
        if (!map.isEmpty()) {
          myTreeView.select(map.keySet().iterator().next());
          return false;
        }
        updateStatus(myFilter.myContext.getCurrentConfigurable());
        return true;
      }

      @Override
      void updateCurrent(Configurable configurable, boolean reset) {
        if (reset && configurable != null) {
          myFilter.myContext.fireReset(configurable);
        }
        checkModified(configurable);
      }

      @Override
      void openLink(Configurable configurable) {
        mySettings.select(configurable);
      }
    };
    mySplitter = new OnePixelSplitter(false, myProperties.getFloat(SPLITTER_PROPORTION, .2f));
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(myTreeView);
    mySplitter.setSecondComponent(myEditor);
    mySpotlightPainter = new SpotlightPainter(myEditor, this) {
      void updateNow() {
        Configurable configurable = myFilter.myContext.getCurrentConfigurable();
        update(myFilter, configurable, myEditor.getContent(configurable));
      }
    };
    myBanner = new Banner(myEditor.getResetAction());
    myBanner.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.add(BorderLayout.WEST, mySearch);
    panel.add(BorderLayout.CENTER, myBanner);
    panel.setBorder(BorderFactory.createCompoundBorder(
      new CustomLineBorder(OnePixelDivider.BACKGROUND, 0, 0, 1, 0),
      BorderFactory.createEmptyBorder(5, 5, 5, 5)));

    add(BorderLayout.NORTH, panel);
    add(BorderLayout.CENTER, mySplitter);

    if (configurable == null) {
      String id = myProperties.getValue(SELECTED_CONFIGURABLE);
      configurable = new ConfigurableVisitor.ByID(id != null ? id : "preferences.lookFeel").find(groups);
      if (configurable == null) {
        configurable = ConfigurableVisitor.ALL.find(groups);
      }
    }
    myFilter.update(filter, false, true);
    myTreeView.select(configurable);
    Disposer.register(this, myTreeView);
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return Settings.KEY.is(dataId) ? mySettings : null;
  }

  @Override
  void disposeOnce() {
    myProperties.setValue(SPLITTER_PROPORTION, Float.toString(mySplitter.getProportion()));
  }

  @Override
  Action getApplyAction() {
    return myEditor.getApplyAction();
  }

  @Override
  Action getResetAction() {
    return null;
  }

  @Override
  String getHelpTopic() {
    Configurable configurable = myFilter.myContext.getCurrentConfigurable();
    while (configurable != null) {
      String topic = configurable.getHelpTopic();
      if (topic != null) {
        return topic;
      }
      configurable = myFilter.myContext.getParentConfigurable(configurable);
    }
    return null;
  }

  @Override
  boolean apply() {
    return myEditor.apply();
  }

  @Override
  boolean cancel() {
    if (myFilter.myContext.isHoldingFilter()) {
      mySearch.setText("");
      return false;
    }
    return super.cancel();
  }

  @Override
  JComponent getPreferredFocusedComponent() {
    return myTreeView != null ? myTreeView.myTree : myEditor;
  }

  void updateStatus(Configurable configurable) {
    myFilter.updateSpotlight(configurable == null);
    if (myBanner != null) {
      myBanner.setProject(myTreeView.findConfigurableProject(configurable));
      myBanner.setText(myTreeView.getPathNames(configurable));
    }
    if (myEditor != null) {
      ConfigurationException exception = myFilter.myContext.getErrors().get(configurable);
      myEditor.getApplyAction().setEnabled(!myFilter.myContext.getModified().isEmpty());
      myEditor.getResetAction().setEnabled(myFilter.myContext.isModified(configurable) || exception != null);
      myEditor.setError(exception);
    }
  }

  void checkModified(Configurable configurable) {
    Configurable parent = myFilter.myContext.getParentConfigurable(configurable);
    if (ConfigurableWrapper.hasOwnContent(parent)) {
      checkModifiedForItem(parent);
      for (Configurable child : myFilter.myContext.getChildren(parent)) {
        checkModifiedForItem(child);
      }
    }
    else if (configurable != null) {
      checkModifiedForItem(configurable);
    }
    updateStatus(configurable);
  }

  private void checkModifiedForItem(final Configurable configurable) {
    if (configurable != null) {
      JComponent component = myEditor.getContent(configurable);
      if (component == null && ConfigurableWrapper.hasOwnContent(configurable)) {
        component = myEditor.readContent(configurable);
      }
      if (component != null) {
        checkModifiedInternal(configurable);
      }
    }
  }

  private void checkModifiedInternal(Configurable configurable) {
    if (configurable.isModified()) {
      myFilter.myContext.fireModifiedAdded(configurable, null);
    }
    else if (!myFilter.myContext.getErrors().containsKey(configurable)) {
      myFilter.myContext.fireModifiedRemoved(configurable, null);
    }
  }
}
