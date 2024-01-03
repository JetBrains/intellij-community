// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.icons.CustomIconUtilKt;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.mock.MockConfirmation;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

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
  public static final Key<Boolean> DISABLE_ICON_IN_LIST = Key.create("popup.disable.icon.in.list");

  private static final Logger LOG = Logger.getInstance(PopupFactoryImpl.class);

  private final Map<Disposable, List<Balloon>> myStorage = new WeakHashMap<>();

  @Override
  public @NotNull <T> IPopupChooserBuilder<T> createPopupChooserBuilder(@NotNull List<? extends T> list) {
    LOG.assertTrue(list.isEmpty() || !(list.get(0) instanceof PsiElement) || ApplicationManager.getApplication().isUnitTestMode(),
                   "Do not use PsiElement for popup model. See PsiTargetNavigator");
    JBList<T> jbList = new JBList<>(new CollectionListModel<>(list));
    PopupUtil.applyNewUIBackground(jbList);
    return new PopupChooserBuilder<>(jbList);
  }

  @Override
  public @NotNull ListPopup createConfirmation(@PopupTitle @Nullable String title, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(), onYes, defaultOptionIndex);
  }

  @Override
  public @NotNull ListPopup createConfirmation(@PopupTitle @Nullable String title, final String yesText, String noText, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, yesText, noText, onYes, EmptyRunnable.getInstance(), defaultOptionIndex);
  }

  @Override
  public @NotNull JBPopup createMessage(@PopupTitle String text) {
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

  @Override
  public @NotNull ListPopup createConfirmation(@PopupTitle @Nullable String title,
                                               @NlsContexts.Label String yesText,
                                               @NlsContexts.Label String noText,
                                               Runnable onYes,
                                               Runnable onNo,
                                               int defaultOptionIndex) {
    BaseListPopupStep<String> step = new BaseListPopupStep<>(title, yesText, noText) {
      boolean myRunYes;
      @Override
      public PopupStep onChosen(String selectedValue, final boolean finalChoice) {
        myRunYes = selectedValue.equals(yesText);
        return FINAL_CHOICE;
      }

      @Override
      public void canceled() {
        (myRunYes ? onYes : onNo).run();
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

    public ActionGroupPopup(@PopupTitle @Nullable String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            Runnable disposeCallback,
                            int maxRowCount,
                            Condition<? super AnAction> preselectActionCondition,
                            @Nullable String actionPlace) {
      this(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
           maxRowCount, preselectActionCondition, actionPlace, null, false);
    }

    public ActionGroupPopup(@PopupTitle @Nullable String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            Runnable disposeCallback,
                            int maxRowCount,
                            Condition<? super AnAction> preselectActionCondition,
                            @Nullable String actionPlace,
                            boolean autoSelection) {
      this(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
           maxRowCount, preselectActionCondition, actionPlace, null, autoSelection);
    }

    public ActionGroupPopup(@PopupTitle @Nullable String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            Runnable disposeCallback,
                            int maxRowCount,
                            Condition<? super AnAction> preselectActionCondition,
                            @Nullable String actionPlace,
                            @Nullable PresentationFactory presentationFactory,
                            boolean autoSelection) {
      this(null, title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics,
           disposeCallback, maxRowCount, preselectActionCondition, actionPlace, presentationFactory, autoSelection);
    }

    public ActionGroupPopup(@Nullable WizardPopup parentPopup,
                            @PopupTitle @Nullable String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            Runnable disposeCallback,
                            int maxRowCount,
                            Condition<? super AnAction> preselectActionCondition,
                            @Nullable String actionPlace,
                            @Nullable PresentationFactory presentationFactory,
                            boolean autoSelection) {
      this(parentPopup, createStep(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics,
                            preselectActionCondition, actionPlace, presentationFactory, autoSelection), disposeCallback, dataContext, maxRowCount);
      UiInspectorUtil.registerProvider(getList(), () -> UiInspectorUtil.collectActionGroupInfo("Menu", actionGroup, actionPlace));
    }

    protected ActionGroupPopup(@Nullable WizardPopup aParent,
                               @NotNull ListPopupStep<?> step,
                               @Nullable Runnable disposeCallback,
                               @NotNull DataContext dataContext,
                               int maxRowCount) {
      super(CommonDataKeys.PROJECT.getData(dataContext), aParent, step, null);
      setMaxRowCount(maxRowCount);
      myDisposeCallback = disposeCallback;
      myComponent = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);

      registerAction("handleActionToggle1", KeyEvent.VK_SPACE, 0, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          handleToggleAction(createKeyEvent(e, KeyEvent.VK_SPACE));
        }
      });

      addListSelectionListener(e -> {
        JList<?> list = (JList<?>)e.getSource();
        ActionItem actionItem = (ActionItem)list.getSelectedValue();
        if (actionItem == null) return;
        ActionMenu.showDescriptionInStatusBar(true, myComponent, actionItem.getDescription());
      });
    }

    protected static @NotNull ListPopupStep<ActionItem> createStep(@PopupTitle @Nullable String title,
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
      DataContext asyncDataContext = Utils.createAsyncDataContext(dataContext);
      List<ActionItem> items = ActionPopupStep.createActionItems(
        actionGroup, asyncDataContext, showNumbers, useAlphaAsNumbers,
        showDisabledActions, honorActionMnemonics, actionPlace, presentationFactory);
      return new ActionPopupStep(items, title, () -> asyncDataContext, actionPlace,
                                 showNumbers || honorActionMnemonics && anyMnemonicsIn(items),
                                 preselectActionCondition, autoSelection, showDisabledActions, presentationFactory);
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
      ActionItem item = ObjectUtils.tryCast(getList().getSelectedValue(), ActionItem.class);
      ActionPopupStep step = ObjectUtils.tryCast(getListStep(), ActionPopupStep.class);
      if (step != null && item != null && step.isSelectable(item) && item.isKeepPopupOpen()) {
        step.performAction(item.getAction(), e);
        step.updateStepItems(getList());
      }
      else {
        super.handleSelect(handleFinalChoices, e);
      }
    }

    @Override
    protected void handleRightKeyPressed(@NotNull KeyEvent keyEvent) {
      if (!handleRightOrLeftKeyPressed(keyEvent, true)) {
        super.handleRightKeyPressed(keyEvent);
      }
    }

    @Override
    protected void handleLeftKeyPressed(@NotNull KeyEvent keyEvent) {
      if (!handleRightOrLeftKeyPressed(keyEvent, false)) {
        super.handleLeftKeyPressed(keyEvent);
      }
    }

    private boolean handleRightOrLeftKeyPressed(@NotNull KeyEvent keyEvent, boolean isRightKey) {
      ActionItem item = ObjectUtils.tryCast(getList().getSelectedValue(), ActionItem.class);
      ActionPopupStep step = ObjectUtils.tryCast(getListStep(), ActionPopupStep.class);
      if (step != null && item != null && step.isSelectable(item) && item.isKeepPopupOpen() && item.myAction instanceof ToggleAction toggleAction) {
        AnActionEvent event = step.createAnActionEvent(toggleAction, keyEvent);
        toggleAction.setSelected(event, isRightKey);
        step.updateStepItems(getList());
        return true;
      }
      return false;
    }

    /**
     * @deprecated Do not use or override this method. Use {@link ActionGroupPopup#handleToggleAction(InputEvent)} instead.
     */
    @Deprecated(forRemoval = true)
    protected void handleToggleAction() {
      handleToggleAction(null);
    }

    protected void handleToggleAction(@Nullable InputEvent inputEvent) {
      List<Object> selectedValues = getList().getSelectedValuesList();
      ActionPopupStep step = ObjectUtils.tryCast(getListStep(), ActionPopupStep.class);
      if (step == null) return;
      boolean updateStep = false;
      for (Object value : selectedValues) {
        ActionItem item = ObjectUtils.tryCast(value, ActionItem.class);
        if (item != null && step.isSelectable(item) && item.getAction() instanceof Toggleable) {
          step.performAction(item.getAction(), inputEvent);
          updateStep = true;
        }
      }
      if (updateStep) {
        step.updateStepItems(getList());
      }
    }

    public void registerShortcuts() {
      for (Object value : getListStep().getValues()) {
        if (value instanceof AnActionHolder) {
          ((AnActionHolder)value).getAction().registerCustomShortcutSet(getComponent(), this);
        }
      }
    }
  }

  @Override
  public @NotNull ListPopup createActionGroupPopup(@PopupTitle @Nullable String title,
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

  @Override
  public @NotNull ListPopup createActionGroupPopup(@PopupTitle @Nullable String title,
                                                   @NotNull ActionGroup actionGroup,
                                                   @NotNull DataContext dataContext,
                                                   boolean showNumbers,
                                                   boolean showDisabledActions,
                                                   boolean honorActionMnemonics,
                                                   Runnable disposeCallback,
                                                   int maxRowCount,
                                                   Condition<? super AnAction> preselectActionCondition) {
    return new ActionGroupPopup(title, actionGroup, dataContext, showNumbers, true, showDisabledActions, honorActionMnemonics,
                                  disposeCallback, maxRowCount, preselectActionCondition, null);
  }

  @Override
  public @NotNull ListPopupStep<ActionItem> createActionsStep(@NotNull ActionGroup actionGroup,
                                                              @NotNull DataContext dataContext,
                                                              @Nullable String actionPlace,
                                                              boolean showNumbers,
                                                              boolean showDisabledActions,
                                                              @PopupTitle @Nullable String title,
                                                              Component component,
                                                              boolean honorActionMnemonics,
                                                              int defaultOptionIndex,
                                                              boolean autoSelectionEnabled) {
    DataContext asyncDataContext = Utils.createAsyncDataContext(dataContext);
    return ActionPopupStep.createActionsStep(
      actionGroup, asyncDataContext, showNumbers, true, showDisabledActions,
      title, honorActionMnemonics, autoSelectionEnabled,
      () -> asyncDataContext,
      actionPlace, null, defaultOptionIndex, null);
  }

  @ApiStatus.Internal
  public static boolean anyMnemonicsIn(Iterable<? extends ActionItem> items) {
    for (ActionItem item : items) {
      if (item.getAction().getTemplatePresentation().getMnemonic() != 0) return true;
    }

    return false;
  }

  @Override
  public @NotNull ListPopup createListPopup(@NotNull ListPopupStep step) {
    return new ListPopupImpl(step);
  }

  @Override
  public @NotNull ListPopup createListPopup(@NotNull ListPopupStep step, int maxRowCount) {
    ListPopupImpl popup = new ListPopupImpl(step);
    popup.setMaxRowCount(maxRowCount);
    return popup;
  }

  @Override
  public @NotNull ListPopup createListPopup(@NotNull Project project,
                                            @NotNull ListPopupStep step,
                                            @NotNull Function<? super ListCellRenderer, ? extends ListCellRenderer> cellRendererProducer) {
    return new ListPopupImpl(project, step) {
      @Override
      protected ListCellRenderer<?> getListElementRenderer() {
        return cellRendererProducer.apply(super.getListElementRenderer());
      }
    };
  }

  @Override
  public @NotNull TreePopup createTree(JBPopup parent, @NotNull TreePopupStep aStep, Object parentValue) {
    return new TreePopupImpl(aStep.getProject(), parent, aStep, parentValue);
  }

  @Override
  public @NotNull TreePopup createTree(@NotNull TreePopupStep aStep) {
    return new TreePopupImpl(aStep.getProject(), null, aStep, null);
  }

  @Override
  public @NotNull ComponentPopupBuilder createComponentPopupBuilder(@NotNull JComponent content, JComponent preferableFocusComponent) {
    return new ComponentPopupBuilderImpl(content, preferableFocusComponent);
  }


  @Override
  public @NotNull RelativePoint guessBestPopupLocation(@NotNull DataContext dataContext) {
    Component component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    JComponent focusOwner = component instanceof JComponent ? (JComponent)component : null;

    if (focusOwner == null || !UIUtil.isShowing(focusOwner)) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      JFrame frame = project == null ? WindowManager.getInstance().findVisibleFrame() : WindowManager.getInstance().getFrame(project);
      focusOwner = frame == null ? null : frame.getRootPane();
      if (focusOwner == null) {
        throw new IllegalArgumentException("focusOwner cannot be null:\n" +
                                           "  contextComponent: " + component + "\n" +
                                           "  project: " + project + "\n" +
                                           "  frame: " + frame);
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

  @Override
  public @NotNull RelativePoint guessBestPopupLocation(@NotNull JComponent component) {
    Point popupMenuPoint = null;
    final Rectangle visibleRect = component.getVisibleRect();
    if (component instanceof JList<?> list) { // JList
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
    else if (component instanceof JTree tree) { // JTree
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
    else if (component instanceof JTable table) {
      int column = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
      int row = Math.max(table.getSelectionModel().getLeadSelectionIndex(), table.getSelectionModel().getAnchorSelectionIndex());
      Rectangle rect = table.getCellRect(row, column, false);
      if (!visibleRect.intersects(rect)) {
        table.scrollRectToVisible(rect);
      }
      popupMenuPoint = new Point(rect.x, rect.y + rect.height - 1);
    }
    else if (component instanceof PopupOwner popupOwner) {
      JComponent popupComponent = popupOwner.getPopupComponent();
      if (popupComponent == null || popupComponent == popupOwner) {
        popupMenuPoint = ((PopupOwner)component).getBestPopupPosition();
      }
      else {
        popupMenuPoint = guessBestPopupLocation(popupComponent).getPoint(component);
      }
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

  @Override
  public @NotNull RelativePoint guessBestPopupLocation(@NotNull Editor editor) {
    Point p = getVisibleBestPopupLocation(editor);
    if (p == null) {
      final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      p = new Point(visibleArea.x + visibleArea.width / 3, visibleArea.y + visibleArea.height / 2);
    }
    return new RelativePoint(editor.getContentComponent(), p);
  }

  private static @Nullable Point getVisibleBestPopupLocation(@NotNull Editor editor) {
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
  public @NotNull List<JBPopup> getChildPopups(@NotNull Component component) {
    return AbstractPopup.getChildPopups(component);
  }

  @Override
  public boolean isPopupActive() {
  return IdeEventQueue.getInstance().isPopupActive();
  }

  @Override
  public @NotNull BalloonBuilder createBalloonBuilder(@NotNull JComponent content) {
    return new BalloonPopupBuilderImpl(myStorage, content);
  }

  @Override
  public @NotNull BalloonBuilder createDialogBalloonBuilder(@NotNull JComponent content, @PopupTitle @Nullable String title) {
    final BalloonPopupBuilderImpl builder = new BalloonPopupBuilderImpl(myStorage, content);
    return fillDialogBalloonBuilder(builder, title);
  }

  protected @NotNull BalloonBuilder fillDialogBalloonBuilder(BalloonBuilder builder, @PopupTitle @Nullable String title) {
    final Color bg = UIManager.getColor("Panel.background");
    final Color borderOriginal = JBColor.DARK_GRAY;
    final Color border = ColorUtil.toAlpha(borderOriginal, 75);
    return builder
      .setDialogMode(true)
      .setTitle(title)
      .setAnimationCycle(200)
      .setFillColor(bg)
      .setBorderColor(border)
      .setHideOnClickOutside(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setCloseButtonEnabled(true)
      .setShadow(true);
  }

  @Override
  public @NotNull BalloonBuilder createHtmlTextBalloonBuilder(@NotNull String htmlContent,
                                                              @Nullable Icon icon,
                                                              Color textColor,
                                                              Color fillColor,
                                                              @Nullable HyperlinkListener listener) {
    return createHtmlTextBalloonBuilder(new Html(htmlContent), icon, textColor, fillColor, listener);
  }

  @Override
  public @NotNull BalloonBuilder createHtmlTextBalloonBuilder(@NotNull Html html,
                                                              @Nullable Icon icon,
                                                              Color textColor,
                                                              Color fillColor,
                                                              @Nullable HyperlinkListener listener) {
    if (textColor == null) {
      textColor = MessageType.INFO.getTitleForeground();
    }
    if (fillColor == null) {
      fillColor = MessageType.INFO.getPopupBackground();
    }

    JEditorPane text = IdeTooltipManager.initPane(html, new HintHint().setTextFg(textColor).setAwtTooltip(true), null, true);

    if (listener != null) {
      text.addHyperlinkListener(listener);
    }
    text.setEditable(false);
    NonOpaquePanel.setTransparent(text);
    text.setBorder(null);


    JLabel label = new JLabel();
    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    final NonOpaquePanel textWrapper = new NonOpaquePanel(new BorderLayout());
    JScrollPane scrolledText = ScrollPaneFactory.createScrollPane(text, true);
    scrolledText.setBackground(fillColor);
    scrolledText.getViewport().setBackground(fillColor);
    textWrapper.add(scrolledText, BorderLayout.CENTER);
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

  @Override
  public @NotNull BalloonBuilder createHtmlTextBalloonBuilder(@NotNull String htmlContent,
                                                              @NotNull MessageType messageType,
                                                              @Nullable HyperlinkListener listener) {
    return createHtmlTextBalloonBuilder(htmlContent, messageType.getDefaultIcon(), messageType.getTitleForeground(),
                                        messageType.getPopupBackground(), listener).setBorderColor(messageType.getBorderColor());
  }

  public static final class InlineActionItem implements AnActionHolder {
    private final AnAction myAction;
    private Icon myIcon;
    private Icon mySelectedIcon;
    private @NlsActions.ActionText String myText;
    private final int maxIconWidth;
    private final int maxIconHeight;
    private boolean myIsKeepPopupOpen;
    private boolean myIsAlwaysVisible;

    public InlineActionItem(AnAction action, int maxIconWidth, int maxIconHeight) {
      myAction = action;
      this.maxIconWidth = maxIconWidth;
      this.maxIconHeight = maxIconHeight;
    }

    public void updateFromPresentation(@NotNull Presentation presentation, @NotNull String actionPlace) {
      Pair<Icon, Icon> icons = ActionStepBuilder.calcRawIcons(myAction, presentation, false);
      Icon icon = icons.first;
      Icon selectedIcon = icons.second;

      if (maxIconWidth != -1 && maxIconHeight != -1) {
        icon = scaleIconToSize(icon, maxIconWidth, maxIconHeight);
        selectedIcon = scaleIconToSize(selectedIcon, maxIconWidth, maxIconHeight);
      }

      if (icon == null) {
        icon = selectedIcon == null ? EmptyIcon.create(maxIconWidth, maxIconHeight) : selectedIcon;
      }
      boolean disableIcon = Boolean.TRUE.equals(presentation.getClientProperty(DISABLE_ICON_IN_LIST));
      myIsKeepPopupOpen = myIsKeepPopupOpen || presentation.isMultiChoice() || myAction instanceof KeepingPopupOpenAction;
      myIsAlwaysVisible = Boolean.TRUE.equals(presentation.getClientProperty(ActionMenu.ALWAYS_VISIBLE));

      myIcon = disableIcon ? null : icon;
      mySelectedIcon = selectedIcon;
      myText = presentation.getText();
    }

    public boolean isKeepPopupOpen() {
      return myIsKeepPopupOpen;
    }

    public boolean isAlwaysVisible() {
      return myIsAlwaysVisible;
    }

    @Override
    public @NotNull AnAction getAction() {
      return myAction;
    }

    public Icon getIcon(boolean selected) {
      return selected && mySelectedIcon != null ? mySelectedIcon : myIcon;
    }

    public @NlsActions.ActionText String getText() {
      return myText;
    }
  }

  public static final class ActionItem implements ShortcutProvider, AnActionHolder, NumericMnemonicItem {
    private final AnAction myAction;
    private @NlsActions.ActionText String myText;
    private @NlsContexts.DetailedDescription String myDescription;
    private @NlsContexts.DetailedDescription String myTooltip;
    private @NlsContexts.ListItem String myValue;
    private boolean myIsEnabled;
    private boolean myIsPerformGroup;
    private boolean myIsSubstepSuppressed;
    private Icon myIcon;
    private Icon mySelectedIcon;
    private boolean myIsKeepPopupOpen;

    private final int maxIconWidth;
    private final int maxIconHeight;

    private final Character myMnemonicChar;
    private final boolean myMnemonicsEnabled;
    private final boolean myHonorActionMnemonics;

    boolean myPrependWithSeparator;
    private @NlsContexts.Separator String mySeparatorText;

    private final @NotNull List<InlineActionItem> myInlineActions;

    ActionItem(@NotNull AnAction action,
               @Nullable Character mnemonicChar,
               boolean mnemonicsEnabled,
               boolean honorActionMnemonics,
               int maxIconWidth,
               int maxIconHeight,
               boolean prependWithSeparator,
               @NlsContexts.Separator String separatorText) {
      this(action, mnemonicChar, mnemonicsEnabled, honorActionMnemonics, maxIconWidth, maxIconHeight, prependWithSeparator, separatorText,
           Collections.emptyList());
    }

    ActionItem(@NotNull AnAction action,
               @Nullable Character mnemonicChar,
               boolean mnemonicsEnabled,
               boolean honorActionMnemonics,
               int maxIconWidth,
               int maxIconHeight,
               boolean prependWithSeparator,
               @NlsContexts.Separator String separatorText,
               @NotNull List<InlineActionItem> inlineActions) {
      myAction = action;
      myMnemonicChar = mnemonicChar;
      myMnemonicsEnabled = mnemonicsEnabled;
      myHonorActionMnemonics = honorActionMnemonics;
      this.maxIconWidth = maxIconWidth;
      this.maxIconHeight = maxIconHeight;
      myPrependWithSeparator = prependWithSeparator;
      mySeparatorText = separatorText;
      myInlineActions = inlineActions;

      // Make sure com.intellij.dvcs.ui.BranchActionGroupPopup.MoreAction.updateActionText is long dead before removing
      myAction.getTemplatePresentation().addPropertyChangeListener(evt -> {
        if (Presentation.PROP_TEXT.equals(evt.getPropertyName())) {
          myText = myAction.getTemplatePresentation().getText();
        }
      });
    }

    ActionItem(@NotNull AnAction action,
               @NotNull @NlsActions.ActionText String text) {
      myAction = action;
      myText = text;

      myMnemonicChar = null;
      myMnemonicsEnabled = false;
      myHonorActionMnemonics = false;
      maxIconWidth = -1;
      maxIconHeight = -1;
      myPrependWithSeparator = false;
      mySeparatorText = null;
      myInlineActions = Collections.emptyList();
    }

    public @NotNull List<InlineActionItem> getInlineActions() {
      return myInlineActions;
    }

    void updateFromPresentation(@NotNull Presentation presentation, @NotNull String actionPlace) {
      String text = presentation.getText();
      if (text != null && !myMnemonicsEnabled && myHonorActionMnemonics) {
        text = TextWithMnemonic.fromPlainText(text, (char)myAction.getTemplatePresentation().getMnemonic()).toString();
      }
      if (StringUtil.isEmpty(text)) {
        Utils.reportEmptyTextMenuItem(myAction, actionPlace);
        text = "";
      }
      myText = ActionPresentationDecorator.decorateTextIfNeeded(myAction, text);
      myDescription =  presentation.getDescription();
      //noinspection deprecation
      myTooltip = (String)presentation.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY);

      myIsEnabled = presentation.isEnabled();
      myIsPerformGroup = myAction instanceof ActionGroup && presentation.isPerformGroup();
      myIsSubstepSuppressed = myAction instanceof ActionGroup && Utils.isSubmenuSuppressed(presentation);
      myIsKeepPopupOpen = myIsKeepPopupOpen || presentation.isMultiChoice() || myAction instanceof KeepingPopupOpenAction;

      Pair<Icon, Icon> icons = ActionStepBuilder.calcRawIcons(myAction, presentation, false);
      Icon icon = icons.first;
      Icon selectedIcon = icons.second;

      if (maxIconWidth != -1 && maxIconHeight != -1) {
        icon = scaleIconToSize(icon, maxIconWidth, maxIconHeight);
        selectedIcon = scaleIconToSize(selectedIcon, maxIconWidth, maxIconHeight);
      }

      if (icon == null) {
        icon = selectedIcon != null ? selectedIcon : EmptyIcon.create(maxIconWidth, maxIconHeight);
      }

      boolean disableIcon = Boolean.TRUE.equals(presentation.getClientProperty(DISABLE_ICON_IN_LIST));

      myIcon = disableIcon ? null : icon;
      mySelectedIcon = selectedIcon;

      myValue = presentation.getClientProperty(Presentation.PROP_VALUE);
    }

    @Override
    public @Nullable Character getMnemonicChar() {
      return myMnemonicChar;
    }

    @Override
    public boolean digitMnemonicsEnabled() {
      return myMnemonicsEnabled;
    }

    @Override
    public @NotNull AnAction getAction() {
      return myAction;
    }

    public @NotNull @NlsActions.ActionText String getText() {
      return myText;
    }

    public @Nullable Icon getIcon(boolean selected) {
      return selected && mySelectedIcon != null ? mySelectedIcon : myIcon;
    }

    public boolean isPrependWithSeparator() {
      return myPrependWithSeparator;
    }

    public @NlsContexts.Separator String getSeparatorText() {
      return mySeparatorText;
    }

    public void setSeparatorText(@NlsContexts.Separator String separatorText) {
      myPrependWithSeparator = separatorText != null;
      mySeparatorText = separatorText;
    }

    public boolean isEnabled() { return myIsEnabled; }

    public boolean isPerformGroup() { return myIsPerformGroup; }

    boolean isSubstepSuppressed() { return myIsSubstepSuppressed; }

    boolean isKeepPopupOpen() { return myIsKeepPopupOpen; }

    public @NlsContexts.DetailedDescription String getDescription() {
      return myDescription == null ? myTooltip : myDescription;
    }

    public @NlsContexts.DetailedDescription String getTooltip() {
      return myTooltip;
    }

    @Override
    public @NotNull ShortcutSet getShortcut() {
      return myAction.getShortcutSet();
    }

    @Override
    public String toString() {
      return myText;
    }

    public @NlsContexts.ListItem String getValue() {
      return myValue;
    }
  }

  private static @Nullable Icon scaleIconToSize(@Nullable Icon icon, int maxIconWidth, int maxIconHeight) {
    if (icon == null || icon instanceof EmptyIcon) {
      return icon;
    }

    float scale = (float)Math.min(maxIconWidth, maxIconHeight) / Math.min(icon.getIconWidth(), icon.getIconHeight());
    return scale == 1 ? icon : CustomIconUtilKt.scaleIconOrLoadCustomVersion(icon, scale);
  }
}
