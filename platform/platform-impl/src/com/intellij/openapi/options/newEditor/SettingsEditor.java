// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ide.actions.BackAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ide.actions.ForwardAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.BackedByPersistentState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.MutableConfigurableGroup;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.internal.statistic.collectors.fus.ui.SettingsCounterUsagesCollector;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;
import kotlin.Unit;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@ApiStatus.Internal
public final class SettingsEditor extends AbstractEditor implements UiDataProvider, Place.Navigator {
  private static final Logger LOG = Logger.getInstance(SettingsEditor.class);
  static final String SELECTED_CONFIGURABLE = "settings.editor.selected.configurable";
  private static final String SPLITTER_PROPORTION = "settings.editor.splitter.proportion";
  private static final float SPLITTER_PROPORTION_DEFAULT_VALUE = .2f;

  private final PropertiesComponent properties;
  private final Settings settings;
  private final SettingsSearch search;
  private final SettingsFilter filter;
  private final SettingsTreeView treeView;
  public final ConfigurableEditor editor;
  private final OnePixelSplitter mySplitter;
  private final SpotlightPainter spotlightPainter;
  private final LoadingDecorator loadingDecorator;
  private final @NotNull ConfigurableEditorBanner myBanner;
  private final @NotNull Project myProject;
  private final History myHistory = new History(this);
  /** Whether to auto-reset unmodified configurables when navigating back to them (non-modal windows). */
  private final boolean myUseLeaveState;
  private final Map<Configurable, Boolean> myLeaveState = new ConcurrentHashMap<>();
  private final @Nullable AnAction myExtraHeaderAction;
  private final Map<Configurable, ConfigurableController> controllers = new HashMap<>();
  private ConfigurableController lastController;

  /**
   * Snapshots of backing {@link PersistentStateComponent} states, captured when the window loses focus
   * for configurables that were modified at that time. Used to detect external changes on focus regain.
   * Key: configurable, Value: map from PersistentStateComponent to its serialized XML state.
   */
  private final Map<Configurable, Map<PersistentStateComponent<?>, Element>> myStateSnapshots = new HashMap<>();

  private final Breadcrumbs myBreadcrumbs = new Breadcrumbs() {
    @Override
    protected int getFontStyle(Crumb crumb) {
      return Font.BOLD;
    }
  };


  private final AbstractAction myResetAllAction = new AbstractAction(UIBundle.message("settings.reset.all.action.name")) {
    @Override
    public void actionPerformed(ActionEvent event) {
      reset();
    }
  };


  SettingsEditor(@NotNull Disposable parent,
                 @NotNull Project project,
                 @NotNull List<? extends ConfigurableGroup> groups,
                 @Nullable Configurable configurable,
                 @Nullable String filter,
                 boolean useLeaveState,
                 @NotNull ISettingsTreeViewFactory factory,
                 @NotNull SpotlightPainterFactory spotlightPainterFactory,
                 @Nullable AnAction extraHeaderAction) {
    super(parent);
    myProject = project;
    myUseLeaveState = useLeaveState;
    myExtraHeaderAction = extraHeaderAction;
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
        if (oldConfigurable != null) {
          checkModified(oldConfigurable);
          if (myUseLeaveState) {
            Boolean modified = isModifiedSafely(oldConfigurable);
            if (modified != null) myLeaveState.put(oldConfigurable, modified);
          }
        }
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
        Set<String> modifiedIds = new HashSet<>() ;
        Map<Configurable, ConfigurationException> map = new LinkedHashMap<>();
        for (Configurable configurable : SettingsEditor.this.filter.context.getModified()) {
          if (myLeaveState.get(configurable) == Boolean.FALSE) {
            // User did not explicitly modify this configurable; skip applying its stale component
            // values to avoid overwriting external or background changes.
            // Cascade: the user-modified configurable (e.g., Color Scheme Font) handles shared
            // state through its own apply(); its sibling (Console Font) must not clobber it.
            LOG.warn("apply: skipping '" + configurable.getDisplayName() + "' (leave-state=false)");
            continue;
          }
          ConfigurationException exception = ConfigurableEditor.apply(configurable);
          if (exception != null) {
            map.put(configurable, exception);
          }
          else if (!configurable.isModified()) {
            SettingsEditor.this.filter.context.fireModifiedRemoved(configurable, null);
            modifiedIds.add(ConfigurableVisitor.getId(configurable));
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
        ApplicationManager.getApplication().getMessageBus()
          .syncPublisher(SettingsDialogListener.TOPIC)
          .afterApply(SettingsEditor.this, modifiedIds);
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
      void postUpdateCurrent(Configurable configurable) {
        if (myUseLeaveState && configurable != null) {
          Boolean leaveState = myLeaveState.remove(configurable);
          LOG.debug("postUpdateCurrent: configurable=" + configurable.getDisplayName() + ", leaveState=" + leaveState);
          if (leaveState == Boolean.FALSE && Boolean.TRUE.equals(isModifiedSafely(configurable))) {
            LOG.warn("postUpdateCurrent: resetting " + configurable.getDisplayName());
            configurable.reset();
            SettingsEditor.this.filter.context.fireReset(configurable);
          }
        }
      }

      @Override
      void openLink(Configurable configurable) {
        settings.select(configurable);
      }
    };

    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(SettingsDialogListener.TOPIC, new SettingsDialogListener() {
        @Override
        public void afterApply(@NotNull SettingsEditor settingsEditor, @NotNull Set<@NotNull String> modifiedConfigurableIds) {
          if (settingsEditor == SettingsEditor.this)
            return;
          for (String id : modifiedConfigurableIds) {
            Configurable conf = ConfigurableVisitor.findById(id, groups);
            if (conf != null)
              checkModified(conf);
          }
          for (Configurable modifiedConfigurable : SettingsEditor.this.filter.context.getModified()) {
            String confId = ConfigurableVisitor.getId(modifiedConfigurable);
            if (!confId.equals(getSelectedConfigurableId()) && modifiedConfigurableIds.contains(confId)) {
              modifiedConfigurable.reset();
              SettingsEditor.this.filter.context.fireModifiedRemoved(modifiedConfigurable, null);
            }
          }
        }
      });


    loadingDecorator = new LoadingDecorator(editor, this, 10, true);
    loadingDecorator.setOverlayBackground(LoadingDecorator.OVERLAY_BACKGROUND);
    myBanner = new ConfigurableEditorBanner(editor.getResetAction(), myBreadcrumbs);
    searchPanel.setBorder(JBUI.Borders.empty(7, 5, 6, 5));
    myBanner.setBorder(JBUI.Borders.empty(11, 6, 0, 10));
    search.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    searchPanel.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    JComponent left = new JPanel(new BorderLayout());
    left.add(BorderLayout.CENTER, treeView);
    JPanel right = new JPanel(new BorderLayout());
    right.add(BorderLayout.CENTER, loadingDecorator.getComponent());
    mySplitter = new OnePixelSplitter(false, properties.getFloat(SPLITTER_PROPORTION, SPLITTER_PROPORTION_DEFAULT_VALUE));
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE);
    mySplitter.setFirstComponent(left);

    mySplitter.setSecondComponent(right);
    right.add(BorderLayout.NORTH, withHistoryToolbar(myBanner));
    left.add(BorderLayout.NORTH, searchPanel);
    editor.setPreferredSize(JBUI.size(800, 600));
    add(BorderLayout.CENTER, mySplitter);

    spotlightPainter = spotlightPainterFactory.createSpotlightPainter(project, editor, this, (painter) -> {
      Configurable currentConfigurable = this.filter.context.getCurrentConfigurable();
      if (treeView.getTree().hasFocus() || search.getTextEditor().hasFocus()) {
        painter.update(this.filter, currentConfigurable, editor.getContent(currentConfigurable));
      }
      return Unit.INSTANCE;
    });

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
  }

  boolean isSidebarVisible() {
    return mySplitter.getFirstComponent().isVisible();
  }

  void setSidebarVisible(boolean visible) {
    mySplitter.getFirstComponent().setVisible(visible);
  }

  @ApiStatus.Internal
  public @NotNull SettingsTreeView getTreeView() {
    return treeView;
  }

  SettingsSearch getSearch() {
    return search;
  }

  void setFilter(@Nullable String text) {
    filter.update(text);
  }

  void selectWithFilter(@NotNull Configurable configurable, @Nullable String filterText) {
    filter.update(filterText);
    treeView.refilterAndSelect(configurable);
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
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionUtil.copyFrom(new BackAction(), "Back"));
    group.add(ActionUtil.copyFrom(new ForwardAction(), "Forward"));
    if (myExtraHeaderAction != null) {
      group.add(myExtraHeaderAction);
    }
    JComponent toolbar = ActionUtil.createToolbarComponent(this, ActionPlaces.SETTINGS_HISTORY, group, true);
    toolbar.setOpaque(false);
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
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
    return myResetAllAction;
  }

  private void reset() {
    checkModified(filter.context.getCurrentConfigurable());
    for (Configurable configurable : filter.context.getModified()) {
      filter.context.fireReset(configurable);
      configurable.reset();
    }
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
    if (source instanceof KeyEvent && filter.context.isHoldingFilter) {
      search.setText("");
      return false;
    }
    for (Configurable configurable : filter.context.getModified()) {
      configurable.cancel();
      filter.context.fireReset(configurable);
    }
    return super.cancel(source);
  }

  @Override
  protected JComponent getPreferredFocusedComponent() {
    return treeView != null ? treeView.getTree() : editor;
  }

  void setHelpTooltip(@NotNull JButton helpButton) {
    if (UISettings.isIdeHelpTooltipEnabled()) {
      new HelpTooltip().setDescription(HtmlChunk.text(ActionsBundle.actionDescription("HelpTopics"))).installOn(helpButton);
    }
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
      boolean isModified = isModified();
      editor.getApplyAction().setEnabled(isModified);
      myResetAllAction.setEnabled(isModified);
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

  public boolean isModified() {
    return !filter.context.getModified().isEmpty();
  }

  public @NotNull Set<Configurable> getModifiedConfigurables() {
    return filter.context.getModified();
  }

  /**
   * Calls {@link Configurable#isModified()} on {@code configurable} and returns the result,
   * or {@code null} if the call throws (with a warning logged).
   * Some configurables (e.g. {@code CustomizationConfigurable}) NPE before
   * {@link Configurable#createComponent()} has been called.
   */
  private static @Nullable Boolean isModifiedSafely(@NotNull Configurable configurable) {
    try {
      return configurable.isModified();
    }
    catch (Exception e) {
      LOG.warn("isModified() failed for " + configurable.getDisplayName(), e);
      return null;
    }
  }

  /**
   * Records the current configurable's modified state at window deactivation time.
   * Call this when the settings window loses focus so the result can be used on reactivation.
   * Also, snapshots the backing {@link PersistentStateComponent} states for all modified
   * configurables that implement {@link BackedByPersistentState}, so that external changes
   * can be detected on focus regain
   */
  public void recordWindowLeaveState() {
    Configurable current = filter.context.getCurrentConfigurable();
    if (current != null) {
      Boolean modified = isModifiedSafely(current);
      if (modified != null) myLeaveState.put(current, modified);
    }
    snapshotModifiedConfigurablesState();
  }

  /**
   * Resets the current configurable if it had no user edits when the window lost focus
   * (myLeaveState=false) but is now isModified=true (external/background change).
   * Call this when the settings window regains focus so external changes become visible.
   * The leave-state entry is consumed (removed) regardless, so any subsequent user edits
   * are not blocked by a stale entry in the apply loop.
   * Non-current configurables are handled lazily: reset on navigation via postUpdateCurrent,
   * and protected at apply time by the myLeaveState skip in the apply loop.
   * Also detects external changes to backing state for modified configurables.
   */
  public void resetUnmodifiedOnWindowFocus() {
    Configurable current = filter.context.getCurrentConfigurable();
    if (current == null) return;
    Boolean leaveState = myLeaveState.remove(current);
    Boolean isModified = isModifiedSafely(current);
    if (isModified == null) return;
    LOG.debug("resetUnmodifiedOnWindowFocus: current=" + current.getDisplayName() + ", leaveState=" + leaveState + ", isModified=" + isModified);
    if (leaveState == Boolean.FALSE && isModified) {
      LOG.warn("resetUnmodifiedOnWindowFocus: resetting " + current.getDisplayName());
      current.reset();
      filter.context.fireReset(current);
    }
    detectExternalChangesOnFocusGain();
  }

  private void snapshotModifiedConfigurablesState() {
    myStateSnapshots.clear();
    for (Configurable c : filter.context.getModified()) {
      BackedByPersistentState backed = ConfigurableWrapper.cast(BackedByPersistentState.class, c);
      if (backed == null) continue;
      Map<PersistentStateComponent<?>, Element> snapshots = new LinkedHashMap<>();
      for (PersistentStateComponent<?> psc : backed.getBackingComponents()) {
        Element snapshot = snapshotOf(psc);
        if (snapshot != null) {
          snapshots.put(psc, snapshot);
        }
      }
      if (!snapshots.isEmpty()) {
        myStateSnapshots.put(c, snapshots);
      }
    }
  }

  private void detectExternalChangesOnFocusGain() {
    for (Map.Entry<Configurable, Map<PersistentStateComponent<?>, Element>> entry : myStateSnapshots.entrySet()) {
      Configurable c = entry.getKey();
      for (Map.Entry<PersistentStateComponent<?>, Element> pscEntry : entry.getValue().entrySet()) {
        PersistentStateComponent<?> psc = pscEntry.getKey();
        Element oldSnapshot = pscEntry.getValue();
        Element currentSnapshot = snapshotOf(psc);
        if (currentSnapshot != null && !JDOMUtil.areElementsEqual(oldSnapshot, currentSnapshot)) {
          String message = UIBundle.message("settings.external.change.conflict.notification",
                                             c.getDisplayName(), psc.getClass().getName());
          LOG.warn(message);
          if (Registry.is("ide.settings.external.change.conflict.show.notification")) {
            NotificationGroupManager.getInstance()
              .getNotificationGroup("Settings External Change Conflict")
              .createNotification(message, NotificationType.WARNING)
              .notify(myProject);
          }
          SettingsCounterUsagesCollector.EXTERNAL_CHANGE_WHILE_MODIFIED.log(
            (c instanceof ConfigurableWrapper w ? w.getConfigurable() : c).getClass());
        }
      }
    }
    myStateSnapshots.clear();
  }

  private static @Nullable Element snapshotOf(@NotNull PersistentStateComponent<?> psc) {
    try {
      Object state = psc.getState();
      if (state == null) return null;
      return XmlSerializer.serialize(state);
    }
    catch (Exception e) {
      LOG.debug("Failed to snapshot state of " + psc.getClass().getName(), e);
      return null;
    }
  }

  public String getSelectedConfigurableId() {
    Configurable configurable = editor.getConfigurable();
    if (configurable == null) {
      return null;
    }
    return ConfigurableVisitor.getId(configurable);
  }

  private void updateController(@Nullable Configurable configurable) {
    Project project = treeView.findConfigurableProject(configurable);
    myBanner.setProjectText(project != null ? getProjectText(project) : null);
    Collection<@NlsContexts.ConfigurableName String> pathNames = treeView.getPathNames(configurable);
    List<Crumb> crumbs = new ArrayList<>();
    if (!pathNames.isEmpty()) {
      List<Action> actions = CopySettingsPathAction.createSwingActions(() -> pathNames);
      for (@NlsContexts.ConfigurableName String name : pathNames) {
        crumbs.add(new Crumb.Impl(null, name, null, actions));
      }
    }
    myBreadcrumbs.setCrumbs(crumbs);

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
    if (parent != null && ConfigurableWrapper.hasOwnContent(parent)) {
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

  private void checkModifiedForItem(@NotNull Configurable configurable) {
    JComponent component = editor.getContent(configurable);
    if (component == null && ConfigurableWrapper.hasOwnContent(configurable)) {
      component = editor.readContent(configurable);
    }
    if (component != null) {
      checkModifiedInternal(configurable);
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
