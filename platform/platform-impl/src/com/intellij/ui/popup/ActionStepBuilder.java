package com.intellij.ui.popup;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ActionStepBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupFactoryImpl");

  private final List<PopupFactoryImpl.ActionItem> myListModel;
  private final DataContext myDataContext;
  private final boolean                         myShowNumbers;
  private final boolean                         myUseAlphaAsNumbers;
  private final boolean                         myShowDisabled;
  private final HashMap<AnAction, Presentation> myAction2presentation;
  private       int                             myCurrentNumber;
  private       boolean                         myPrependWithSeparator;
  private       String                          mySeparatorText;
  private final boolean                         myHonorActionMnemonics;
  private IconWrapper myEmptyIcon;
  private int myMaxIconWidth  = -1;
  private int myMaxIconHeight = -1;
  @NotNull private String myActionPlace;

  public ActionStepBuilder(@NotNull DataContext dataContext,
                           final boolean showNumbers,
                           final boolean useAlphaAsNumbers,
                           final boolean showDisabled,
                           final boolean honorActionMnemonics)
  {
    myUseAlphaAsNumbers = useAlphaAsNumbers;
    myListModel = new ArrayList<>();
    myDataContext = dataContext;
    myShowNumbers = showNumbers;
    myShowDisabled = showDisabled;
    myAction2presentation = new HashMap<>();
    myCurrentNumber = 0;
    myPrependWithSeparator = false;
    mySeparatorText = null;
    myHonorActionMnemonics = honorActionMnemonics;
    myActionPlace = ActionPlaces.UNKNOWN;
  }

  public void setActionPlace(@NotNull String actionPlace) {
    myActionPlace = actionPlace;
  }

  @NotNull
  public List<PopupFactoryImpl.ActionItem> getItems() {
    return myListModel;
  }

  public void buildGroup(@NotNull ActionGroup actionGroup) {
    calcMaxIconSize(actionGroup);
    myEmptyIcon = myMaxIconHeight != -1 && myMaxIconWidth != -1 ? createWrapper(null) : null;

    appendActionsFromGroup(actionGroup);

    if (myListModel.isEmpty()) {
      myListModel.add(new PopupFactoryImpl.ActionItem(Utils.EMPTY_MENU_FILLER, Utils.NOTHING_HERE, null, false, null, false, null));
    }
  }

  private void calcMaxIconSize(final ActionGroup actionGroup) {
    AnAction[] actions = actionGroup.getChildren(createActionEvent(actionGroup));
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
      if (icon == null && action instanceof Toggleable) icon = PlatformIcons.CHECK_ICON;
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

  @NotNull
  private AnActionEvent createActionEvent(@NotNull AnAction actionGroup) {
    final AnActionEvent actionEvent =
      new AnActionEvent(null, myDataContext, myActionPlace, getPresentation(actionGroup), ActionManager.getInstance(), 0);
    actionEvent.setInjectedContext(actionGroup.isInInjectedContext());
    return actionEvent;
  }

  private void appendActionsFromGroup(@NotNull ActionGroup actionGroup) {
    AnAction[] actions = actionGroup.getChildren(createActionEvent(actionGroup));
    for (AnAction action : actions) {
      if (action == null) {
        LOG.error("null action in group " + actionGroup);
        continue;
      }
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

  private void appendAction(@NotNull AnAction action) {
    Presentation presentation = getPresentation(action);
    AnActionEvent event = createActionEvent(action);

    ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, event, true);
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

      Icon icon = presentation.isEnabled() ? presentation.getIcon() : IconLoader.getDisabledIcon(presentation.getIcon());
      IconWrapper iconWrapper;
      if (icon == null && presentation.getHoveredIcon() == null) {
        @NonNls final String actionId = ActionManager.getInstance().getId(action);
        if (actionId != null && actionId.startsWith("QuickList.")) {
          iconWrapper = createWrapper(AllIcons.Actions.QuickList);
        }
        else if (action instanceof Toggleable) {
          boolean toggled = Boolean.TRUE.equals(presentation.getClientProperty(Toggleable.SELECTED_PROPERTY));
          iconWrapper = toggled ? createWrapper(PlatformIcons.CHECK_ICON) : myEmptyIcon;
        }
        else {
          iconWrapper = myEmptyIcon;
        }
      }
      else {
        iconWrapper = new IconWrapper(icon, presentation.getHoveredIcon(), myMaxIconWidth, myMaxIconHeight);
      }
      boolean prependSeparator = (!myListModel.isEmpty() || mySeparatorText != null) && myPrependWithSeparator;
      assert text != null : action + " has no presentation";
      myListModel.add(
        new PopupFactoryImpl.ActionItem(action, text, (String)presentation.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY), presentation.isEnabled(), iconWrapper,
                                        prependSeparator, mySeparatorText));
      myPrependWithSeparator = false;
      mySeparatorText = null;
    }
  }

  /**
   * Adjusts icon size to maximum, so that icons with different sizes were aligned correctly.
   */
  public static class IconWrapper extends IconUtil.IconSizeWrapper {

    @Nullable private Icon myIcon;
    @Nullable private Icon myHoverIcon;

    private boolean isHovered;

    public IconWrapper(@Nullable Icon icon, @Nullable Icon hoverIcon, int width, int height) {
      super(null, width, height);
      setIcons(icon, hoverIcon);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      paintIcon(myHoverIcon != null && isHovered ? myHoverIcon : myIcon, c, g, x, y);
    }

    public boolean isHovered() {
      return isHovered;
    }

    public void setHovered(boolean hovered) {
      isHovered = hovered;
    }

    public void setIcons(@Nullable Icon icon, @Nullable Icon hoveredIcon) {
      myIcon = icon;
      myHoverIcon = hoveredIcon;
    }
  }

  @NotNull
  public IconWrapper createWrapper(@Nullable Icon icon) {
    return new IconWrapper(icon, null, myMaxIconWidth, myMaxIconHeight);
  }

  private Presentation getPresentation(@NotNull AnAction action) {
    Presentation presentation = myAction2presentation.get(action);
    if (presentation == null) {
      presentation = action.getTemplatePresentation().clone();
      myAction2presentation.put(action, presentation);
    }
    return presentation;
  }
}
