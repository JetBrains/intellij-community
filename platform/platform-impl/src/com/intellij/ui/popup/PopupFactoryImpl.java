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
package com.intellij.ui.popup;

import com.intellij.CommonBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.mock.MockConfirmation;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PopupFactoryImpl extends JBPopupFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupFactoryImpl");
  private static final Icon QUICK_LIST_ICON = IconLoader.getIcon("/actions/quickList.png");

  public ListPopup createConfirmation(String title, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(), onYes, defaultOptionIndex);
  }

  public ListPopup createConfirmation(String title, final String yesText, String noText, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, yesText, noText, onYes, EmptyRunnable.getInstance(), defaultOptionIndex);
  }

  public JBPopup createMessage(String text) {
    return createListPopup(new BaseListPopupStep<String>(null, new String[]{text})); 
  }

  public ListPopup createConfirmation(String title, final String yesText, String noText, final Runnable onYes, final Runnable onNo, int defaultOptionIndex) {

      final BaseListPopupStep<String> step = new BaseListPopupStep<String>(title, new String[]{yesText, noText}) {
        public PopupStep onChosen(String selectedValue, final boolean finalChoice) {
          if (selectedValue.equals(yesText)) {
            onYes.run();
          }
          else {
            onNo.run();
          }
          return FINAL_CHOICE;
        }

        public void canceled() {
          onNo.run();
        }

        public boolean isMnemonicsNavigationEnabled() {
          return true;
        }
      };
      step.setDefaultOptionIndex(defaultOptionIndex);

    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    return app == null || !app.isUnitTestMode() ? new ListPopupImpl(step) : new MockConfirmation(step, yesText);
  }


  private static ListPopup createActionGroupPopup(final String title,
                                          final ActionGroup actionGroup,
                                          DataContext dataContext,
                                          boolean showNumbers,
                                          boolean useAlphaAsNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics,
                                          final Runnable disposeCallback,
                                          final int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
                                  maxRowCount, null);
  }

  public ListPopup createActionGroupPopup(final String title,
                                          final ActionGroup actionGroup,
                                          DataContext dataContext,
                                          boolean showNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics,
                                          final Runnable disposeCallback,
                                          final int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
                                  maxRowCount, null);
  }

  private static ListPopup createActionGroupPopup(final String title,
                                          final ActionGroup actionGroup,
                                          DataContext dataContext,
                                          boolean showNumbers,
                                          boolean useAlphaAsNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics,
                                          final Runnable disposeCallback,
                                          final int maxRowCount,
                                          final Condition<AnAction> preselectActionCondition) {
    final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);

    final ActionStepBuilder builder = new ActionStepBuilder(dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics);
    builder.buildGroup(actionGroup);
    final List<ActionItem> items = builder.getItems();

    int defaultOptionIndex = 0;
    if (preselectActionCondition != null) {
      for (int i = 0; i < items.size(); i++) {
        final AnAction action = items.get(i).getAction();
        if (preselectActionCondition.value(action)) {
          defaultOptionIndex = i;
          break;
        }
      }
    }

    ListPopupStep step = new ActionPopupStep(items, title, component, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items),
                                             defaultOptionIndex,
                                             false);

    final ListPopupImpl popup = new ListPopupImpl(step, maxRowCount) {
      public void dispose() {
        if (disposeCallback != null) {
          disposeCallback.run();
        }
        ActionMenu.showDescriptionInStatusBar(true, component, null);
        super.dispose();
      }
    };
    popup.addListSelectionListener(new ListSelectionListener(){
      public void valueChanged(ListSelectionEvent e) {
        final JList list = (JList)e.getSource();
        final ActionItem actionItem = (ActionItem)list.getSelectedValue();
        if (actionItem == null) return;
        AnAction action = actionItem.getAction();
        Presentation presentation = new Presentation();
        presentation.setDescription(action.getTemplatePresentation().getDescription());
        action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(component), ActionPlaces.UNKNOWN, presentation,
                                        ActionManager.getInstance(), 0));
        ActionMenu.showDescriptionInStatusBar(true, component, presentation.getDescription());
      }
    });
    return popup;
  }
  public ListPopup createActionGroupPopup(final String title,
                                          final ActionGroup actionGroup,
                                          DataContext dataContext,
                                          boolean showNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics,
                                          final Runnable disposeCallback,
                                          final int maxRowCount,
                                          final Condition<AnAction> preselectActionCondition) {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, true, showDisabledActions, honorActionMnemonics,
                                  disposeCallback, maxRowCount, preselectActionCondition);
  }

  public ListPopup createActionGroupPopup(String title,
                                          ActionGroup actionGroup,
                                          DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions) {
    return createActionGroupPopup(title, actionGroup, dataContext,
                                  selectionAidMethod == ActionSelectionAid.NUMBERING || selectionAidMethod == ActionSelectionAid.ALPHA_NUMBERING,
                                  selectionAidMethod == ActionSelectionAid.ALPHA_NUMBERING,
                                  showDisabledActions,
                                  selectionAidMethod == ActionSelectionAid.MNEMONICS,
                                  null, -1);
  }

  public ListPopup createActionGroupPopup(String title,
                                          ActionGroup actionGroup,
                                          DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions,
                                          Runnable disposeCallback,
                                          int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext,
                                  selectionAidMethod == ActionSelectionAid.NUMBERING || selectionAidMethod == ActionSelectionAid.ALPHA_NUMBERING,
                                  selectionAidMethod == ActionSelectionAid.ALPHA_NUMBERING,
                                  showDisabledActions,
                                  selectionAidMethod == ActionSelectionAid.MNEMONICS,
                                  disposeCallback,
                                  maxRowCount);
  }

  public ListPopupStep createActionsStep(final ActionGroup actionGroup,
                                         final DataContext dataContext,
                                         final boolean showNumbers,
                                         final boolean showDisabledActions,
                                         final String title,
                                         final Component component,
                                         final boolean honorActionMnemonics) {
    return createActionsStep(actionGroup, dataContext, showNumbers, showDisabledActions, title, component, honorActionMnemonics, 0, false);
  }

  private static ListPopupStep createActionsStep(ActionGroup actionGroup, DataContext dataContext, boolean showNumbers, boolean useAlphaAsNumbers, boolean showDisabledActions,
                                         String title, Component component, boolean honorActionMnemonics, int defaultOptionIndex,
                                         final boolean autoSelectionEnabled) {
    final ActionStepBuilder builder = new ActionStepBuilder(dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics);
    builder.buildGroup(actionGroup);
    final List<ActionItem> items = builder.getItems();

    return new ActionPopupStep(items, title, component, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items), defaultOptionIndex,
                               autoSelectionEnabled);
  }

  public ListPopupStep createActionsStep(ActionGroup actionGroup, DataContext dataContext, boolean showNumbers, boolean showDisabledActions,
                                         String title, Component component, boolean honorActionMnemonics, int defaultOptionIndex,
                                         final boolean autoSelectionEnabled) {
    return createActionsStep(actionGroup, dataContext, showNumbers, true, showDisabledActions, title, component, honorActionMnemonics,
                             defaultOptionIndex, autoSelectionEnabled);
  }

  private static boolean itemsHaveMnemonics(final List<ActionItem> items) {
    for (ActionItem item : items) {
      if (item.getAction().getTemplatePresentation().getMnemonic() != 0) return true;
    }

    return false;
  }

  public ListPopup createWizardStep(PopupStep step) {
    return new ListPopupImpl((ListPopupStep) step);
  }

  public ListPopup createListPopup(ListPopupStep step) {
    return new ListPopupImpl(step);
  }

  public TreePopup createTree(JBPopup parent, TreePopupStep aStep, Object parentValue) {
    return new TreePopupImpl(parent, aStep, parentValue);
  }

  public TreePopup createTree(TreePopupStep aStep) {
    return new TreePopupImpl(aStep);
  }

  public ComponentPopupBuilder createComponentPopupBuilder(JComponent content, JComponent prefferableFocusComponent) {
    return new ComponentPopupBuilderImpl(content, prefferableFocusComponent);
  }

 
  public RelativePoint guessBestPopupLocation(DataContext dataContext) {
    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component component = focusManager.getFocusOwner();
    JComponent focusOwner=component instanceof JComponent ? (JComponent)component : null;

    if (focusOwner == null) {
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      IdeFrameImpl frame = project == null ? null : ((WindowManagerEx)WindowManager.getInstance()).getFrame(project);
      focusOwner = frame == null ? null : frame.getRootPane();
      if (focusOwner == null) {
        throw new IllegalArgumentException("focusOwner cannot be null");
      }
    }

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null && focusOwner == editor.getContentComponent()) {
      return guessBestPopupLocation(editor);
    }
    else {
      return guessBestPopupLocation(focusOwner);
    }
  }

  public RelativePoint guessBestPopupLocation(final JComponent component) {
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
          popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 4, cellBounds.y + cellBounds.height);
          break;
        }
      }
    }
    else if (component instanceof JTree) { // JTree
      JTree tree = (JTree)component;
      int[] selectionRows = tree.getSelectionRows();
      for (int i = 0; selectionRows != null && i < selectionRows.length; i++) {
        int row = selectionRows[i];
        Rectangle rowBounds = tree.getRowBounds(row);
        if (visibleRect.y <= rowBounds.y && rowBounds.y <= visibleRect.y + visibleRect.height) {
          popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 4, rowBounds.y + rowBounds.height);
          break;
        }
      }
    } else if (component instanceof PopupOwner){
      popupMenuPoint = ((PopupOwner)component).getBestPopupPosition();
    }
    // TODO[vova] add usability for JTable
    if (popupMenuPoint == null) {
      popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height / 2);
    }

    return new RelativePoint(component, popupMenuPoint);
  }

  public RelativePoint guessBestPopupLocation(Editor editor) {
    VisualPosition logicalPosition = editor.getCaretModel().getVisualPosition();
    Point p = editor.visualPositionToXY(new VisualPosition(logicalPosition.line + 1, logicalPosition.column));

    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    if (!visibleArea.contains(p)) {
      p = new Point((visibleArea.x + visibleArea.width) / 2, (visibleArea.y + visibleArea.height) / 2);
    }

    return new RelativePoint(editor.getContentComponent(), p);
  }

  public Point getCenterOf(JComponent container, JComponent content) {
    return AbstractPopup.getCenterOf(container, content);
  }

  private static class ActionItem {
    private final AnAction myAction;
    private final String myText;
    private final boolean myIsEnabled;
    private final Icon myIcon;
    private final boolean myPrependWithSeparator;
    private final String mySeparatorText;

    private ActionItem(@NotNull AnAction action, @NotNull String text, boolean enabled, Icon icon, final boolean prependWithSeparator, String separatorText) {
      myAction = action;
      myText = text;
      myIsEnabled = enabled;
      myIcon = icon;
      myPrependWithSeparator = prependWithSeparator;
      mySeparatorText = separatorText;
    }

    @NotNull
    public AnAction getAction() {
      return myAction;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    public Icon getIcon() {
      return myIcon;
    }                                                                                                                           

    public boolean isPrependWithSeparator() {
      return myPrependWithSeparator;
    }

    public String getSeparatorText() {
      return mySeparatorText;
    }

    public boolean isEnabled() { return myIsEnabled; }
  }

  private static class ActionPopupStep implements ListPopupStep<ActionItem>, MnemonicNavigationFilter<ActionItem>, SpeedSearchFilter<ActionItem> {
    private final List<ActionItem> myItems;
    private final String myTitle;
    private final Component myContext;
    private final boolean myEnableMnemonics;
    private final int myDefaultOptionIndex;
    private final boolean myAutoSelectionEnabled;
    private Runnable myFinalRunnable;

    private ActionPopupStep(@NotNull final List<ActionItem> items,
                           final String title,
                           Component context,
                           boolean enableMnemonics,
                           final int defaultOptionIndex, final boolean autoSelection) {
      myItems = items;
      myTitle = title;
      myContext = context;
      myEnableMnemonics = enableMnemonics;
      myDefaultOptionIndex = defaultOptionIndex;
      myAutoSelectionEnabled = autoSelection;
    }

    @NotNull
    public List<ActionItem> getValues() {
      return myItems;
    }

    public boolean isSelectable(final ActionItem value) {
      return value.isEnabled();
    }

    public int getMnemonicPos(final ActionItem value) {
      final String text = getTextFor(value);
      int i = text.indexOf(UIUtil.MNEMONIC);
      if (i < 0) {
        i = text.indexOf('&');
      }
      if (i < 0) {
        i = text.indexOf('_');
      }
      return i;
    }

    public Icon getIconFor(final ActionItem aValue) {
      return aValue.getIcon();
    }

    @NotNull
    public String getTextFor(final ActionItem value) {
      return value.getText();
    }

    public ListSeparator getSeparatorAbove(final ActionItem value) {
      return value.isPrependWithSeparator() ? new ListSeparator(value.getSeparatorText()) : null;
    }

    public int getDefaultOptionIndex() {
      return myDefaultOptionIndex;
    }

    public String getTitle() {
      return myTitle;
    }

    public PopupStep onChosen(final ActionItem actionChoice, final boolean finalChoice) {
      if (!actionChoice.isEnabled()) return FINAL_CHOICE;
      final AnAction action = actionChoice.getAction();
      DataManager mgr = DataManager.getInstance();

      final DataContext dataContext = myContext != null ? mgr.getDataContext(myContext) : mgr.getDataContext();

      if (action instanceof ActionGroup && (!finalChoice || !((ActionGroup)action).canBePerformed(dataContext))) {
          return JBPopupFactory.getInstance().createActionsStep((ActionGroup)action, dataContext, myEnableMnemonics, false, null, myContext, false);
      }
      else {
        myFinalRunnable = new Runnable() {
          public void run() {
            action.actionPerformed(
              new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, (Presentation)action.getTemplatePresentation().clone(),
                                ActionManager.getInstance(), 0));
          }
        };
        return FINAL_CHOICE;
      }
    }

    public Runnable getFinalRunnable() {
      return myFinalRunnable;
    }

    public boolean hasSubstep(final ActionItem selectedValue) {
      return selectedValue != null && selectedValue.isEnabled() && selectedValue.getAction() instanceof ActionGroup;
    }

    public void canceled() {
    }

    public boolean isMnemonicsNavigationEnabled() {
      return myEnableMnemonics;
    }

    public MnemonicNavigationFilter<ActionItem> getMnemonicNavigationFilter() {
      return this;
    }

    public boolean canBeHidden(final ActionItem value) {
      return true;
    }

    public String getIndexedString(final ActionItem value) {
      return getTextFor(value);
    }

    public boolean isSpeedSearchEnabled() {
      return true;
    }

    public boolean isAutoSelectionEnabled() {
      return myAutoSelectionEnabled;
    }

    public SpeedSearchFilter<ActionItem> getSpeedSearchFilter() {
      return this;
    }
  }

  @Nullable
  public List<JBPopup> getChildPopups(@NotNull final Component component) {
    return FocusTrackback.getChildPopups(component);
  }

  @Override
  public boolean isPopupActive() {
  return IdeEventQueue.getInstance().isPopupActive();
  }

  private static class ActionStepBuilder {
    private final List<ActionItem> myListModel;
    private final DataContext myDataContext;
    private final boolean myShowNumbers;
    private final boolean myUseAlphaAsNumbers;
    private final boolean myShowDisabled;
    private final HashMap<AnAction, Presentation> myAction2presentation;
    private int myCurrentNumber;
    private boolean myPrependWithSeparator;
    private String mySeparatorText;
    private final boolean myHonorActionMnemonics;
    private Icon myEmptyIcon;
    private int myMaxIconWidth = -1;
    private int myMaxIconHeight = -1;

    private ActionStepBuilder(final DataContext dataContext,
                             final boolean showNumbers,
                             final boolean useAlphaAsNumbers,
                             final boolean showDisabled,
                             final boolean honorActionMnemonics) {
      myUseAlphaAsNumbers = useAlphaAsNumbers;
      myListModel = new ArrayList<ActionItem>();
      myDataContext = dataContext;
      myShowNumbers = showNumbers;
      myShowDisabled = showDisabled;
      myAction2presentation = new HashMap<AnAction, Presentation>();
      myCurrentNumber = 0;
      myPrependWithSeparator = false;
      mySeparatorText = null;
      myHonorActionMnemonics = honorActionMnemonics;
    }

    public List<ActionItem> getItems() {
      return myListModel;
    }

    public void buildGroup(ActionGroup actionGroup) {
      calcMaxIconSize(actionGroup);
      myEmptyIcon = myMaxIconHeight != -1 && myMaxIconWidth != -1 ? new EmptyIcon(myMaxIconWidth, myMaxIconHeight) : null;

      appendActionsFromGroup(actionGroup);

      if (myListModel.isEmpty()) {
        myListModel.add(new ActionItem(Utils.EMPTY_MENU_FILLER, Utils.NOTHING_HERE, false, null, false, null));
      }
    }

    private void calcMaxIconSize(final ActionGroup actionGroup) {
      AnAction[] actions = actionGroup.getChildren(new AnActionEvent(null, myDataContext,
                                                                     ActionPlaces.UNKNOWN,
                                                                     getPresentation(actionGroup),
                                                                     ActionManager.getInstance(),
                                                                     0));
      for (AnAction action : actions) {
        if (action == null) continue;
        if (action instanceof ActionGroup) {
          final ActionGroup group = (ActionGroup)action;
          if (!group.isPopup()) {
            calcMaxIconSize(group);
            continue;
          }
        }

        Icon icon = action.getTemplatePresentation().getIcon();
        if (icon != null) {
          final int width = icon.getIconWidth();
          final int height = icon.getIconHeight();
          if (myMaxIconWidth < width) {
            myMaxIconWidth = width;
          }
          if (myMaxIconHeight < height) {
            myMaxIconHeight = height;
          }
        }
      }
    }

    private void appendActionsFromGroup(final ActionGroup actionGroup) {
      AnAction[] actions = actionGroup.getChildren(new AnActionEvent(null, myDataContext,
                                                                     ActionPlaces.UNKNOWN,
                                                                     getPresentation(actionGroup),
                                                                     ActionManager.getInstance(),
                                                                     0));
      for (AnAction action : actions) {
        if (action instanceof Separator) {
          myPrependWithSeparator = true;
          mySeparatorText = ((Separator)action).getText();
        }
        else {
          if (action instanceof ActionGroup) {
            ActionGroup group = (ActionGroup)action;
            if (group.isPopup()) {
              appendAction(group);
            }
            else {
              appendActionsFromGroup(group);
            }
          }
          else {
            appendAction(action);
          }
        }
      }
    }

    private void appendAction(AnAction action) {
      Presentation presentation = getPresentation(action);
      AnActionEvent event = new AnActionEvent(null, myDataContext,
                                              ActionPlaces.UNKNOWN,
                                              presentation,
                                              ActionManager.getInstance(),
                                              0);

      ActionUtil.performDumbAwareUpdate(action, event, true);
      if ((myShowDisabled || presentation.isEnabled()) && presentation.isVisible()) {
        String text = presentation.getText();
        if (myShowNumbers) {
          if (myCurrentNumber < 9) {
            text = "&" + (myCurrentNumber + 1) + ". " + text;
          }
          else if (myCurrentNumber == 9) {
            text = "&" + 0 + ". " + text;
          }
          else if (myUseAlphaAsNumbers) {
            text = "&" + (char)('A' + myCurrentNumber - 10) + ". " + text;
          }
          myCurrentNumber++;
        }
        else if (myHonorActionMnemonics) {
          text = Presentation.restoreTextWithMnemonic(text, action.getTemplatePresentation().getMnemonic());
        }

        Icon icon = presentation.getIcon();
        if (icon == null) {
          @NonNls final String actionId = ActionManager.getInstance().getId(action);
          icon = actionId != null && actionId.startsWith("QuickList.") ? QUICK_LIST_ICON : myEmptyIcon;

        }
        boolean prependSeparator = !myListModel.isEmpty() && myPrependWithSeparator;
        assert text != null : action + " has no presentation";
        myListModel.add(new ActionItem(action, text, presentation.isEnabled(), icon, prependSeparator, mySeparatorText));
        myPrependWithSeparator = false;
        mySeparatorText = null;
      }
    }

    private Presentation getPresentation(AnAction action) {
      Presentation presentation = myAction2presentation.get(action);
      if (presentation == null) {
        presentation = (Presentation)action.getTemplatePresentation().clone();
        myAction2presentation.put(action, presentation);
      }
      return presentation;
    }
  }

  public BalloonBuilder createBalloonBuilder(@NotNull final JComponent content) {
    return new BalloonPopupBuilderImpl(content);
  }

  public BalloonBuilder createHtmlTextBalloonBuilder(@NotNull final String htmlContent, @Nullable final Icon icon, final Color fillColor,
                                                        @Nullable final HyperlinkListener listener) {


    final JEditorPane text = new JEditorPane();
    text.setEditorKit(new HTMLEditorKit());
    if (listener != null) {
      text.addHyperlinkListener(listener);
    }
    text.setText(UIUtil.toHtml(htmlContent));
    final JLabel label = new JLabel(text.getText());
    final Dimension size = label.getPreferredSize();
    text.setEditable(false);
    NonOpaquePanel.setTransparent(text);
    text.setBorder(null);
    text.setPreferredSize(size);


    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    final NonOpaquePanel textWrapper = new NonOpaquePanel(new GridBagLayout());
    textWrapper.add(text);
    content.add(textWrapper, BorderLayout.CENTER);

    final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
    north.add(new JLabel(icon), BorderLayout.NORTH);
    content.add(north, BorderLayout.WEST);

    content.setBorder(new EmptyBorder(2, 4, 2, 4));

    final BalloonBuilder builder = createBalloonBuilder(content);

    builder.setFillColor(fillColor);

    return builder;
  }

  @Override
  public BalloonBuilder createHtmlTextBalloonBuilder(@NotNull String htmlContent,
                                                     MessageType messageType,
                                                     @Nullable HyperlinkListener listener) {
    return createHtmlTextBalloonBuilder(htmlContent, messageType.getDefaultIcon(), messageType.getPopupBackground(), listener);
  }
}
