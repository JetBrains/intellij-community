// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.CommonBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.mock.MockConfirmation;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PopupFactoryImpl extends JBPopupFactory {

  /**
   * Allows to get an editor position for which a popup with auxiliary information might be shown.
   * <p/>
   * Primary intention for this key is to hint popup position for the non-caret location.
   */
  public static final Key<VisualPosition> ANCHOR_POPUP_POSITION = Key.create("popup.anchor.position");
  /**
   * If corresponding value is defined for an {@link Editor}, popups shown for the editor will be located at specified point. This allows to
   * show popups for non-default locations (caret location is used by default).
   *
   * @see JBPopupFactory#guessBestPopupLocation(Editor)
   */
  public static final Key<Point> ANCHOR_POPUP_POINT = Key.create("popup.anchor.point");

  private static final Logger LOG = Logger.getInstance(PopupFactoryImpl.class);

  private final Map<Disposable, List<Balloon>> myStorage = ContainerUtil.createWeakMap();

  @NotNull
  @Override
  public <T> IPopupChooserBuilder<T> createPopupChooserBuilder(@NotNull List<? extends T> list) {
    return new PopupChooserBuilder<>(new JBList<>(new CollectionListModel<>(list)));
  }

  @NotNull
  @Override
  public ListPopup createConfirmation(@PopupTitle String title, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(), onYes, defaultOptionIndex);
  }

  @NotNull
  @Override
  public ListPopup createConfirmation(@PopupTitle String title, final String yesText, String noText, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, yesText, noText, onYes, EmptyRunnable.getInstance(), defaultOptionIndex);
  }

  @NotNull
  @Override
  public JBPopup createMessage(@PopupTitle String text) {
    return createListPopup(new BaseListPopupStep<>(null, text));
  }

  @Override
  public Balloon getParentBalloonFor(@Nullable Component c) {
    if (c == null) return null;
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof JComponent) {
        Object balloon = ((JComponent)eachParent).getClientProperty(Balloon.KEY);
        if (balloon instanceof Balloon) {
          return (Balloon)balloon;
        }
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }

  @Override
  protected <T> PopupChooserBuilder.@NotNull PopupComponentAdapter<T> createPopupComponentAdapter(@NotNull PopupChooserBuilder<T> builder, @NotNull JList<T> list) {
    return new PopupListAdapter<>(builder, list);
  }

  @Override
  protected <T> PopupChooserBuilder.@NotNull PopupComponentAdapter<T> createPopupComponentAdapter(@NotNull PopupChooserBuilder<T> builder, @NotNull JTree tree) {
    return new PopupTreeAdapter<>(builder, tree);
  }

  @Override
  protected <T> PopupChooserBuilder.@NotNull PopupComponentAdapter<T> createPopupComponentAdapter(@NotNull PopupChooserBuilder<T> builder, @NotNull JTable table) {
    return new PopupTableAdapter<>(builder, table);
  }

  @NotNull
  @Override
  public ListPopup createConfirmation(@PopupTitle String title,
                                      @NlsContexts.Label String yesText,
                                      @NlsContexts.Label String noText,
                                      final Runnable onYes,
                                      final Runnable onNo,
                                      int defaultOptionIndex) {
    final BaseListPopupStep<String> step = new BaseListPopupStep<String>(title, yesText, noText) {
      @Override
      public PopupStep onChosen(String selectedValue, final boolean finalChoice) {
        return doFinalStep(selectedValue.equals(yesText) ? onYes : onNo);
      }

      @Override
      public void canceled() {
        onNo.run();
      }

      @Override
      public boolean isMnemonicsNavigationEnabled() {
        return true;
      }
    };
    step.setDefaultOptionIndex(defaultOptionIndex);

    final Application app = ApplicationManager.getApplication();
    return app == null || !app.isUnitTestMode() ? new ListPopupImpl(step) : new MockConfirmation(step, yesText);
  }


  public static class ActionGroupPopup extends ListPopupImpl {

    private final Runnable myDisposeCallback;
    private final Component myComponent;
    private final String myActionPlace;

    public ActionGroupPopup(@PopupTitle String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            final Runnable disposeCallback,
                            final int maxRowCount,
                            final Condition<? super AnAction> preselectActionCondition,
                            @Nullable final String actionPlace) {
      this(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
           maxRowCount, preselectActionCondition, actionPlace, null, false);
    }

    public ActionGroupPopup(@PopupTitle String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            Runnable disposeCallback,
                            int maxRowCount,
                            Condition<? super AnAction> preselectActionCondition,
                            @Nullable final String actionPlace,
                            boolean autoSelection) {
      this(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
           maxRowCount, preselectActionCondition, actionPlace, null, autoSelection);
    }

    public ActionGroupPopup(@PopupTitle String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            Runnable disposeCallback,
                            int maxRowCount,
                            Condition<? super AnAction> preselectActionCondition,
                            @Nullable final String actionPlace,
                            @Nullable PresentationFactory presentationFactory,
                            boolean autoSelection) {
      this(null, createStep(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics,
                            preselectActionCondition, actionPlace, presentationFactory, autoSelection), disposeCallback, dataContext, actionPlace, maxRowCount);
      UiInspectorUtil.registerProvider(getList(), () -> UiInspectorUtil.collectActionGroupInfo("Menu", actionGroup, actionPlace));
    }

    protected ActionGroupPopup(@Nullable WizardPopup aParent,
                               @NotNull ListPopupStep step,
                               @Nullable Runnable disposeCallback,
                               @NotNull DataContext dataContext,
                               @Nullable String actionPlace,
                               int maxRowCount) {
      super(CommonDataKeys.PROJECT.getData(dataContext), aParent, step, null);
      setMaxRowCount(maxRowCount);
      myDisposeCallback = disposeCallback;
      myComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      myActionPlace = ObjectUtils.notNull(actionPlace, ActionPlaces.POPUP);

      registerAction("handleActionToggle1", KeyEvent.VK_SPACE, 0, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          handleToggleAction();
        }
      });

      addListSelectionListener(e -> {
        final JList list = (JList)e.getSource();
        final ActionItem actionItem = (ActionItem)list.getSelectedValue();
        if (actionItem == null) return;
        Presentation presentation = updateActionItem(actionItem);
        ActionMenu.showDescriptionInStatusBar(true, myComponent, presentation.getDescription());
      });
    }

    @NotNull
    private Presentation updateActionItem(@NotNull ActionItem actionItem) {
      AnAction action = actionItem.getAction();
      Presentation presentation = new Presentation();
      presentation.setDescription(action.getTemplatePresentation().getDescription());

      final AnActionEvent actionEvent =
        new AnActionEvent(null, DataManager.getInstance().getDataContext(myComponent), myActionPlace, presentation,
                          ActionManager.getInstance(), 0);
      actionEvent.setInjectedContext(action.isInInjectedContext());
      ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, actionEvent, false);
      return presentation;
    }

    private static ListPopupStep<ActionItem> createStep(@PopupTitle String title,
                                                        @NotNull ActionGroup actionGroup,
                                                        @NotNull DataContext dataContext,
                                                        boolean showNumbers,
                                                        boolean useAlphaAsNumbers,
                                                        boolean showDisabledActions,
                                                        boolean honorActionMnemonics,
                                                        Condition<? super AnAction> preselectActionCondition,
                                                        @Nullable String actionPlace,
                                                        @Nullable PresentationFactory presentationFactory,
                                                        boolean autoSelection) {
      final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      LOG.assertTrue(component != null, "dataContext has no component for new ListPopupStep");

      List<ActionItem> items = ActionPopupStep.createActionItems(
          actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, actionPlace, presentationFactory);

      return new ActionPopupStep(items, title, getComponentContextSupplier(component), actionPlace, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items),
                                 preselectActionCondition, autoSelection, showDisabledActions, presentationFactory);
    }

    /** @deprecated Use {@link ActionPopupStep#createActionItems(ActionGroup, DataContext, boolean, boolean, boolean, boolean, String, PresentationFactory)} instead. */
    @Deprecated
    @NotNull
    public static List<ActionItem> getActionItems(@NotNull ActionGroup actionGroup,
                                                  @NotNull DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean useAlphaAsNumbers,
                                                  boolean showDisabledActions,
                                                  boolean honorActionMnemonics,
                                                  @Nullable String actionPlace) {
      return ActionPopupStep.createActionItems(
        actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, actionPlace, null);
    }

    @Override
    public void dispose() {
      if (myDisposeCallback != null) {
        myDisposeCallback.run();
      }
      ActionMenu.showDescriptionInStatusBar(true, myComponent, null);
      super.dispose();
    }

    @Override
    public void handleSelect(boolean handleFinalChoices, InputEvent e) {
      final Object selectedValue = getList().getSelectedValue();
      final ActionPopupStep actionPopupStep = ObjectUtils.tryCast(getListStep(), ActionPopupStep.class);

      if (actionPopupStep != null) {
        KeepingPopupOpenAction dontClosePopupAction = getActionByClass(selectedValue, actionPopupStep, KeepingPopupOpenAction.class);
        if (dontClosePopupAction != null) {
          actionPopupStep.performAction((AnAction)dontClosePopupAction, e != null ? e.getModifiers() : 0, e);
          for (ActionItem item : actionPopupStep.getValues()) {
            updateActionItem(item);
          }
          getList().repaint();
          return;
        }
      }

      super.handleSelect(handleFinalChoices, e);
    }

    protected void handleToggleAction() {
      final Object[] selectedValues = getList().getSelectedValues();

      ListPopupStep<Object> listStep = getListStep();
      final ActionPopupStep actionPopupStep = ObjectUtils.tryCast(listStep, ActionPopupStep.class);
      if (actionPopupStep == null) return;

      List<ToggleAction> filtered = ContainerUtil.mapNotNull(selectedValues, o -> getActionByClass(o, actionPopupStep, ToggleAction.class));

      for (ToggleAction action : filtered) {
        actionPopupStep.performAction(action, 0);
      }

      for (ActionItem item : actionPopupStep.getValues()) {
        updateActionItem(item);
      }

      getList().repaint();
    }

    @Nullable
    private static <T> T getActionByClass(@Nullable Object value, @NotNull ActionPopupStep actionPopupStep, @NotNull Class<T> actionClass) {
      ActionItem item = value instanceof ActionItem ? (ActionItem)value : null;
      if (item == null) return null;
      if (!actionPopupStep.isSelectable(item)) return null;
      return actionClass.isInstance(item.getAction()) ? actionClass.cast(item.getAction()) : null;
    }
  }

  @NotNull
  private static Supplier<DataContext> getComponentContextSupplier(Component component) {
    return () -> DataManager.getInstance().getDataContext(component);
  }

  @Override
  @NotNull
  public ListPopup createActionGroupPopup(@PopupTitle String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid aid,
                                          boolean showDisabledActions,
                                          Runnable disposeCallback,
                                          int maxRowCount,
                                          Condition<? super AnAction> preselectActionCondition,
                                          @Nullable String actionPlace) {
    return new ActionGroupPopup(title,
                                actionGroup,
                                dataContext,
                                aid == ActionSelectionAid.ALPHA_NUMBERING || aid == ActionSelectionAid.NUMBERING,
                                aid == ActionSelectionAid.ALPHA_NUMBERING,
                                showDisabledActions,
                                aid == ActionSelectionAid.MNEMONICS,
                                disposeCallback,
                                maxRowCount,
                                preselectActionCondition,
                                actionPlace);
  }

  @NotNull
  @Override
  public ListPopup createActionGroupPopup(@PopupTitle String title,
                                          @NotNull final ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          boolean showNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics,
                                          final Runnable disposeCallback,
                                          final int maxRowCount,
                                          final Condition<? super AnAction> preselectActionCondition) {
    return new ActionGroupPopup(title, actionGroup, dataContext, showNumbers, true, showDisabledActions, honorActionMnemonics,
                                  disposeCallback, maxRowCount, preselectActionCondition, null);
  }

  @NotNull
  @Override
  public ListPopupStep<ActionItem> createActionsStep(@NotNull ActionGroup actionGroup,
                                                     @NotNull DataContext dataContext,
                                                     @Nullable String actionPlace,
                                                     boolean showNumbers,
                                                     boolean showDisabledActions,
                                                     @PopupTitle String title,
                                                     Component component,
                                                     boolean honorActionMnemonics,
                                                     int defaultOptionIndex,
                                                     boolean autoSelectionEnabled) {
    return ActionPopupStep.createActionsStep(
      actionGroup, dataContext, showNumbers, true, showDisabledActions,
      title, honorActionMnemonics, autoSelectionEnabled,
      getComponentContextSupplier(component),
      actionPlace, null, defaultOptionIndex, null);
  }

  private static boolean itemsHaveMnemonics(final List<? extends ActionItem> items) {
    for (ActionItem item : items) {
      if (item.getAction().getTemplatePresentation().getMnemonic() != 0) return true;
    }

    return false;
  }

  @NotNull
  @Override
  public ListPopup createListPopup(@NotNull ListPopupStep step) {
    return new ListPopupImpl(step);
  }

  @NotNull
  @Override
  public ListPopup createListPopup(@NotNull ListPopupStep step, int maxRowCount) {
    ListPopupImpl popup = new ListPopupImpl(step);
    popup.setMaxRowCount(maxRowCount);
    return popup;
  }

  @NotNull
  @Override
  public TreePopup createTree(JBPopup parent, @NotNull TreePopupStep aStep, Object parentValue) {
    return new TreePopupImpl(aStep.getProject(), parent, aStep, parentValue);
  }

  @NotNull
  @Override
  public TreePopup createTree(@NotNull TreePopupStep aStep) {
    return new TreePopupImpl(aStep.getProject(), null, aStep, null);
  }

  @NotNull
  @Override
  public ComponentPopupBuilder createComponentPopupBuilder(@NotNull JComponent content, JComponent preferableFocusComponent) {
    return new ComponentPopupBuilderImpl(content, preferableFocusComponent);
  }


  @NotNull
  @Override
  public RelativePoint guessBestPopupLocation(@NotNull DataContext dataContext) {
    Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    JComponent focusOwner = component instanceof JComponent ? (JComponent)component : null;

    if (focusOwner == null) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      JFrame frame = project == null ? null : WindowManager.getInstance().getFrame(project);
      focusOwner = frame == null ? null : frame.getRootPane();
      if (focusOwner == null) {
        throw new IllegalArgumentException("focusOwner cannot be null");
      }
    }

    final Point point = PlatformDataKeys.CONTEXT_MENU_POINT.getData(dataContext);
    if (point != null) {
      return new RelativePoint(focusOwner, point);
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null && focusOwner == editor.getContentComponent()) {
      return guessBestPopupLocation(editor);
    }
    return guessBestPopupLocation(focusOwner);
  }

  @NotNull
  @Override
  public RelativePoint guessBestPopupLocation(@NotNull final JComponent component) {
    Point popupMenuPoint = null;
    final Rectangle visibleRect = component.getVisibleRect();
    if (component instanceof JList) { // JList
      JList list = (JList)component;
      int firstVisibleIndex = list.getFirstVisibleIndex();
      int lastVisibleIndex = list.getLastVisibleIndex();
      int[] selectedIndices = list.getSelectedIndices();
      for (int index : selectedIndices) {
        if (firstVisibleIndex <= index && index <= lastVisibleIndex) {
          Rectangle cellBounds = list.getCellBounds(index, index);
          popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 4, cellBounds.y + cellBounds.height - 1);
          break;
        }
      }
    }
    else if (component instanceof JTree) { // JTree
      JTree tree = (JTree)component;
      TreePath[] paths = tree.getSelectionPaths();
      if (paths != null && paths.length > 0) {
        TreePath pathFound = null;
        int distanceFound = Integer.MAX_VALUE;
        int center = visibleRect.y + visibleRect.height / 2;
        for (TreePath path : paths) {
          Rectangle bounds = tree.getPathBounds(path);
          if (bounds != null) {
            int distance = Math.abs(bounds.y + bounds.height / 2 - center);
            if (distance < distanceFound) {
              popupMenuPoint = new Point(bounds.x + 2, bounds.y + bounds.height - 1);
              distanceFound = distance;
              pathFound = path;
            }
          }
        }
        if (pathFound != null) {
          TreeUtil.scrollToVisible(tree, pathFound, false);
        }
      }
    }
    else if (component instanceof JTable) {
      JTable table = (JTable)component;
      int column = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
      int row = Math.max(table.getSelectionModel().getLeadSelectionIndex(), table.getSelectionModel().getAnchorSelectionIndex());
      Rectangle rect = table.getCellRect(row, column, false);
      if (!visibleRect.intersects(rect)) {
        table.scrollRectToVisible(rect);
      }
      popupMenuPoint = new Point(rect.x, rect.y + rect.height - 1);
    }
    else if (component instanceof PopupOwner) {
      popupMenuPoint = ((PopupOwner)component).getBestPopupPosition();
    }
    if (popupMenuPoint == null) {
      popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height / 2);
    }

    return new RelativePoint(component, popupMenuPoint);
  }

  @Override
  public boolean isBestPopupLocationVisible(@NotNull Editor editor) {
    return getVisibleBestPopupLocation(editor) != null;
  }

  @NotNull
  @Override
  public RelativePoint guessBestPopupLocation(@NotNull Editor editor) {
    Point p = getVisibleBestPopupLocation(editor);
    if (p == null) {
      final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      p = new Point(visibleArea.x + visibleArea.width / 3, visibleArea.y + visibleArea.height / 2);
    }
    return new RelativePoint(editor.getContentComponent(), p);
  }

  @Nullable
  private static Point getVisibleBestPopupLocation(@NotNull Editor editor) {
    int lineHeight = editor.getLineHeight();
    Point p = editor.getUserData(ANCHOR_POPUP_POINT);
    if (p == null) {
      VisualPosition visualPosition = editor.getUserData(ANCHOR_POPUP_POSITION);

      if (visualPosition == null) {
        CaretModel caretModel = editor.getCaretModel();
        if (caretModel.isUpToDate()) {
          visualPosition = caretModel.getVisualPosition();
        }
        else {
          visualPosition = editor.offsetToVisualPosition(caretModel.getOffset());
        }
      }

      p = editor.visualPositionToXY(visualPosition);
      p.y += lineHeight;
    }

    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    return !visibleArea.contains(p) && !visibleArea.contains(p.x, p.y - lineHeight) ? null : p;
  }

  @Override
  public Point getCenterOf(JComponent container, JComponent content) {
    return AbstractPopup.getCenterOf(container, content);
  }

  @Override
  @NotNull
  public List<JBPopup> getChildPopups(@NotNull final Component component) {
    return AbstractPopup.getChildPopups(component);
  }

  @Override
  public boolean isPopupActive() {
  return IdeEventQueue.getInstance().isPopupActive();
  }

  @NotNull
  @Override
  public BalloonBuilder createBalloonBuilder(@NotNull final JComponent content) {
    return new BalloonPopupBuilderImpl(myStorage, content);
  }

  @NotNull
  @Override
  public BalloonBuilder createDialogBalloonBuilder(@NotNull JComponent content, @PopupTitle String title) {
    final BalloonPopupBuilderImpl builder = new BalloonPopupBuilderImpl(myStorage, content);
    final Color bg = UIManager.getColor("Panel.background");
    final Color borderOriginal = Color.darkGray;
    final Color border = ColorUtil.toAlpha(borderOriginal, 75);
    builder
      .setDialogMode(true)
      .setTitle(title)
      .setAnimationCycle(200)
      .setFillColor(bg).setBorderColor(border).setHideOnClickOutside(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setCloseButtonEnabled(true)
      .setShadow(true);

    return builder;
  }

  @NotNull
  @Override
  public BalloonBuilder createHtmlTextBalloonBuilder(@NotNull final String htmlContent,
                                                     @Nullable final Icon icon,
                                                     Color textColor,
                                                     final Color fillColor,
                                                     @Nullable final HyperlinkListener listener) {
    JEditorPane text = IdeTooltipManager.initPane(htmlContent, new HintHint().setTextFg(textColor).setAwtTooltip(true), null);

    if (listener != null) {
      text.addHyperlinkListener(listener);
    }
    text.setEditable(false);
    NonOpaquePanel.setTransparent(text);
    text.setBorder(null);


    JLabel label = new JLabel();
    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    final NonOpaquePanel textWrapper = new NonOpaquePanel(new GridBagLayout());
    JScrollPane scrolledText = ScrollPaneFactory.createScrollPane(text, true);
    scrolledText.setBackground(fillColor);
    scrolledText.getViewport().setBackground(fillColor);
    textWrapper.add(scrolledText);
    content.add(textWrapper, BorderLayout.CENTER);
    if (icon != null) {
      final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
      north.add(new JLabel(icon), BorderLayout.NORTH);
      content.add(north, BorderLayout.WEST);
    }

    content.setBorder(JBUI.Borders.empty(2, 4));

    final BalloonBuilder builder = createBalloonBuilder(content);

    builder.setFillColor(fillColor);

    return builder;
  }

  @NotNull
  @Override
  public BalloonBuilder createHtmlTextBalloonBuilder(@NotNull String htmlContent,
                                                     MessageType messageType,
                                                     @Nullable HyperlinkListener listener)
  {
    return createHtmlTextBalloonBuilder(htmlContent, messageType.getDefaultIcon(), messageType.getPopupBackground(), listener);
  }


  public static class ActionItem implements ShortcutProvider, AnActionHolder {
    private final AnAction myAction;
    private String myText;
    private final boolean myIsEnabled;
    private final Icon myIcon;
    private final Icon mySelectedIcon;
    private final boolean myPrependWithSeparator;
    private final String mySeparatorText;
    private final String myDescription;

    ActionItem(@NotNull AnAction action,
               @NotNull String text,
               @Nullable String description,
               boolean enabled,
               @Nullable Icon icon,
               @Nullable Icon selectedIcon,
               final boolean prependWithSeparator,
               String separatorText) {
      myAction = action;
      myText = text;
      myIsEnabled = enabled;
      myIcon = icon;
      mySelectedIcon = selectedIcon;
      myPrependWithSeparator = prependWithSeparator;
      mySeparatorText = separatorText;
      myDescription = description;
      myAction.getTemplatePresentation().addPropertyChangeListener(evt -> {
        if (evt.getPropertyName() == Presentation.PROP_TEXT) {
          myText = myAction.getTemplatePresentation().getText();
        }
      });
    }

    @NotNull
    @Override
    public AnAction getAction() {
      return myAction;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @Nullable
    public Icon getIcon(boolean selected) {
      return selected && mySelectedIcon != null ? mySelectedIcon : myIcon;
    }

    public boolean isPrependWithSeparator() {
      return myPrependWithSeparator;
    }

    public String getSeparatorText() {
      return mySeparatorText;
    }

    public boolean isEnabled() { return myIsEnabled; }

    public String getDescription() {
      return myDescription;
    }

    @Nullable
    @Override
    public ShortcutSet getShortcut() {
      return myAction.getShortcutSet();
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}
