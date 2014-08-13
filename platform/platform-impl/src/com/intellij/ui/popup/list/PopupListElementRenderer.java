/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.popup.list;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

public class PopupListElementRenderer extends GroupedItemsListRenderer {
  private final ListPopupImpl myPopup;

  public PopupListElementRenderer(final ListPopupImpl aPopup) {
    super(new ListItemDescriptorAdapter() {
      @Override
      public String getTextFor(Object value) {
        return aPopup.getListStep().getTextFor(value);
      }

      @Override
      public Icon getIconFor(Object value) {
        return aPopup.getListStep().getIconFor(value);
      }

      @Override
      public boolean hasSeparatorAboveOf(Object value) {
        return aPopup.getListModel().isSeparatorAboveOf(value);
      }

      @Override
      public String getCaptionAboveOf(Object value) {
        return aPopup.getListModel().getCaptionAboveOf(value);
      }
    });
    myPopup = aPopup;
  }

  @Override
  protected void customizeComponent(JList list, Object value, boolean isSelected) {
    ListPopupStep<Object> step = myPopup.getListStep();
    boolean isSelectable = step.isSelectable(value);
    myTextLabel.setEnabled(isSelectable);

    if (step.isMnemonicsNavigationEnabled()) {
      final int pos = step.getMnemonicNavigationFilter().getMnemonicPos(value);
      if (pos != -1) {
        String text = myTextLabel.getText();
        text = text.substring(0, pos) + text.substring(pos + 1);
        myTextLabel.setText(text);
        myTextLabel.setDisplayedMnemonicIndex(pos);
      }
    }
    else {
      myTextLabel.setDisplayedMnemonicIndex(-1);
    }

    if (step.hasSubstep(value) && isSelectable) {
      myNextStepLabel.setVisible(true);
      final boolean isDark = ColorUtil.isDark(UIUtil.getListSelectionBackground());
      myNextStepLabel.setIcon(isSelected ? isDark ? AllIcons.Icons.Ide.NextStepInverted
                                                  : AllIcons.Icons.Ide.NextStep
                                         : AllIcons.Icons.Ide.NextStepGrayed);
    }
    else {
      myNextStepLabel.setVisible(false);
      //myNextStepLabel.setIcon(PopupIcons.EMPTY_ICON);
    }

    if (isSelected) {
      setSelected(myNextStepLabel);
    }
    else {
      setDeselected(myNextStepLabel);
    }
  }


}
