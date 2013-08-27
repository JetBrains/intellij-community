/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EdtRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class OptionsEditor extends JPanel implements DataProvider, Place.Navigator, Disposable, AWTEventListener {
  public static DataKey<OptionsEditor> KEY = DataKey.create("options.editor");

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.newEditor.OptionsEditor");

  @NonNls private static final String MAIN_SPLITTER_PROPORTION = "options.splitter.main.proportions";
  @NonNls private static final String DETAILS_SPLITTER_PROPORTION = "options.splitter.details.proportions";

  @NonNls private static final String SEARCH_VISIBLE = "options.searchVisible";

  @NonNls private static final String NOT_A_NEW_COMPONENT = "component.was.already.instantiated";

  private final Project myProject;

  private final OptionsEditorContext myContext;

  private final History myHistory = new History(this);

  private final OptionsTree myTree;
  private final MySearchField mySearch;
  private final Splitter myMainSplitter;
  //[back/forward] JComponent myToolbarComponent;

  private final DetailsComponent myOwnDetails = new DetailsComponent().setEmptyContentText("Select configuration element in the tree to edit its settings");
  private final ContentWrapper myContentWrapper = new ContentWrapper();


  private final Map<Configurable, ConfigurableContent> myConfigurable2Content = new HashMap<Configurable, ConfigurableContent>();
  private final Map<Configurable, ActionCallback> myConfigurable2LoadCallback = new HashMap<Configurable, ActionCallback>();

  private final MergingUpdateQueue myModificationChecker;
  private final ConfigurableGroup[] myGroups;

  private final SpotlightPainter mySpotlightPainter = new SpotlightPainter();
  private final MergingUpdateQueue mySpotlightUpdate;
  private final LoadingDecorator myLoadingDecorator;
  private final Filter myFilter;

  private final Wrapper mySearchWrapper = new Wrapper();
  private final JPanel myLeftSide;

  private boolean myFilterDocumentWasChanged;
  //[back/forward] private ActionToolbar myToolbar;
  private Window myWindow;
  private final PropertiesComponent myProperties;
  private volatile boolean myDisposed;

  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProject = project;
    myGroups = groups;
    myProperties = PropertiesComponent.getInstance(project);

    myFilter = new Filter();
    myContext = new OptionsEditorContext(myFilter);

    mySearch = new MySearchField() {
      @Override
      protected void onTextKeyEvent(final KeyEvent e) {
        myTree.processTextEvent(e);
      }
    };

    mySearch.getTextEditor().addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        boolean hasText = mySearch.getText().length() > 0;
        if (!myContext.isHoldingFilter() && hasText) {
          myFilter.reenable();
        }

        if (!isSearchFieldFocused() && hasText) {
          mySearch.selectText();
        }
      }
    });

    myTree = new OptionsTree(myProject, groups, getContext()) {
      @Override
      protected void onTreeKeyEvent(final KeyEvent e) {
        myFilterDocumentWasChanged = false;
        try {
          mySearch.keyEventToTextField(e);
        }
        finally {
          if (myFilterDocumentWasChanged && !isFilterFieldVisible()) {
            setFilterFieldVisible(true, false, false);
          }
        }
      }
    };

    getContext().addColleague(myTree);
    Disposer.register(this, myTree);
    mySearch.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myFilter.update(e.getType(), true, false);
      }
    });


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
        dimension.width = Math.max(myTree.getMinimumSize().width, mySearchWrapper.getPreferredSize().width);
        return dimension;
      }
    };

    /* [back/forward]

    final NonOpaquePanel toolbarPanel = new NonOpaquePanel(new BorderLayout());
    toolbarPanel.add(myToolbarComponent, BorderLayout.WEST);
    toolbarPanel.add(mySearchWrapper, BorderLayout.CENTER);
    */

    myLeftSide.add(mySearchWrapper, BorderLayout.NORTH);
    myLeftSide.add(myTree, BorderLayout.CENTER);

    setLayout(new BorderLayout());

    myMainSplitter = new Splitter(false);
    myMainSplitter.setFirstComponent(myLeftSide);

    myLoadingDecorator = new LoadingDecorator(myOwnDetails.getComponent(), this, 150);
    myMainSplitter.setSecondComponent(myLoadingDecorator.getComponent());


    myMainSplitter.setProportion(readProportion(0.3f, MAIN_SPLITTER_PROPORTION));
    myContentWrapper.mySplitter.setProportion(readProportion(0.2f, DETAILS_SPLITTER_PROPORTION));

    add(myMainSplitter, BorderLayout.CENTER);

    MyColleague colleague = new MyColleague();
    getContext().addColleague(colleague);

    mySpotlightUpdate = new MergingUpdateQueue("OptionsSpotlight", 200, false, this, this, this);

    if (preselectedConfigurable != null) {
      myTree.select(preselectedConfigurable);
    } else {
      myTree.selectFirst();
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

    IdeGlassPaneUtil.installPainter(myOwnDetails.getContentGutter(), mySpotlightPainter, this);

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

  /** @see #select(com.intellij.openapi.options.Configurable) */
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
    return myTree.findConfigurable(configurableClass);
  }

  @Nullable
  public SearchableConfigurable findConfigurableById(@NotNull String configurableId) {
    return myTree.findConfigurableById(configurableId);
  }

  public ActionCallback clearSearchAndSelect(Configurable configurable) {
    clearFilter();
    return select(configurable, "");
  }

  public ActionCallback select(Configurable configurable) {
    if (StringUtil.isEmpty(mySearch.getText())) {
      return select(configurable, "");
    } else {
      return myFilter.refilterFor(mySearch.getText(), true, true);
    }
  }

  public ActionCallback select(Configurable configurable, final String text) {
    myFilter.refilterFor(text, false, true);
    return myTree.select(configurable);
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

      updateSpotlight(true);
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

          final ConfigurableContent content = myConfigurable2Content.get(current);

          content.setText(getBannerText(configurable));
          content.setBannerActions(new Action[] {new ResetAction(configurable)});

          content.updateBannerActions();

          myLoadingDecorator.stopLoading();

          updateSpotlight(false);

          checkModified(oldConfigurable);
          checkModified(configurable);

          if (myTree.myBuilder.getSelectedElements().size() == 0) {
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
                  if (myProject.isDisposed()) {
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

    final ConfigurableContent content;

    if (configurable instanceof MasterDetails) {
      content = new Details((MasterDetails)configurable);
    }
    else {
      content = new Simple(configurable);
    }

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


  private void updateSpotlight(boolean now) {
    if (now) {
      final boolean success = mySpotlightPainter.updateForCurrentConfigurable();
      if (!success) {
        updateSpotlight(false);
      }
    } else {
      mySpotlightUpdate.queue(new Update(this) {
        @Override
        public void run() {
          final boolean success = mySpotlightPainter.updateForCurrentConfigurable();
          if (!success) {
            updateSpotlight(false);
          }
        }
      });
    }
  }

  private String[] getBannerText(Configurable configurable) {
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
      if (!myConfigurable2Content.containsKey(configurable) && isParentWithContent(configurable)) {

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

  private static boolean isParentWithContent(final Configurable configurable) {
    return configurable instanceof SearchableConfigurable.Parent &&
        ((SearchableConfigurable.Parent)configurable).hasOwnContent();
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
      return myContext.isModified(myConfigurable) || getContext().getErrors().containsKey(myConfigurable);
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
          JScrollPane scroll = ScrollPaneFactory.createScrollPane(c);
          scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
          scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
          scroll.getVerticalScrollBar().setUnitIncrement(10);
          scroll.setBorder(null);
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
      myTree.select(errors.keySet().iterator().next());
    }
  }


  @Override
  public Object getData(@NonNls final String dataId) {
    if (KEY.is(dataId)) {
      return this;
    }
    return History.KEY.is(dataId) ? myHistory : null;
  }

  public JTree getPreferredFocusedComponent() {
    return myTree.getTree();
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(1200, 768);
  }

  private class Filter extends ElementFilter.Active.Impl<SimpleNode> {

    SearchableOptionsRegistrar myIndex = SearchableOptionsRegistrar.getInstance();
    Set<Configurable> myFiltered = null;
    ConfigurableHit myHits;

    boolean myUpdateEnabled = true;
    private Configurable myLastSelected;

    @Override
    public boolean shouldBeShowing(final SimpleNode value) {
      if (myFiltered == null) return true;

      if (value instanceof OptionsTree.EditorNode) {
        final OptionsTree.EditorNode node = (OptionsTree.EditorNode)value;
        return myFiltered.contains(node.getConfigurable()) || isChildOfNameHit(node);
      }

      return true;
    }

    private boolean isChildOfNameHit(OptionsTree.EditorNode node) {
      if (myHits != null) {
        OptionsTree.Base eachParent = node;
        while (eachParent != null) {
          if (eachParent instanceof OptionsTree.EditorNode) {
            final OptionsTree.EditorNode eachEditorNode = (OptionsTree.EditorNode)eachParent;
            if (myHits.getNameFullHits().contains(eachEditorNode.myConfigurable)) return true;
          }
          eachParent = (OptionsTree.Base)eachParent.getParent();
        }

        return false;
      }

      return false;
    }

    public ActionCallback refilterFor(String text, boolean adjustSelection, final boolean now) {
      try {
        myUpdateEnabled = false;
        mySearch.setText(text);
      }
      finally {
        myUpdateEnabled = true;
      }

      return update(DocumentEvent.EventType.CHANGE, adjustSelection, now);
    }

    public void clearTemporary() {
      myContext.setHoldingFilter(false);
      updateSpotlight(false);
    }

    public void reenable() {
      myContext.setHoldingFilter(true);
      updateSpotlight(false);
    }

    public ActionCallback update(DocumentEvent.EventType type, boolean adjustSelection, boolean now) {
      if (!myUpdateEnabled) return new ActionCallback.Rejected();

      final String text = mySearch.getText();
      if (getFilterText().length() == 0) {
        myContext.setHoldingFilter(false);
        myFiltered = null;
      } else {
        myContext.setHoldingFilter(true);
        myHits = myIndex.getConfigurables(myGroups, type, myFiltered, text, myProject);
        myFiltered = myHits.getAll();
      }

      if (myFiltered != null && myFiltered.isEmpty()) {
        mySearch.getTextEditor().setBackground(LightColors.RED);
      } else {
        mySearch.getTextEditor().setBackground(UIUtil.getTextFieldBackground());
      }


      final Configurable current = getContext().getCurrentConfigurable();

      boolean shouldMoveSelection = true;

      if (myHits != null && (myHits.getNameFullHits().contains(current) || myHits.getContentHits().contains(current))) {
        shouldMoveSelection = false;
      }

      if (shouldMoveSelection && type != DocumentEvent.EventType.INSERT && (myFiltered == null || myFiltered.contains(current))) {
        shouldMoveSelection = false;
      }

      Configurable toSelect = adjustSelection ? current : null;
      if (shouldMoveSelection && myHits != null) {
        if (!myHits.getNameHits().isEmpty()) {
          toSelect = suggestToSelect(myHits.getNameHits(), myHits.getNameFullHits());
        } else if (!myHits.getContentHits().isEmpty()) {
          toSelect = suggestToSelect(myHits.getContentHits(), null);
        }
      }

      updateSpotlight(false);

      if ((myFiltered == null || !myFiltered.isEmpty()) && toSelect == null && myLastSelected != null) {
        toSelect = myLastSelected;
        myLastSelected = null;
      }

      if (toSelect == null && current != null) {
        myLastSelected = current;
      }

      final ActionCallback callback = fireUpdate(adjustSelection ? myTree.findNodeFor(toSelect) : null, adjustSelection, now);

      myFilterDocumentWasChanged = true;

      return callback;
    }

    private boolean isEmptyParent(Configurable configurable) {
      return configurable instanceof SearchableConfigurable.Parent && !((SearchableConfigurable.Parent)configurable).hasOwnContent();
    }

    @Nullable
    private Configurable suggestToSelect(Set<Configurable> set, Set<Configurable> fullHits) {
      Configurable candidate = null;
      for (Configurable each : set) {
        if (fullHits != null && fullHits.contains(each)) return each;
        if (!isEmptyParent(each) && candidate == null) {
          candidate = each;
        }
      }

      return candidate;
    }

  }

  @Override
  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    final Configurable config = (Configurable)place.getPath("configurable");
    final String filter = (String)place.getPath("filter");

    final ActionCallback result = new ActionCallback();

    myFilter.refilterFor(filter, false, true).doWhenDone(new Runnable() {
      @Override
      public void run() {
        myTree.select(config).notifyWhenDone(result);
      }
    });

    return result;
  }

  @Override
  public void queryPlace(@NotNull final Place place) {
    final Configurable current = getContext().getCurrentConfigurable();
    place.putPath("configurable", current);
    place.putPath("filter", getFilterText());

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
    myProperties.setValue(SEARCH_VISIBLE, Boolean.valueOf(isFilterFieldVisible()).toString());

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
    return myContext;
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
        myFilter.clearTemporary();
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

  private static class MySearchField extends SearchTextField {

    private boolean myDelegatingNow;

    private MySearchField() {
      super(false);
      addKeyListener(new KeyAdapter() {});
    }

    @Override
    protected boolean preprocessEventForTextField(final KeyEvent e) {
      final KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
      if (!myDelegatingNow) {
        if ("pressed ESCAPE".equals(stroke.toString()) && getText().length() > 0) {
          setText(""); // reset filter on ESC
          return true;
        }

        if (getTextEditor().isFocusOwner()) {
          try {
            myDelegatingNow = true;
            boolean treeNavigation =
              stroke.getModifiers() == 0 && (stroke.getKeyCode() == KeyEvent.VK_UP || stroke.getKeyCode() == KeyEvent.VK_DOWN);

            if ("pressed ENTER".equals(stroke.toString())) {
              return true; // avoid closing dialog on ENTER
            }

            final Object action = getTextEditor().getInputMap().get(stroke);
            if (action == null || treeNavigation) {
              onTextKeyEvent(e);
              return true;
            }
          }
          finally {
            myDelegatingNow = false;
          }
        }
      }
      return false;
    }


    protected void onTextKeyEvent(final KeyEvent e) {

    }
  }

  private class SpotlightPainter extends AbstractPainter {
    Map<Configurable, String> myConfigurableToLastOption = new HashMap<Configurable, String>();

    GlassPanel myGP = new GlassPanel(myOwnDetails.getContentGutter());
    boolean myVisible;

    @Override
    public void executePaint(final Component component, final Graphics2D g) {
      if (myVisible && myGP.isVisible()) {
        myGP.paintSpotlight(g, myOwnDetails.getContentGutter());
      }
    }

    public boolean updateForCurrentConfigurable() {
      final Configurable current = getContext().getCurrentConfigurable();

      if (current != null && !myConfigurable2Content.containsKey(current)) {
        return ApplicationManager.getApplication().isUnitTestMode();
      }

      String text = getFilterText();

      try {
        final boolean sameText =
          myConfigurableToLastOption.containsKey(current) && text.equals(myConfigurableToLastOption.get(current));


        if (current == null) {
          myVisible = false;
          myGP.clear();
          return true;
        }

        SearchableConfigurable searchable;
        if (current instanceof SearchableConfigurable) {
          searchable = (SearchableConfigurable)current;
        } else {
          searchable = new SearachableWrappper(current);
        }

        myGP.clear();

        final Runnable runnable = SearchUtil.lightOptions(searchable, myContentWrapper, text, myGP);
        if (runnable != null) {
          myVisible = true;//myContext.isHoldingFilter();
          runnable.run();

          boolean pushFilteringFurther = true;
          if (sameText) {
            pushFilteringFurther = false;
          } else {
            if (myFilter.myHits != null) {
              pushFilteringFurther = !myFilter.myHits.getNameHits().contains(current);
            }
          }

          final Runnable ownSearch = searchable.enableSearch(text);
          if (pushFilteringFurther && ownSearch != null) {
            ownSearch.run();
          }
          fireNeedsRepaint(myOwnDetails.getComponent());
        } else {
          myVisible = false;
        }
      }
      finally {
        myConfigurableToLastOption.put(current, text);
      }

      return true;
    }


    @Override
    public boolean needsRepaint() {
      return true;
    }
  }

  private String getFilterText() {
    return mySearch.getText() != null ? mySearch.getText().trim() : "";
  }

  private static class SearachableWrappper implements SearchableConfigurable {
    private final Configurable myConfigurable;

    private SearachableWrappper(final Configurable configurable) {
      myConfigurable = configurable;
    }

    @Override
    @NotNull
    public String getId() {
      return myConfigurable.getClass().getName();
    }

    @Override
    public Runnable enableSearch(final String option) {
      return null;
    }

    @Override
    @Nls
    public String getDisplayName() {
      return myConfigurable.getDisplayName();
    }

    @Override
    public String getHelpTopic() {
      return myConfigurable.getHelpTopic();
    }

    @Override
    public JComponent createComponent() {
      return myConfigurable.createComponent();
    }

    @Override
    public boolean isModified() {
      return myConfigurable.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
      myConfigurable.apply();
    }

    @Override
    public void reset() {
      myConfigurable.reset();
    }

    @Override
    public void disposeUIResources() {
      myConfigurable.disposeUIResources();
    }
  }

  private abstract static class ConfigurableContent {
    abstract void set(ContentWrapper wrapper);

    abstract boolean isShowing();

    abstract void setBannerActions(Action[] actions);

    abstract void updateBannerActions();

    abstract void setText(final String[] bannerText);
  }

  private class Simple extends ConfigurableContent {
    JComponent myComponent;
    Configurable myConfigurable;

    Simple(final Configurable configurable) {
      myConfigurable = configurable;
      myComponent = configurable.createComponent();

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
      wrapper.setContent(myComponent, getContext().getErrors().get(myConfigurable), !ConfigurableWrapper.isNoScroll(myConfigurable));
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
