/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.ClickListener;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.*;

/**
 * Base class for components which allow to set up filter for the VCS Log, by displaying a popup with available choices.
 */
abstract class FilterPopupComponent extends JPanel {

  /**
   * Special value that indicates that no filtering is on.
   */
  protected static final String ALL = "All";

  private static final int GAP_BEFORE_ARROW = 3;
  private static final int BORDER_SIZE = 2;
  private static final Border INNER_MARGIN_BORDER = BorderFactory.createEmptyBorder(2, 2, 2, 2);
  private static final Border FOCUSED_BORDER = createFocusedBorder();
  private static final Border UNFOCUSED_BORDER = createUnfocusedBorder();

  @NotNull private final JLabel myFilterNameLabel;
  @NotNull private final JLabel myFilterValueLabel;
  @NotNull private final VcsLogClassicFilterUi myFilterUi;

  FilterPopupComponent(@NotNull VcsLogClassicFilterUi filterUi, @NotNull String filterName) {
    myFilterUi = filterUi;
    myFilterNameLabel = new JLabel(filterName + ": ");
    myFilterValueLabel = new JLabel(ALL);
    setDefaultForeground();
    setFocusable(true);
    setBorder(UNFOCUSED_BORDER);

    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(myFilterNameLabel);
    add(myFilterValueLabel);
    add(Box.createHorizontalStrut(GAP_BEFORE_ARROW));
    add(new JLabel(AllIcons.Ide.Statusbar_arrows));

    showPopupMenuOnClick();
    showPopupMenuFromKeyboard();
    indicateHovering();
    indicateFocusing();
  }

  private static Border createFocusedBorder() {
    return BorderFactory.createCompoundBorder(new RoundedLineBorder(UIUtil.getHeaderActiveColor(), 10, BORDER_SIZE),
                                              INNER_MARGIN_BORDER);
  }

  private static Border createUnfocusedBorder() {
    return BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE),
                                              INNER_MARGIN_BORDER);
  }

  /**
   * Create popup actions available under this filter.
   */
  protected abstract ActionGroup createActionGroup();

  /**
   * Return the filter currently selected by this component.
   */
  @Nullable
  protected abstract VcsLogFilter getFilter();

  protected void setValue(@NotNull String newValue) {
    myFilterValueLabel.setText(newValue);
  }

  @NotNull
  public String getValue() {
    return myFilterValueLabel.getText();
  }

  protected void applyFilters() {
    myFilterUi.applyFilters();
  }

  /**
   * Returns the special action that indicates that no filtering is selected in this component.
   */
  protected AnAction createAllAction() {
    return new AllAction(this);
  }

  private void indicateFocusing() {
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        setBorder(FOCUSED_BORDER);
      }

      @Override
      public void focusLost(FocusEvent e) {
        setBorder(UNFOCUSED_BORDER);
      }
    });
  }

  private void showPopupMenuFromKeyboard() {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_DOWN) {
          showPopupMenu();
        }
      }
    });
  }

  private void showPopupMenuOnClick() {
    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent event, int clickCount) {
        showPopupMenu();
        return true;
      }
    }.installOn(this);
  }

  private void indicateHovering() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        setOnHoverForeground();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setDefaultForeground();
      }
    });
  }

  private void setDefaultForeground() {
    myFilterNameLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
    myFilterValueLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() :
                                     UIUtil.getInactiveTextColor().darker().darker());
  }

  private void setOnHoverForeground() {
    myFilterNameLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextAreaForeground());
    myFilterValueLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextFieldForeground());
  }

  private void showPopupMenu() {
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, createActionGroup(),
                                                                          DataManager.getInstance().getDataContext(this),
                                                                          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    popup.showUnderneathOf(this);
  }

  private static class AllAction extends SetValueAction {
    AllAction(@NotNull FilterPopupComponent component) {
      super(ALL, component);
    }
  }

  protected static class SetValueAction extends DumbAwareAction {

    private final String myValue;
    private final FilterPopupComponent myFilterComponent;

    SetValueAction(String value, FilterPopupComponent filterComponent) {
      super(value);
      myValue = value;
      myFilterComponent = filterComponent;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myFilterComponent.setValue(myValue);
      myFilterComponent.applyFilters();
    }
  }

}
