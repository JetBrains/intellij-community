// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
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
import com.intellij.openapi.ui.Splitter;
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
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public final class SettingsEditor extends AbstractEditor implements UiDataProvider, Place.Navigator {
  private static final String SELECTED_CONFIGURABLE = "settings.editor.selected.configurable";
  private static final String SPLITTER_PROPORTION = "settings.editor.splitter.proportion";
  private static final float SPLITTER_PROPORTION_DEFAULT_VALUE = .2f;

  private final PropertiesComponent properties;
  private final Settings settings;
  private final SettingsSearch search;
  private final SettingsFilter filter;
  private final SettingsTreeView treeView;
  private final ConfigurableEditor editor;
  private final OnePixelSplitter mySplitter;
  private final SpotlightPainter spotlightPainter;
  private final LoadingDecorator loadingDecorator;
  private final @NotNull Banner myBanner;
  private final History myHistory = new History(this);

  private final Map<Configurable, ConfigurableController> controllers = new HashMap<>();
  private ConfigurableController lastController;

  SettingsEditor(@NotNull Disposable parent,
                 @NotNull Project project,
                 @NotNull List<? extends ConfigurableGroup> groups,
                 @Nullable Configurable configurable,
                 String filter,
                 @NotNull ISettingsTreeViewFactory factory,
                 @NotNull SpotlightPainterFactory spotlightPainterFactory) {
    super(parent);

    properties = PropertiesComponent.getInstance(project);
    settings = new Settings(groups) {
      @Override
      protected @NotNull Promise<? super Object> selectImpl(Configurable configurable) {
        SettingsEditor.this.filter.update(null);
        return treeView.select(configurable);
      }

      @Override
      protected @Nullable Configurable getConfigurableWithInitializedUiComponentImpl(@Nullable Configurable configurable,
                                                                                     boolean initializeUiComponentIfNotYet) {
        JComponent content = editor.getContent(configurable);
        if (!initializeUiComponentIfNotYet || content != null) {
          return content == null ? null : configurable;
        }

        // calls Configurable.createComponent() and Configurable.reset()
        editor.readContent(configurable);
        return configurable;
      }

      @Override
      protected void checkModifiedImpl(@NotNull Configurable configurable) {
        SettingsEditor.this.checkModified(configurable);
      }

      @Override
      protected void setSearchText(String search) {
        SettingsEditor.this.filter.update(search);
      }

      @Override
      public void revalidate() {
        editor.requestUpdate();
      }
    };
    search = new SettingsSearch() {
      @Override
      void onTextKeyEvent(KeyEvent event) {
        treeView.getTree().processKeyEvent(event);
      }
    };

    JPanel searchPanel = new JPanel(new VerticalLayout(0));
    searchPanel.add(VerticalLayout.CENTER, search);
    this.filter = new SettingsFilter(project, groups, search, coroutineScope) {
      @Override
      protected Configurable getConfigurable(SimpleNode node) {
        return SettingsTreeView.getConfigurable(node);
      }

      @Override
      protected SimpleNode findNode(Configurable configurable) {
        return treeView.findNode(configurable);
      }

      @Override
      protected void updateSpotlight(boolean now) {
        if (!isDisposed && spotlightPainter != null) {
          if (!now) {
            spotlightPainter.updateLater();
          }
          else {
            spotlightPainter.updateNow();
          }
        }
      }
    };
    this.filter.context.addColleague(new OptionsEditorColleague() {
      @Override
      public @NotNull Promise<? super Object> onSelected(@Nullable Configurable configurable, Configurable oldConfigurable) {
        if (configurable != null) {
          properties.setValue(SELECTED_CONFIGURABLE, ConfigurableVisitor.getId(configurable));
          myHistory.pushQueryPlace();
          loadingDecorator.startLoading(false);
        }
        checkModified(oldConfigurable);
        Promise<? super Object> result = editor.select(configurable);
        result.onSuccess(it -> {
          updateController(configurable);
          //requestFocusToEditor(); // TODO
          loadingDecorator.stopLoading();
        });
        return result;
      }

      @Override
      public @NotNull Promise<? super Object> onModifiedAdded(Configurable configurable) {
        return updateIfCurrent(configurable);
      }

      @Override
      public @NotNull Promise<? super Object> onModifiedRemoved(Configurable configurable) {
        return updateIfCurrent(configurable);
      }

      @Override
      public @NotNull Promise<? super Object> onErrorsChanged() {
        return updateIfCurrent(SettingsEditor.this.filter.context.getCurrentConfigurable());
      }

      private @NotNull Promise<? super Object> updateIfCurrent(@Nullable Configurable configurable) {
        if (configurable != null && configurable == SettingsEditor.this.filter.context.getCurrentConfigurable()) {
          updateStatus(configurable);
          return Promises.resolvedPromise();
        }
        else {
          return Promises.cancelledPromise();
        }
      }
    });
    treeView = factory.createTreeView(this.filter, groups);
    treeView.getTree().addKeyListener(search);
    editor = new ConfigurableEditor(this, null) {
      @Override
      protected boolean apply() {
        checkModified(SettingsEditor.this.filter.context.getCurrentConfigurable());
        if (SettingsEditor.this.filter.context.getModified().isEmpty()) {
          return true;
        }
        Map<Configurable, ConfigurationException> map = new LinkedHashMap<>();
        for (Configurable configurable : SettingsEditor.this.filter.context.getModified()) {
          ConfigurationException exception = ConfigurableEditor.apply(configurable);
          if (exception != null) {
            map.put(configurable, exception);
          }
          else if (!configurable.isModified()) {
            SettingsEditor.this.filter.context.fireModifiedRemoved(configurable, null);
          }
        }
        search.updateToolTipText();
        SettingsEditor.this.filter.context.fireErrorsChanged(map, null);
        if (!map.isEmpty()) {
          Configurable targetConfigurable = map.keySet().iterator().next();
          ConfigurationException exception = map.get(targetConfigurable);
          Configurable originator = exception.getOriginator();
          if (originator != null) {
            targetConfigurable = originator;
          }
          treeView.select(targetConfigurable);
          return false;
        }
        updateStatus(SettingsEditor.this.filter.context.getCurrentConfigurable());
        return true;
      }

      @Override
      void updateCurrent(Configurable configurable, boolean reset) {
        if (reset && configurable != null) {
          SettingsEditor.this.filter.context.fireReset(configurable);
        }
        checkModified(configurable);
      }

      @Override
      void openLink(Configurable configurable) {
        settings.select(configurable);
      }
    };
    editor.setPreferredSize(JBUI.size(800, 600));
    loadingDecorator = new LoadingDecorator(editor, this, 10, true);
    loadingDecorator.setOverlayBackground(LoadingDecorator.OVERLAY_BACKGROUND);
    myBanner = new Banner(editor.getResetAction());
    searchPanel.setBorder(JBUI.Borders.empty(7, 5, 6, 5));
    myBanner.setBorder(JBUI.Borders.empty(11, 6, 0, 10));
    search.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    searchPanel.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    JComponent left = new JPanel(new BorderLayout());
    left.add(BorderLayout.NORTH, searchPanel);
    left.add(BorderLayout.CENTER, treeView);
    JComponent right = new JPanel(new BorderLayout());
    right.add(BorderLayout.NORTH, withHistoryToolbar(myBanner));
    right.add(BorderLayout.CENTER, loadingDecorator.getComponent());
    mySplitter = new OnePixelSplitter(false, properties.getFloat(SPLITTER_PROPORTION, SPLITTER_PROPORTION_DEFAULT_VALUE));
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE);
    mySplitter.setFirstComponent(left);
    mySplitter.setSecondComponent(right);

    if (IdeFrameDecorator.Companion.isCustomDecorationActive()) {
      mySplitter.getDivider().setOpaque(false);
    }

    spotlightPainter = spotlightPainterFactory.createSpotlightPainter(project, editor, this, (painter) -> {
      Configurable currentConfigurable = this.filter.context.getCurrentConfigurable();
      if (treeView.getTree().hasFocus() || search.getTextEditor().hasFocus()) {
        painter.update(this.filter, currentConfigurable, editor.getContent(currentConfigurable));
      }
      return Unit.INSTANCE;
    });
    add(BorderLayout.CENTER, mySplitter);

    if (configurable == null) {
      String id = properties.getValue(SELECTED_CONFIGURABLE);
      configurable = ConfigurableVisitor.findById(id != null ? id : "preferences.lookFeel", groups);
      if (configurable == null) {
        configurable = ConfigurableVisitor.find(ConfigurableVisitor.ALL, groups);
      }
    }

    treeView.select(configurable).onProcessed(it -> this.filter.update(filter));

    Disposer.register(this, treeView);
    installSpotlightRemover();
    //noinspection CodeBlock2Expr
    search.getTextEditor().addActionListener(event -> {
      treeView.select(this.filter.context.getCurrentConfigurable()).onProcessed(o -> requestFocusToEditor());
    });

    for (ConfigurableGroup group : groups) {
      if (group instanceof MutableConfigurableGroup mutable) {
        Disposer.register(this, mutable);
        mutable.addListener(createReloadListener(groups));
      }
    }
  }

  @ApiStatus.Internal
  public void select(Configurable configurable) {
    treeView.select(configurable);
    editor.select(configurable);
    updateController(configurable);
  }

  @ApiStatus.Internal
  public @NotNull SettingsTreeView getTreeView() {
    return treeView;
  }

  private @NotNull MutableConfigurableGroup.Listener createReloadListener(List<? extends ConfigurableGroup> groups) {
    return new MutableConfigurableGroup.Listener() {
      @Override
      public void handleUpdate() {
        Configurable selected = editor.getConfigurable();
        String id = selected instanceof SearchableConfigurable ? ((SearchableConfigurable)selected).getId() : null;
        editor.reload();
        filter.reload();
        controllers.clear();
        lastController = null;

        Configurable candidate = id == null ? null :ConfigurableVisitor.findById(id, groups);
        if (candidate == null) {
          candidate = ConfigurableVisitor.findById(PluginManagerConfigurable.ID, groups);
        }
        editor.init(candidate, false);
        treeView.reloadWithSelection(candidate);
        settings.reload();
        invalidate();
        repaint();
      }
    };
  }

  private void requestFocusToEditor() {
    JComponent component = editor.getPreferredFocusedComponent();
    if (component != null) {
      IdeFocusManager.findInstanceByComponent(component).requestFocus(component, true);
    }
  }

  private void installSpotlightRemover() {
    final FocusAdapter spotlightRemover = new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        final Component comp = e.getOppositeComponent();
        if (comp == search.getTextEditor() || comp == treeView.getTree()) {
          return;
        }
        spotlightPainter.update(null, null, null);
      }

      @Override
      public void focusGained(FocusEvent e) {
        if (!StringUtil.isEmpty(search.getText())) {
          spotlightPainter.updateNow();
        }
      }
    };
    treeView.getTree().addFocusListener(spotlightRemover);
    search.getTextEditor().addFocusListener(spotlightRemover);
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
    place.putPath(SELECTED_CONFIGURABLE, properties.getValue(SELECTED_CONFIGURABLE));
  }

  @Override
  public @NotNull ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    Object path = place == null ? null : place.getPath(SELECTED_CONFIGURABLE);
    String id = path instanceof String ? (String)path : null;
    return settings.select(id == null ? null : settings.find(id));
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(History.KEY, myHistory);
    sink.set(Settings.KEY, settings);
    sink.set(SearchTextField.KEY, search);
  }

  @Override
  protected void disposeOnce() {
    if (properties == null || mySplitter == null) return; // if constructor failed
    properties.setValue(SPLITTER_PROPORTION, mySplitter.getProportion(), SPLITTER_PROPORTION_DEFAULT_VALUE);
  }

  @Override
  protected Action getApplyAction() {
    return editor.getApplyAction();
  }

  @Override
  protected Action getResetAction() {
    return null;
  }

  @Override
  protected String getHelpTopic() {
    Configurable configurable = filter.context.getCurrentConfigurable();
    while (configurable != null) {
      String topic = configurable.getHelpTopic();
      if (topic != null) {
        return topic;
      }
      configurable = filter.context.getParentConfigurable(configurable);
    }
    return "preferences";
  }

  @Override
  protected boolean apply() {
    return editor.apply();
  }

  @Override
  protected boolean cancel(AWTEvent source) {
    if (source instanceof KeyEvent && filter.context.isHoldingFilter()) {
      search.setText("");
      return false;
    }
    for (Configurable configurable : filter.context.getModified()) {
      configurable.cancel();
    }
    return super.cancel(source);
  }

  @Override
  protected JComponent getPreferredFocusedComponent() {
    return treeView != null ? treeView.getTree() : editor;
  }

  @Nullable
  Collection<@NlsContexts.ConfigurableName String> getPathNames() {
    return treeView == null ? null : treeView.getPathNames(filter.context.getCurrentConfigurable());
  }

  public void addOptionsListener(OptionsEditorColleague colleague) {
    filter.context.addColleague(colleague);
  }

  void updateStatus(Configurable configurable) {
    filter.updateSpotlight(configurable == null);
    if (editor != null) {
      ConfigurationException exception = filter.context.getErrors().get(configurable);
      editor.getApplyAction().setEnabled(!filter.context.getModified().isEmpty());
      editor.getResetAction().setEnabled(filter.context.isModified(configurable) || exception != null);
      editor.setError(exception);
      editor.revalidate();
    }
    if (configurable != null) {
      EdtScheduler.getInstance().schedule(300, () -> {
        if (!isDisposed && spotlightPainter != null) {
          spotlightPainter.updateNow();
        }
      });
    }
  }

  private void updateController(@Nullable Configurable configurable) {
    Project project = treeView.findConfigurableProject(configurable);
    myBanner.setProjectText(project != null ? getProjectText(project) : null);
    myBanner.setText(treeView.getPathNames(configurable));

    if (lastController != null) {
      lastController.setBanner(null);
      lastController = null;
    }

    ConfigurableController controller = ConfigurableController.getOrCreate(configurable, controllers);
    if (controller != null) {
      lastController = controller;
      controller.setBanner(myBanner);
    }
  }

  void checkModified(Configurable configurable) {
    Configurable parent = filter.context.getParentConfigurable(configurable);
    if (ConfigurableWrapper.hasOwnContent(parent)) {
      checkModifiedForItem(parent);
      for (Configurable child : filter.context.getChildren(parent)) {
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
      JComponent component = editor.getContent(configurable);
      if (component == null && ConfigurableWrapper.hasOwnContent(configurable)) {
        component = editor.readContent(configurable);
      }
      if (component != null) {
        checkModifiedInternal(configurable);
      }
    }
  }

  private void checkModifiedInternal(Configurable configurable) {
    if (configurable.isModified()) {
      filter.context.fireModifiedAdded(configurable, null);
    }
    else if (!filter.context.getErrors().containsKey(configurable)) {
      filter.context.fireModifiedRemoved(configurable, null);
    }
  }

  private static @NotNull @Nls String getProjectText(@NotNull Project project) {
    IdeUICustomization customization = IdeUICustomization.getInstance();
    return project.isDefault() ?
           customization.projectMessage("configurable.default.project.tooltip") :
           customization.projectMessage("configurable.current.project.tooltip");
  }
}
