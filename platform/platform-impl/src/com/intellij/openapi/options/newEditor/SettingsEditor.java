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
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey.Malenkov
 */
final class SettingsEditor extends AbstractEditor implements DataProvider {
  private static final String SELECTED_CONFIGURABLE = "settings.editor.selected.configurable";
  private static final String SPLITTER_PROPORTION = "settings.editor.splitter.proportion";

  private final PropertiesComponent myProperties;
  private final Settings mySettings;
  private final SettingsSearch mySearch;
  private final JPanel mySearchPanel;
  private final SettingsFilter myFilter;
  private final SettingsTreeView myTreeView;
  private final ConfigurableEditor myEditor;
  private final OnePixelSplitter mySplitter;
  private final SpotlightPainter mySpotlightPainter;
  private final LoadingDecorator myLoadingDecorator;
  private final Banner myBanner;

  SettingsEditor(Disposable parent, Project project, ConfigurableGroup[] groups, Configurable configurable, final String filter) {
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
    mySearchPanel = new JPanel(new VerticalLayout(0));
    mySearchPanel.add(VerticalLayout.CENTER, mySearch);
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
          myLoadingDecorator.startLoading(false);
        }
        checkModified(oldConfigurable);
        ActionCallback result = myEditor.select(configurable);
        result.doWhenDone(() -> myLoadingDecorator.stopLoading());
        return result;
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
    myEditor = new ConfigurableEditor(this, null) {
      @Override
      boolean apply() {
        checkModified(myFilter.myContext.getCurrentConfigurable());
        if (myFilter.myContext.getModified().isEmpty()) {
          return true;
        }
        Map<Configurable, ConfigurationException> map = new LinkedHashMap<>();
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
    myEditor.setPreferredSize(JBUI.size(800, 600));
    myLoadingDecorator = new LoadingDecorator(myEditor, this, 10, true);
    myBanner = new Banner(myEditor.getResetAction());
    mySearchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JComponent left = myTreeView;
    JComponent right = myLoadingDecorator.getComponent();
    if (Registry.is("ide.settings.old.style")) {
      myBanner.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
      mySearch.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
      mySearchPanel.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
      mySearchPanel.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
          Dimension size = myBanner.getPreferredSize();
          size.height = mySearchPanel.getHeight() - 5;
          myBanner.setPreferredSize(size);
          myBanner.setSize(size);
          myBanner.revalidate();
          myBanner.repaint();
        }
      });
      left = new JPanel(new BorderLayout());
      left.add(BorderLayout.NORTH, mySearchPanel);
      left.add(BorderLayout.CENTER, myTreeView);

      right = new JPanel(new BorderLayout());
      right.add(BorderLayout.NORTH, myBanner);
      right.add(BorderLayout.CENTER, myLoadingDecorator.getComponent());
    }
    else {
      myBanner.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
      myTreeView.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
          Dimension size = mySearchPanel.getPreferredSize();
          size.width = myTreeView.getWidth();
          mySearchPanel.setPreferredSize(size);
          mySearchPanel.setSize(size);
          mySearchPanel.revalidate();
          mySearchPanel.repaint();
        }
      });
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(BorderLayout.WEST, mySearchPanel);
      panel.add(BorderLayout.CENTER, myBanner);
      panel.setBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 1, 0));
      add(BorderLayout.NORTH, panel);
    }
    mySplitter = new OnePixelSplitter(false, myProperties.getFloat(SPLITTER_PROPORTION, .2f));
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(left);
    mySplitter.setSecondComponent(right);
    mySpotlightPainter = new SpotlightPainter(myEditor, this) {
      void updateNow() {
        Configurable configurable = myFilter.myContext.getCurrentConfigurable();
        if (myTreeView.myTree.hasFocus() || mySearch.getTextEditor().hasFocus()) {
          update(myFilter, configurable, myEditor.getContent(configurable));
        }
      }
    };
    add(BorderLayout.CENTER, mySplitter);

    if (configurable == null) {
      String id = myProperties.getValue(SELECTED_CONFIGURABLE);
      configurable = new ConfigurableVisitor.ByID(id != null ? id : "preferences.lookFeel").find(groups);
      if (configurable == null) {
        configurable = ConfigurableVisitor.ALL.find(groups);
      }
    }
    myTreeView.select(configurable).doWhenDone(() -> myFilter.update(filter, false, true));
    Disposer.register(this, myTreeView);
    installSpotlightRemover();
    mySearch.getTextEditor().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        myTreeView.select(myFilter.myContext.getCurrentConfigurable()).doWhenDone(() -> {
          JComponent component1 = myEditor.getPreferredFocusedComponent();
          if (component1 != null) {
            IdeFocusManager.findInstanceByComponent(component1).requestFocus(component1, true);
          }
        });
      }
    });
  }

  private void installSpotlightRemover() {
    final FocusAdapter spotlightRemover = new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        final Component comp = e.getOppositeComponent();
        if (comp == mySearch.getTextEditor() || comp == myTreeView.myTree) {
          return;
        }
        mySpotlightPainter.update(null, null, null);
      }

      @Override
      public void focusGained(FocusEvent e) {
        if (!StringUtil.isEmpty(mySearch.getText())) {
          mySpotlightPainter.updateNow();
        }
      }
    };
    myTreeView.myTree.addFocusListener(spotlightRemover);
    mySearch.getTextEditor().addFocusListener(spotlightRemover);
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
    return "preferences";
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
      myEditor.revalidate();
    }
    if (configurable != null) {
      new Alarm().addRequest(() -> {
        if (!myDisposed && mySpotlightPainter != null) {
          mySpotlightPainter.updateNow();
        }
      }, 300);
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
