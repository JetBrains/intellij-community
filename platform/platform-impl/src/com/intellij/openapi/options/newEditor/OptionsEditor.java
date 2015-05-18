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

import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EdtRunnable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.LightColors;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class OptionsEditor extends JPanel implements DataProvider, Place.Navigator, Disposable, AWTEventListener {
  public static DataKey<OptionsEditor> KEY = DataKey.create("options.editor");

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.newEditor.OptionsEditor");

  @NonNls private static final String MAIN_SPLITTER_PROPORTION = "options.splitter.main.proportions";
  @NonNls private static final String DETAILS_SPLITTER_PROPORTION = "options.splitter.details.proportions";

  @NonNls private static final String NOT_A_NEW_COMPONENT = "component.was.already.instantiated";

  private final History myHistory = new History(this);

  private final OptionsTree myTree;
  private final SettingsTreeView myTreeView;
  private final SettingsSearch mySearch;
  private final Splitter myMainSplitter;
  //[back/forward] JComponent myToolbarComponent;

  private final DetailsComponent myOwnDetails =
    new DetailsComponent().setEmptyContentText("Select configuration element in the tree to edit its settings");
  private final ContentWrapper myContentWrapper = new ContentWrapper();


  private final Map<Configurable, ConfigurableContent> myConfigurable2Content = new HashMap<Configurable, ConfigurableContent>();
  private final Map<Configurable, ActionCallback> myConfigurable2LoadCallback = new HashMap<Configurable, ActionCallback>();

  private final MergingUpdateQueue myModificationChecker;

  private final SpotlightPainter mySpotlightPainter = new SpotlightPainter(myOwnDetails.getContentGutter(), this) {
    void updateNow() {
      Configurable configurable = getContext().getCurrentConfigurable();
      update(myFilter, configurable, myConfigurable2Content.containsKey(configurable) ? myContentWrapper : null);
    }
  };
  private final LoadingDecorator myLoadingDecorator;
  private final SettingsFilter myFilter;

  private final Wrapper mySearchWrapper = new Wrapper();
  private final JPanel myLeftSide;

  //[back/forward] private ActionToolbar myToolbar;
  private Window myWindow;
  private final PropertiesComponent myProperties;
  private volatile boolean myDisposed;

  final Settings mySettings;

  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProperties = PropertiesComponent.getInstance(project);

    mySettings = new Settings(groups) {
      @Override
      protected ActionCallback selectImpl(Configurable configurable) {
        return OptionsEditor.this.select(configurable, "");
      }
    };

    mySearch = new SettingsSearch() {
      @Override
      protected void onTextKeyEvent(final KeyEvent e) {
        if (myTreeView != null) {
          myTreeView.myTree.processKeyEvent(e);
        }
        else {
          myTree.processTextEvent(e);
        }
      }

      @Override
      void delegateKeyEvent(KeyEvent event) {
        myFilter.myDocumentWasChanged = false;
        try {
          super.delegateKeyEvent(event);
        }
        finally {
          if (myFilter.myDocumentWasChanged && !isFilterFieldVisible()) {
            setFilterFieldVisible(true, false, false);
          }
        }
      }
    };
    if (Registry.is("ide.new.settings.dialog")) {
      mySearch.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
      mySearch.setBorder(new EmptyBorder(5, 10, 2, 10));
    }

    myFilter = new SettingsFilter(project, groups, mySearch) {
      @Override
      Configurable getConfigurable(SimpleNode node) {
        if (node instanceof OptionsTree.EditorNode) {
          return ((OptionsTree.EditorNode)node).getConfigurable();
        }
        return SettingsTreeView.getConfigurable(node);
      }

      @Override
      SimpleNode findNode(Configurable configurable) {
        return myTreeView != null
               ? myTreeView.findNode(configurable)
               : myTree.findNodeFor(configurable);
      }

      @Override
      void updateSpotlight(boolean now) {
        if (!now) {
          mySpotlightPainter.updateLater();
        }
        else {
          mySpotlightPainter.updateNow();
        }
      }
    };

    if (Registry.is("ide.new.settings.dialog")) {
      myTreeView = new SettingsTreeView(myFilter, groups);
      myTreeView.myTree.addKeyListener(mySearch);
      myTree = null;
    }
    else {
      myTreeView = null;
      myTree = new OptionsTree(myFilter, groups);
      myTree.addKeyListener(mySearch);
    }

    getContext().addColleague(myTreeView != null ? myTreeView : myTree);
    Disposer.register(this, myTreeView != null ? myTreeView : myTree);

    /* [back/forward]
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.add(new BackAction(myTree));
    toolbarActions.add(new ForwardAction(myTree));
    toolbarActions.addSeparator();
    toolbarActions.add(new ShowSearchFieldAction(this));
    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, true);
    myToolbar.setTargetComponent(this);
    myToolbarComponent = myToolbar.getComponent();

    myHistory.addListener(new HistoryListener.Adapter() {
      @Override
      public void navigationFinished(final Place from, final Place to) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            if (myToolbarComponent.isShowing()) {
              myToolbar.updateActionsImmediately();
            }
          }
        });
      }
    }, this);
    */


    myLeftSide = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getMinimumSize() {
        Dimension dimension = super.getMinimumSize();
        JComponent component = myTreeView != null ? myTreeView : myTree;
        dimension.width = Math.max(component.getMinimumSize().width, mySearchWrapper.getPreferredSize().width);
        return dimension;
      }
    };

    /* [back/forward]

    final NonOpaquePanel toolbarPanel = new NonOpaquePanel(new BorderLayout());
    toolbarPanel.add(myToolbarComponent, BorderLayout.WEST);
    toolbarPanel.add(mySearchWrapper, BorderLayout.CENTER);
    */

    myLeftSide.add(mySearchWrapper, BorderLayout.NORTH);
    myLeftSide.add(myTreeView != null ? myTreeView : myTree, BorderLayout.CENTER);

    setLayout(new BorderLayout());

    myMainSplitter = Registry.is("ide.new.settings.dialog") ? new OnePixelSplitter(false) : new Splitter(false);
    myMainSplitter.setFirstComponent(myLeftSide);

    myLoadingDecorator = new LoadingDecorator(myOwnDetails.getComponent(), this, 150);
    myMainSplitter.setSecondComponent(myLoadingDecorator.getComponent());


    myMainSplitter.setProportion(readProportion(0.3f, MAIN_SPLITTER_PROPORTION));
    myContentWrapper.mySplitter.setProportion(readProportion(0.2f, DETAILS_SPLITTER_PROPORTION));

    add(myMainSplitter, BorderLayout.CENTER);

    MyColleague colleague = new MyColleague();
    getContext().addColleague(colleague);

    if (preselectedConfigurable != null) {
      selectInTree(preselectedConfigurable);
    }
    else {
      if (myTreeView != null) {
        myTreeView.selectFirst();
      }
      else {
        myTree.selectFirst();
      }
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(this,
                                                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

    ActionManager.getInstance().addAnActionListener(new AnActionListener() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      }

      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        queueModificationCheck();
      }

      @Override
      public void beforeEditorTyping(char c, DataContext dataContext) {
      }
    }, this);

    myModificationChecker = new MergingUpdateQueue("OptionsModificationChecker", 1000, false, this, this, this);

    /*
    String visible = PropertiesComponent.getInstance(myProject).getValue(SEARCH_VISIBLE);
    if (visible == null) {
      visible = "true";
    }
    */

    setFilterFieldVisible(true, false, false);

    new UiNotifyConnector.Once(this, new Activatable() {
      @Override
      public void showNotify() {
        myWindow = SwingUtilities.getWindowAncestor(OptionsEditor.this);
      }

      @Override
      public void hideNotify() {
      }
    });
  }

  private ActionCallback selectInTree(Configurable configurable) {
    return myTreeView != null
           ? myTreeView.select(configurable)
           : myTree.select(configurable);
  }

  /** @see #select(Configurable) */
  @Deprecated
  public ActionCallback select(Class<? extends Configurable> configurableClass) {
    final Configurable configurable = findConfigurable(configurableClass);
    if (configurable == null) {
      return new ActionCallback.Rejected();
    }
    return select(configurable);
  }

  /** @see #findConfigurableById(String) */
  @Deprecated
  @Nullable
  public <T extends Configurable> T findConfigurable(Class<T> configurableClass) {
    return myTreeView != null
           ? myTreeView.findConfigurable(configurableClass)
           : myTree.findConfigurable(configurableClass);
  }

  @Nullable
  public SearchableConfigurable findConfigurableById(@NotNull String configurableId) {
    return myTreeView != null
           ? myTreeView.findConfigurableById(configurableId)
           : myTree.findConfigurableById(configurableId);
  }

  public ActionCallback clearSearchAndSelect(Configurable configurable) {
    clearFilter();
    return select(configurable, "");
  }

  public ActionCallback select(Configurable configurable) {
    if (myFilter.getFilterText().isEmpty()) {
      return select(configurable, "");
    }
    else {
      return myFilter.update(true, true);
    }
  }

  public ActionCallback select(Configurable configurable, final String text) {
    myFilter.update(text, false, true);
    return selectInTree(configurable);
  }

  private float readProportion(final float defaultValue, final String propertyName) {
    float proportion = defaultValue;
    try {
      final String p = myProperties.getValue(propertyName);
      if (p != null) {
        proportion = Float.valueOf(p);
      }
    }
    catch (NumberFormatException e) {
      LOG.debug(e);
    }
    return proportion;
  }

  private ActionCallback processSelected(final Configurable configurable, final Configurable oldConfigurable) {
    if (isShowing(configurable)) return new ActionCallback.Done();

    final ActionCallback result = new ActionCallback();

    if (configurable == null) {
      myOwnDetails.setContent(null);

      myFilter.updateSpotlight(true);
      checkModified(oldConfigurable);

      result.setDone();

    } else {
      getUiFor(configurable).doWhenDone(new EdtRunnable() {
        @Override
        public void runEdt() {
          if (myDisposed) return;

          final Configurable current = getContext().getCurrentConfigurable();
          if (current != configurable) {
            result.setRejected();
            return;
          }

          myHistory.pushQueryPlace();

          updateDetails();

          myOwnDetails.setContent(myContentWrapper);
          myOwnDetails.setBannerMinHeight(mySearchWrapper.getHeight());
          myOwnDetails.setText(getBannerText(configurable));
          if (myTreeView != null) {
            myOwnDetails.forProject(myTreeView.findConfigurableProject(configurable));
          }
          else if (Registry.is("ide.new.settings.dialog")) {
            myOwnDetails.forProject(myTree.getConfigurableProject(configurable));
          }

          final ConfigurableContent content = myConfigurable2Content.get(current);

          content.setText(getBannerText(configurable));
          content.setBannerActions(new Action[] {new ResetAction(configurable)});

          content.updateBannerActions();

          myLoadingDecorator.stopLoading();

          myFilter.updateSpotlight(false);

          checkModified(oldConfigurable);
          checkModified(configurable);

          FilteringTreeBuilder builder = myTreeView != null ? myTreeView.myBuilder : myTree.myBuilder;
          if (builder.getSelectedElements().size() == 0) {
            select(configurable).notify(result);
          } else {
            result.setDone();
          }
        }
      });
    }

    return result;
  }

  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  private ActionCallback getUiFor(final Configurable configurable) {
    assertIsDispatchThread();

    if (myDisposed) {
      return new ActionCallback.Rejected();
    }

    final ActionCallback result = new ActionCallback();

    if (!myConfigurable2Content.containsKey(configurable)) {

      final ActionCallback readyCallback = myConfigurable2LoadCallback.get(configurable);
      if (readyCallback != null) {
        return readyCallback;
      }

      myConfigurable2LoadCallback.put(configurable, result);
      myLoadingDecorator.startLoading(false);
      final Application app = ApplicationManager.getApplication();
      Runnable action = new Runnable() {
        @Override
        public void run() {
          app.runReadAction(new Runnable() {
            @Override
            public void run() {
              ((ApplicationEx)app).runEdtSafeAction(new Runnable() {
                @Override
                public void run() {
                  if (myFilter.myProject.isDisposed()) {
                    result.setRejected();
                  }
                  else {
                    initConfigurable(configurable).notifyWhenDone(result);
                  }
                }
              });
            }
          });
        }
      };
      if (app.isUnitTestMode()) {
        action.run();
      }
      else {
        app.executeOnPooledThread(action);
      }
    }
    else {
      result.setDone();
    }

    return result;
  }

  private ActionCallback initConfigurable(@NotNull final Configurable configurable) {
    final ActionCallback result = new ActionCallback();

    final ConfigurableContent content = new Simple(configurable);

    if (!myConfigurable2Content.containsKey(configurable)) {
      if (configurable instanceof Place.Navigator) {
        ((Place.Navigator)configurable).setHistory(myHistory);
      }
      configurable.reset();
    }

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myDisposed) return;
        myConfigurable2Content.put(configurable, content);
        result.setDone();
      }
    });

    return result;
  }

  private String[] getBannerText(Configurable configurable) {
    if (myTreeView != null) {
      return myTreeView.getPathNames(configurable);
    }
    final List<Configurable> list = myTree.getPathToRoot(configurable);
    final String[] result = new String[list.size()];
    int add = 0;
    for (int i = list.size() - 1; i >=0; i--) {
      result[add++] = list.get(i).getDisplayName().replace('\n', ' ');
    }
    return result;
  }

  private void checkModified(final Configurable configurable) {
    fireModification(configurable);
  }

  private void fireModification(final Configurable actual) {

    Collection<Configurable> toCheck = collectAllParentsAndSiblings(actual);

    for (Configurable configurable : toCheck) {
      fireModificationForItem(configurable);
    }

  }

  private Collection<Configurable> collectAllParentsAndSiblings(final Configurable actual) {
    ArrayList<Configurable> result = new ArrayList<Configurable>();
    Configurable nearestParent = getContext().getParentConfigurable(actual);

    if (nearestParent != null) {
      Configurable parent = nearestParent;
      while (parent != null) {
        result.add(parent);
        parent = getContext().getParentConfigurable(parent);
      }

      result.addAll(getContext().getChildren(nearestParent));
    } else {
      result.add(actual);
    }


    return result;
  }

  private void fireModificationForItem(final Configurable configurable) {
    if (configurable != null) {
      if (!myConfigurable2Content.containsKey(configurable) && ConfigurableWrapper.hasOwnContent(configurable)) {

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                if (myDisposed) return;
                initConfigurable(configurable).doWhenDone(new Runnable() {
                  @Override
                  public void run() {
                    if (myDisposed) return;
                    fireModificationInt(configurable);
                  }
                });
              }
            });
          }
        });
      }
      else if (myConfigurable2Content.containsKey(configurable)) {
        fireModificationInt(configurable);
      }
    }
  }

  private void fireModificationInt(final Configurable configurable) {
    if (configurable.isModified()) {
      getContext().fireModifiedAdded(configurable, null);
    } else if (!configurable.isModified() && !getContext().getErrors().containsKey(configurable)) {
      getContext().fireModifiedRemoved(configurable, null);
    }
  }

  private void updateDetails() {
    final Configurable current = getContext().getCurrentConfigurable();

    assert current != null;

    final ConfigurableContent content = myConfigurable2Content.get(current);
    content.set(myContentWrapper);
  }

  private boolean isShowing(Configurable configurable) {
    final ConfigurableContent content = myConfigurable2Content.get(configurable);
    return content != null && content.isShowing();
  }

  @Nullable
  public String getHelpTopic() {
    Configurable current = getContext().getCurrentConfigurable();
    while (current != null) {
      String topic = current.getHelpTopic();
      if (topic != null) return topic;
      current = getContext().getParentConfigurable(current);
    }
    return null;
  }

  public boolean isFilterFieldVisible() {
    return mySearch.getParent() == mySearchWrapper;
  }

  public void setFilterFieldVisible(final boolean visible, boolean requestFocus, boolean checkFocus) {
    if (isFilterFieldVisible() && checkFocus && requestFocus && !isSearchFieldFocused()) {
      UIUtil.requestFocus(mySearch);
      return;
    }

    mySearchWrapper.setContent(visible ? mySearch : null);

    myLeftSide.revalidate();
    myLeftSide.repaint();

    if (visible && requestFocus) {
      UIUtil.requestFocus(mySearch);
    }
  }

  public boolean isSearchFieldFocused() {
    return mySearch.getTextEditor().isFocusOwner();
  }

  private class ResetAction extends AbstractAction {
    Configurable myConfigurable;

    ResetAction(final Configurable configurable) {
      myConfigurable = configurable;
      putValue(NAME, "Reset");
      putValue(SHORT_DESCRIPTION, "Rollback changes for this configuration element");
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
      reset(myConfigurable, true);
      checkModified(myConfigurable);
    }

    @Override
    public boolean isEnabled() {
      return myFilter.myContext.isModified(myConfigurable) || getContext().getErrors().containsKey(myConfigurable);
    }
  }

  private static class ContentWrapper extends NonOpaquePanel {

    private final JLabel myErrorLabel;

    private JComponent mySimpleContent;
    private ConfigurationException myException;


    private JComponent myMaster;
    private JComponent myToolbar;
    private DetailsComponent myDetails;

    private final Splitter mySplitter = new Splitter(false);
    private JPanel myLeft = new JPanel(new BorderLayout());
    public float myLastSplitterProportion;

    private ContentWrapper() {
      setLayout(new BorderLayout());
      myErrorLabel = new JLabel();
      myErrorLabel.setOpaque(true);
      myErrorLabel.setBackground(LightColors.RED);

      myLeft = new JPanel(new BorderLayout());

      mySplitter.addPropertyChangeListener(Splitter.PROP_PROPORTION, new PropertyChangeListener() {
        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
          myLastSplitterProportion = ((Float)evt.getNewValue()).floatValue();
        }
      });
    }

    void setContent(JComponent c, ConfigurationException e, boolean scrollable) {
      if (c != null && mySimpleContent == c && myException == e) return;

      removeAll();

      if (c != null) {
        if (scrollable) {
          JScrollPane scroll = ScrollPaneFactory.createScrollPane(null, true);
          scroll.setViewport(new GradientViewport(c, JBUI.insets(5), false));
          scroll.getVerticalScrollBar().setUnitIncrement(JBUI.scale(10));
          add(scroll, BorderLayout.CENTER);
        }
        else {
          add(c, BorderLayout.CENTER);
        }
      }

      if (e != null) {
        myErrorLabel.setText(UIUtil.toHtml(e.getMessage()));
        add(myErrorLabel, BorderLayout.NORTH);
      }

      mySimpleContent = c;
      myException = e;

      myMaster = null;
      myToolbar = null;
      myDetails = null;
      mySplitter.setFirstComponent(null);
      mySplitter.setSecondComponent(null);
    }

    void setContent(JComponent master, JComponent toolbar, DetailsComponent details, ConfigurationException e) {
      if (myMaster == master && myToolbar == toolbar && myDetails == details && myException == e) return;

      myMaster = master;
      myToolbar = toolbar;
      myDetails = details;
      myException = e;


      removeAll();
      myLeft.removeAll();

      myLeft.add(myToolbar, BorderLayout.NORTH);
      myLeft.add(myMaster, BorderLayout.CENTER);

      myDetails.setBannerMinHeight(myToolbar.getPreferredSize().height);

      mySplitter.setFirstComponent(myLeft);
      mySplitter.setSecondComponent(myDetails.getComponent());
      mySplitter.setProportion(myLastSplitterProportion);

      add(mySplitter, BorderLayout.CENTER);

      mySimpleContent = null;
    }


    @Override
    public boolean isNull() {
      final boolean superNull = super.isNull();
      if (superNull) return superNull;

      if (myMaster == null) {
        return NullableComponent.Check.isNull(mySimpleContent);
      } else {
        return NullableComponent.Check.isNull(myMaster);
      }
    }
  }

  public void reset(Configurable configurable, boolean notify) {
    configurable.reset();
    if (notify) {
      getContext().fireReset(configurable);
    }
  }

  public void apply() {
    Map<Configurable, ConfigurationException> errors = new LinkedHashMap<Configurable, ConfigurationException>();
    final Set<Configurable> modified = getContext().getModified();
    for (Configurable each : modified) {
      try {
        each.apply();
        UsageTrigger.trigger("ide.settings." + ConvertUsagesUtil.escapeDescriptorName(each.getDisplayName()));
        if (!each.isModified()) {
          getContext().fireModifiedRemoved(each, null);
        }
      }
      catch (ConfigurationException e) {
        errors.put(each, e);
        LOG.debug(e);
      }
    }

    getContext().fireErrorsChanged(errors, null);

    if (!errors.isEmpty()) {
      selectInTree(errors.keySet().iterator().next());
    }
  }


  @Override
  public Object getData(@NonNls final String dataId) {
    if (Settings.KEY.is(dataId)) {
      return mySettings;
    }
    if (KEY.is(dataId)) {
      return this;
    }
    return History.KEY.is(dataId) ? myHistory : null;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTreeView != null ? myTreeView.myTree : mySearch;//myTree.getTree();
  }

  @Override
  public Dimension getPreferredSize() {
    return JBUI.size(1200, 768);
  }

  @Override
  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    final Configurable config = (Configurable)place.getPath("configurable");
    final String filter = (String)place.getPath("filter");

    final ActionCallback result = new ActionCallback();

    myFilter.update(filter, false, true).doWhenDone(new Runnable() {
      @Override
      public void run() {
        selectInTree(config).notifyWhenDone(result);
      }
    });

    return result;
  }

  @Override
  public void queryPlace(@NotNull final Place place) {
    final Configurable current = getContext().getCurrentConfigurable();
    place.putPath("configurable", current);
    place.putPath("filter", myFilter.getFilterText());

    if (current instanceof Place.Navigator) {
      ((Place.Navigator)current).queryPlace(place);
    }
  }

  @Override
  public void dispose() {
    assertIsDispatchThread();

    if (myDisposed) {
      return;
    }

    myDisposed = true;

    myProperties.setValue(MAIN_SPLITTER_PROPORTION, String.valueOf(myMainSplitter.getProportion()));
    myProperties.setValue(DETAILS_SPLITTER_PROPORTION, String.valueOf(myContentWrapper.myLastSplitterProportion));

    Toolkit.getDefaultToolkit().removeAWTEventListener(this);


    final Set<Configurable> configurables = new HashSet<Configurable>();
    configurables.addAll(myConfigurable2Content.keySet());
    configurables.addAll(myConfigurable2LoadCallback.keySet());
    for (final Configurable each : configurables) {
      ActionCallback loadCb = myConfigurable2LoadCallback.get(each);
      if (loadCb != null) {
        loadCb.doWhenProcessed(new Runnable() {
          @Override
          public void run() {
            assertIsDispatchThread();
            each.disposeUIResources();
          }
        });
      } else {
        each.disposeUIResources();
      }
    }

    Disposer.clearOwnFields(this);
  }

  public OptionsEditorContext getContext() {
    return myFilter.myContext;
  }

  private class MyColleague extends OptionsEditorColleague.Adapter {
    @Override
    public ActionCallback onSelected(final Configurable configurable, final Configurable oldConfigurable) {
      return processSelected(configurable, oldConfigurable);
    }

    @Override
    public ActionCallback onModifiedRemoved(final Configurable configurable) {
      return updateIfCurrent(configurable);
    }

    @Override
    public ActionCallback onModifiedAdded(final Configurable configurable) {
      return updateIfCurrent(configurable);
    }

    @Override
    public ActionCallback onErrorsChanged() {
      return updateIfCurrent(getContext().getCurrentConfigurable());
    }

    private ActionCallback updateIfCurrent(final Configurable configurable) {
      if (getContext().getCurrentConfigurable() == configurable && configurable != null) {
        updateDetails();
        final ConfigurableContent content = myConfigurable2Content.get(configurable);
        content.updateBannerActions();
        return new ActionCallback.Done();
      } else {
        return new ActionCallback.Rejected();
      }
    }
  }

  public void flushModifications() {
    fireModification(getContext().getCurrentConfigurable());
  }

  public boolean canApply() {
    return !getContext().getModified().isEmpty();
  }

  @Override
  public void eventDispatched(final AWTEvent event) {
    if (event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED || event.getID() == MouseEvent.MOUSE_DRAGGED) {
      final MouseEvent me = (MouseEvent)event;
      if (SwingUtilities.isDescendingFrom(me.getComponent(), SwingUtilities.getWindowAncestor(myContentWrapper)) || isPopupOverEditor(me.getComponent())) {
        queueModificationCheck();
      }
    }
    else if (event.getID() == KeyEvent.KEY_PRESSED || event.getID() == KeyEvent.KEY_RELEASED) {
      final KeyEvent ke = (KeyEvent)event;
      if (SwingUtilities.isDescendingFrom(ke.getComponent(), myContentWrapper)) {
        queueModificationCheck();
      }
    }
  }

  private void queueModificationCheck() {
    final Configurable configurable = getContext().getCurrentConfigurable();
    myModificationChecker.queue(new Update(this) {
      @Override
      public void run() {
        checkModified(configurable);
      }

      @Override
      public boolean isExpired() {
        return getContext().getCurrentConfigurable() != configurable;
      }
    });
  }

  private boolean isPopupOverEditor(Component c) {
    final Window wnd = SwingUtilities.getWindowAncestor(c);
    return (wnd instanceof JWindow || wnd instanceof JDialog && ((JDialog)wnd).getModalityType() == Dialog.ModalityType.MODELESS) && myWindow != null && wnd.getParent() == myWindow;
  }

  private abstract static class ConfigurableContent {
    abstract void set(ContentWrapper wrapper);

    abstract boolean isShowing();

    abstract void setBannerActions(Action[] actions);

    abstract void updateBannerActions();

    abstract void setText(final String[] bannerText);
  }

  /**
   * Returns default view for the specified configurable.
   * It uses the configurable identifier to retrieve description.
   *
   * @param searchable the configurable that does not have any view
   * @return default view for the specified configurable
   */
  private JComponent createDefaultComponent(SearchableConfigurable searchable) {
    JPanel panel = new JPanel(new BorderLayout(0, 9));
    try {
      panel.add(BorderLayout.NORTH, new JLabel(getDefaultDescription(searchable)));
    }
    catch (AssertionError error) {
      return null; // description is not set
    }
    if (searchable instanceof Configurable.Composite) {
      JPanel box = new JPanel();
      box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
      panel.add(BorderLayout.CENTER, box);

      Configurable.Composite composite = (Configurable.Composite)searchable;
      for (final Configurable configurable : composite.getConfigurables()) {
        LinkLabel label = new LinkLabel(configurable.getDisplayName(), null) {
          @Override
          public void doClick() {
            select(configurable, null);
          }
        };
        label.setBorder(BorderFactory.createEmptyBorder(1, 17, 3, 1));
        box.add(label);
      }
    }
    return panel;
  }

  @NotNull
  private static String getDefaultDescription(SearchableConfigurable configurable) {
    String key = configurable.getId() + ".settings.description";
    if (configurable instanceof ConfigurableWrapper) {
      ConfigurableWrapper wrapper = (ConfigurableWrapper) configurable;
      ResourceBundle resourceBundle = wrapper.getExtensionPoint().findBundle();
      if (resourceBundle != null) {
        return CommonBundle.message(resourceBundle, key);
      }
    }
    return OptionsBundle.message(key);
  }

  private class Simple extends ConfigurableContent {
    JComponent myComponent;
    Configurable myConfigurable;

    Simple(final Configurable configurable) {
      myConfigurable = configurable;
      myComponent = configurable.createComponent();
      if (myComponent == null && configurable instanceof SearchableConfigurable) {
        myComponent = createDefaultComponent((SearchableConfigurable)configurable);
      }
      if (myComponent != null) {
        final Object clientProperty = myComponent.getClientProperty(NOT_A_NEW_COMPONENT);
        if (clientProperty != null && ApplicationManager.getApplication().isInternal()) {
          LOG.warn("Settings component for " + configurable.getClass()+ " must be created anew, not reused, in createComponent() and destroyed in disposeUIResources()");
        }
        else {
          myComponent.putClientProperty(NOT_A_NEW_COMPONENT, Boolean.TRUE);
        }
      }
    }

    @Override
    void set(final ContentWrapper wrapper) {
      myOwnDetails.setDetailsModeEnabled(true);
      boolean noScroll = ConfigurableWrapper.isNoScroll(myConfigurable) ||
                         ConfigurableWrapper.cast(MasterDetails.class, myConfigurable) != null;
      wrapper.setContent(myComponent, getContext().getErrors().get(myConfigurable), !noScroll);
    }

    @Override
    boolean isShowing() {
      return myComponent != null && myComponent.isShowing();
    }

    @Override
    void setBannerActions(final Action[] actions) {
      myOwnDetails.setBannerActions(actions);
    }

    @Override
    void updateBannerActions() {
      myOwnDetails.updateBannerActions();
    }

    @Override
    void setText(final String[] bannerText) {
      myOwnDetails.setText(bannerText);
    }
  }

  private class Details extends ConfigurableContent {
    MasterDetails myConfigurable;
    DetailsComponent myDetails;
    JComponent myMaster;
    JComponent myToolbar;

    Details(final MasterDetails configurable) {
      myConfigurable = configurable;
      myConfigurable.initUi();
      myDetails = myConfigurable.getDetails();
      myMaster = myConfigurable.getMaster();
      myToolbar = myConfigurable.getToolbar();
    }

    @Override
    void set(final ContentWrapper wrapper) {
      myOwnDetails.setDetailsModeEnabled(false);
      myDetails.setPrefix(getBannerText((Configurable)myConfigurable));
      wrapper.setContent(myMaster, myToolbar, myDetails, getContext().getErrors().get(myConfigurable));
    }

    @Override
    void setBannerActions(final Action[] actions) {
      myDetails.setBannerActions(actions);
    }

    @Override
    boolean isShowing() {
      return myDetails.getComponent().isShowing();
    }

    @Override
    void updateBannerActions() {
      myDetails.updateBannerActions();
    }

    @Override
    void setText(final String[] bannerText) {
      myDetails.update();
    }
  }

  public void clearFilter() {
    mySearch.setText("");
  }

  @Override
  public void setHistory(final History history) {
  }
}
