// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.MutableConfigurableGroup;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

@ApiStatus.Internal
public final class SettingsEditor extends AbstractEditor implements DataProvider, Place.Navigator {
  private static final String SELECTED_CONFIGURABLE = "settings.editor.selected.configurable";
  private static final String SPLITTER_PROPORTION = "settings.editor.splitter.proportion";
  private static final float SPLITTER_PROPORTION_DEFAULT_VALUE = .2f;

  private final PropertiesComponent myProperties;
  private final Settings mySettings;
  private final SettingsSearch mySearch;
  private final SettingsFilter myFilter;
  private final SettingsTreeView myTreeView;
  private final ConfigurableEditor myEditor;
  private final OnePixelSplitter mySplitter;
  private final SpotlightPainter mySpotlightPainter;
  private final LoadingDecorator myLoadingDecorator;
  private final @NotNull Banner myBanner;
  private final History myHistory = new History(this);

  private final Map<Configurable, ConfigurableController> myControllers = new HashMap<>();
  private ConfigurableController myLastController;

  SettingsEditor(@NotNull Disposable parent,
                 @NotNull Project project,
                 @NotNull List<? extends ConfigurableGroup> groups,
                 @Nullable Configurable configurable,
                 final String filter,
                 @NotNull ISettingsTreeViewFactory factory) {
    super(parent);

    myProperties = PropertiesComponent.getInstance(project);
    mySettings = new Settings(groups) {
      @NotNull
      @Override
      protected Promise<? super Object> selectImpl(Configurable configurable) {
        myFilter.update(null);
        return myTreeView.select(configurable);
      }

      @Override
      protected @Nullable Configurable getConfigurableWithInitializedUiComponentImpl(@Nullable Configurable configurable,
                                                                                     boolean initializeUiComponentIfNotYet) {
        JComponent content = myEditor.getContent(configurable);
        if (!initializeUiComponentIfNotYet || content != null) {
          return content == null ? null : configurable;
        }

        myEditor.readContent(configurable); // calls Configurable.createComponent() and Configurable.reset()

        return configurable;
      }

      @Override
      protected void checkModifiedImpl(@NotNull Configurable configurable) {
        SettingsEditor.this.checkModified(configurable);
      }

      @Override
      protected void setSearchText(String search) {
        myFilter.update(search);
      }

      @Override
      public void revalidate() {
        myEditor.requestUpdate();
      }
    };
    mySearch = new SettingsSearch() {
      @Override
      void onTextKeyEvent(KeyEvent event) {
        myTreeView.myTree.processKeyEvent(event);
      }
    };
    JPanel searchPanel = new JPanel(new VerticalLayout(0));
    searchPanel.add(VerticalLayout.CENTER, mySearch);
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
      @NotNull
      @Override
      public Promise<? super Object> onSelected(@Nullable Configurable configurable, Configurable oldConfigurable) {
        if (configurable != null) {
          myProperties.setValue(SELECTED_CONFIGURABLE, ConfigurableVisitor.getId(configurable));
          myHistory.pushQueryPlace();
          myLoadingDecorator.startLoading(false);
        }
        checkModified(oldConfigurable);
        Promise<? super Object> result = myEditor.select(configurable);
        result.onSuccess(it -> {
          updateController(configurable);
          //requestFocusToEditor(); // TODO
          myLoadingDecorator.stopLoading();
        });
        return result;
      }

      @NotNull
      @Override
      public Promise<? super Object> onModifiedAdded(Configurable configurable) {
        return updateIfCurrent(configurable);
      }

      @NotNull
      @Override
      public Promise<? super Object> onModifiedRemoved(Configurable configurable) {
        return updateIfCurrent(configurable);
      }

      @NotNull
      @Override
      public Promise<? super Object> onErrorsChanged() {
        return updateIfCurrent(myFilter.myContext.getCurrentConfigurable());
      }

      @NotNull
      private Promise<? super Object> updateIfCurrent(@Nullable Configurable configurable) {
        if (configurable != null && configurable == myFilter.myContext.getCurrentConfigurable()) {
          updateStatus(configurable);
          return Promises.resolvedPromise();
        }
        else {
          return Promises.cancelledPromise();
        }
      }
    });
    myTreeView = factory.createTreeView(myFilter, groups);
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
        mySearch.updateToolTipText();
        myFilter.myContext.fireErrorsChanged(map, null);
        if (!map.isEmpty()) {
          Configurable targetConfigurable = map.keySet().iterator().next();
          ConfigurationException exception = map.get(targetConfigurable);
          Configurable originator = exception.getOriginator();
          if (originator != null) {
            targetConfigurable = originator;
          }
          myTreeView.select(targetConfigurable);
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
    myLoadingDecorator.setOverlayBackground(LoadingDecorator.OVERLAY_BACKGROUND);
    myBanner = new Banner(myEditor.getResetAction());
    searchPanel.setBorder(JBUI.Borders.empty(7, 5, 6, 5));
    myBanner.setBorder(JBUI.Borders.empty(11, 6, 0, 10));
    mySearch.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    searchPanel.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    JComponent left = new JPanel(new BorderLayout());
    left.add(BorderLayout.NORTH, searchPanel);
    left.add(BorderLayout.CENTER, myTreeView);
    JComponent right = new JPanel(new BorderLayout());
    right.add(BorderLayout.NORTH, withHistoryToolbar(myBanner));
    right.add(BorderLayout.CENTER, myLoadingDecorator.getComponent());
    mySplitter = new OnePixelSplitter(false, myProperties.getFloat(SPLITTER_PROPORTION, SPLITTER_PROPORTION_DEFAULT_VALUE));
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(left);
    mySplitter.setSecondComponent(right);

    if (IdeFrameDecorator.isCustomDecorationActive()) {
      mySplitter.getDivider().setOpaque(false);
    }

    mySpotlightPainter = new SpotlightPainter(myEditor, this) {
      @Override
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
      configurable = ConfigurableVisitor.findById(id != null ? id : "preferences.lookFeel", groups);
      if (configurable == null) {
        configurable = ConfigurableVisitor.find(ConfigurableVisitor.ALL, groups);
      }
    }

    myTreeView.select(configurable).onProcessed(it -> myFilter.update(filter));

    Disposer.register(this, myTreeView);
    installSpotlightRemover();
    //noinspection CodeBlock2Expr
    mySearch.getTextEditor().addActionListener(event -> {
      myTreeView.select(myFilter.myContext.getCurrentConfigurable()).onProcessed(o -> requestFocusToEditor());
    });

    for (ConfigurableGroup group : groups) {
      if (group instanceof MutableConfigurableGroup) {
        MutableConfigurableGroup mutable = (MutableConfigurableGroup)group;
        Disposer.register(this, mutable);
        mutable.addListener(createReloadListener(groups));
      }
    }
  }

  @ApiStatus.Internal
  public void select(Configurable configurable) {
    myTreeView.select(configurable);
    myEditor.select(configurable);
    updateController(configurable);
  }

  @NotNull
  private MutableConfigurableGroup.Listener createReloadListener(List<? extends ConfigurableGroup> groups) {
    return new MutableConfigurableGroup.Listener() {
      @Override
      public void handleUpdate() {
        Configurable selected = myEditor.getConfigurable();
        String id = selected instanceof SearchableConfigurable ? ((SearchableConfigurable)selected).getId() : null;
        myEditor.reload();
        myFilter.reload();
        myControllers.clear();
        myLastController = null;

        Configurable candidate = id == null ? null :ConfigurableVisitor.findById(id, groups);
        if (candidate == null) {
          candidate = ConfigurableVisitor.findById(PluginManagerConfigurable.ID, groups);
        }
        myEditor.init(candidate, false);
        myTreeView.reloadWithSelection(candidate);
        mySettings.reload();
        invalidate();
        repaint();
      }
    };
  }

  private void requestFocusToEditor() {
    JComponent component = myEditor.getPreferredFocusedComponent();
    if (component != null) {
      IdeFocusManager.findInstanceByComponent(component).requestFocus(component, true);
    }
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

  private JComponent withHistoryToolbar(JComponent component) {
    ActionGroup group = ActionUtil.getActionGroup("Back", "Forward");
    if (group == null) return component;
    JComponent toolbar = ActionUtil.createToolbarComponent(this, ActionPlaces.SETTINGS_HISTORY, group, true);
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.gridx = 1;
    gbc.weightx = 1;
    panel.add(component, gbc);
    gbc.gridx = 2;
    gbc.weightx = 0;
    gbc.insets = JBUI.insets(8, 2, 0, 0);
    panel.add(toolbar, gbc);
    return panel;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    place.putPath(SELECTED_CONFIGURABLE, myProperties.getValue(SELECTED_CONFIGURABLE));
  }

  @Override
  public @NotNull ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    Object path = place == null ? null : place.getPath(SELECTED_CONFIGURABLE);
    String id = path instanceof String ? (String)path : null;
    return mySettings.select(id == null ? null : mySettings.find(id));
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    return History.KEY.is(dataId) ? myHistory :
           Settings.KEY.is(dataId) ? mySettings :
           SearchTextField.KEY.is(dataId) ? mySearch :
           null;
  }

  @Override
  void disposeOnce() {
    if (myProperties == null || mySplitter == null) return; // if constructor failed
    myProperties.setValue(SPLITTER_PROPORTION, mySplitter.getProportion(), SPLITTER_PROPORTION_DEFAULT_VALUE);
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
  boolean cancel(AWTEvent source) {
    if (source instanceof KeyEvent && myFilter.myContext.isHoldingFilter()) {
      mySearch.setText("");
      return false;
    }
    for (Configurable configurable : myFilter.myContext.getModified()) {
      configurable.cancel();
    }
    return super.cancel(source);
  }

  @Override
  JComponent getPreferredFocusedComponent() {
    return myTreeView != null ? myTreeView.myTree : myEditor;
  }

  @Nullable
  Collection<@NlsContexts.ConfigurableName String> getPathNames() {
    return myTreeView == null ? null : myTreeView.getPathNames(myFilter.myContext.getCurrentConfigurable());
  }

  public void addOptionsListener(OptionsEditorColleague colleague) {
    myFilter.myContext.addColleague(colleague);
  }

  void updateStatus(Configurable configurable) {
    myFilter.updateSpotlight(configurable == null);
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

  private void updateController(@Nullable Configurable configurable) {
    Project project = myTreeView.findConfigurableProject(configurable);
    myBanner.setProjectText(project != null ? getProjectText(project) : null);
    myBanner.setText(myTreeView.getPathNames(configurable));

    if (myLastController != null) {
      myLastController.setBanner(null);
      myLastController = null;
    }

    ConfigurableController controller = ConfigurableController.getOrCreate(configurable, myControllers);
    if (controller != null) {
      myLastController = controller;
      controller.setBanner(myBanner);
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

  private static @NotNull @Nls String getProjectText(@NotNull Project project) {
    IdeUICustomization customization = IdeUICustomization.getInstance();
    return project.isDefault() ?
           customization.projectMessage("configurable.default.project.tooltip") :
           customization.projectMessage("configurable.current.project.tooltip");
  }
}
