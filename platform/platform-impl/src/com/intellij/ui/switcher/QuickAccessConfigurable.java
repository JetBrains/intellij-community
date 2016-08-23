/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.*;

/**
* @author nik
*/
public class QuickAccessConfigurable extends JPanel implements SearchableConfigurable {
  private Set<String> myModifiers = new HashSet<>();
  private boolean myQaEnabled;
  private int myDelay;
  private JCheckBox myEnabled;
  private ModifierBox myCtrl;
  private ModifierBox myAlt;
  private ModifierBox myShift;
  private ModifierBox myMeta;
  private JPanel myConflicts;
  private JFormattedTextField myHoldTime;
  private QuickAccessSettings myQuickAccessSettings;

  public QuickAccessConfigurable(QuickAccessSettings quickAccessSettings) {
    myQuickAccessSettings = quickAccessSettings;
    JPanel north = new JPanel(new BorderLayout());
    VerticalBox box = new VerticalBox();
    north.add(box, BorderLayout.NORTH);

    setLayout(new BorderLayout());
    add(north, BorderLayout.WEST);

    myEnabled = new JCheckBox("Enable Quick Access");
    myEnabled.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        myQaEnabled = myEnabled.isSelected();
        processEnabled();
      }
    });
    box.add(myEnabled);

    VerticalBox kbConfig = new VerticalBox();

    JPanel modifiers = new JPanel(new FlowLayout(FlowLayout.CENTER)) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width *= 1.5;
        return size;
      }
    };
    myCtrl = new ModifierBox("control", KeyEvent.getKeyModifiersText(KeyEvent.CTRL_MASK));
    myAlt = new ModifierBox("alt", KeyEvent.getKeyModifiersText(KeyEvent.ALT_MASK));
    myShift = new ModifierBox("shift", KeyEvent.getKeyModifiersText(KeyEvent.SHIFT_MASK));
    myMeta = new ModifierBox("meta", KeyEvent.getKeyModifiersText(KeyEvent.META_MASK));

    modifiers.add(new JLabel("Modifiers:"));
    modifiers.add(myCtrl);
    modifiers.add(myAlt);
    modifiers.add(myShift);
    if (SystemInfo.isMac) {
      modifiers.add(myMeta);
    }

    JPanel hold = new JPanel(new FlowLayout(FlowLayout.CENTER));
    hold.add(new JLabel("Hold time:"));
    myHoldTime = new JFormattedTextField(NumberFormat.getIntegerInstance());
    myHoldTime.setColumns(5);
    myHoldTime.setHorizontalAlignment(JTextField.RIGHT);
    myHoldTime.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String txt = myHoldTime.getText();
        if (txt != null) {
          try {
            Integer value = Integer.valueOf(txt);
            myDelay = value.intValue();
          }
          catch (NumberFormatException e1) {
          }
        }
      }
    });
    hold.add(myHoldTime);
    hold.add(new JLabel("ms"));

    kbConfig.add(modifiers);
    kbConfig.add(hold);

    kbConfig.setBorder(IdeBorderFactory.createTitledBorder("Keyboard Configuration", true));

    box.add(kbConfig);

    myConflicts = new JPanel();

    box.add(myConflicts);

    updateConflicts();
  }

  private Set<String> getModifierTexts() {
    HashSet<String> result = new HashSet<>();

    for (Integer each : myQuickAccessSettings.getModiferCodes()) {
      if (each == KeyEvent.VK_SHIFT) {
        result.add("shift");
      }
      else if (each == KeyEvent.VK_CONTROL) {
        result.add("control");
      }
      else if (each == KeyEvent.VK_ALT) {
        result.add("alt");
      }
      else if (each == KeyEvent.VK_META) {
        result.add("meta");
      }
    }

    return result;
  }

  private void updateConflicts() {
    myConflicts.removeAll();
    myConflicts.setBorder(null);

    if (!myQaEnabled) {
      return;
    }

    if (myModifiers.size() == 0) {
      myConflicts.setLayout(new BorderLayout());
      myConflicts.add(getSmallLabel("Without assigning modifier keys Quick Access will not function"), BorderLayout.NORTH);
      return;
    }

    myConflicts.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(0, 4, 0, 4);

    boolean hasConflicts = printConflict(c, KeyEvent.VK_UP, QuickAccessSettings.SWITCH_UP);
    hasConflicts |= printConflict(c, KeyEvent.VK_DOWN, QuickAccessSettings.SWITCH_DOWN);
    hasConflicts |= printConflict(c, KeyEvent.VK_LEFT, QuickAccessSettings.SWITCH_LEFT);
    hasConflicts |= printConflict(c, KeyEvent.VK_RIGHT, QuickAccessSettings.SWITCH_RIGHT);
    hasConflicts |= printConflict(c, KeyEvent.VK_ENTER, QuickAccessSettings.SWITCH_APPLY);

    if (hasConflicts) {
      myConflicts.setBorder(IdeBorderFactory.createTitledBorder("Conflicts", true));
      c.gridx = 0;
      c.gridy++;
      c.gridwidth = 2;
      myConflicts.add(new SeparatorWithText(), c);

      c.gridx = 0;
      c.gridy++;
      myConflicts.add(getSmallLabel("These conflicting actions may be not what you use a lot"), c);
    }
  }

  private static JLabel getSmallLabel(final String text) {
    JLabel message = new JLabel(text, null, JLabel.CENTER);
    message.setFont(message.getFont().deriveFont(message.getFont().getStyle(), message.getFont().getSize() - 2));
    return message;
  }

  private boolean printConflict(GridBagConstraints c, int actionKey, String actionId) {
    boolean hasConflicts = false;

    int mask = myQuickAccessSettings.getModifierMask(myModifiers);

    KeyboardShortcut sc = new KeyboardShortcut(KeyStroke.getKeyStroke(actionKey, mask), null);

    Map<String,ArrayList<KeyboardShortcut>> conflictMap = myQuickAccessSettings.getKeymap().getConflicts(actionId, sc);
    if (conflictMap.size() > 0) {
      hasConflicts = true;

      JLabel scText = new JLabel(sc.toString());
      c.gridy++;
      c.gridx = 0;
      myConflicts.add(scText, c);

      Iterator<String> actions = conflictMap.keySet().iterator();
      while (actions.hasNext()) {
        String each = actions.next();
        AnAction eachAnAction = ActionManager.getInstance().getAction(each);
        if (eachAnAction != null) {
          String text = eachAnAction.getTemplatePresentation().getText();
          JLabel eachAction = new JLabel(text != null && text.length() > 0 ? text : each);
          c.gridx = 1;
          myConflicts.add(eachAction, c);
          c.gridy++;
        }
      }
    }

    c.gridx = 0;
    c.gridwidth = 2;
    c.gridy++;

    myConflicts.add(new SeparatorWithText(), c);
    c.gridwidth = 1;

    return hasConflicts;

  }

  @Nls
  public String getDisplayName() {
    return "Quick Access";
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return this;
  }

  @NotNull
  public String getId() {
    return "QuickAccess";
  }

  public boolean isModified() {
    return !myModifiers.equals(getModifierTexts())
           || myQuickAccessSettings.isEnabled() != myEnabled.isSelected()
           || getHoldTime() != myDelay;
  }

  public void apply() throws ConfigurationException {
    Registry.get("actionSystem.quickAccessEnabled").setValue(myEnabled.isSelected());
    myQuickAccessSettings.saveModifiersToRegistry(myModifiers);
    Registry.get("actionSystem.keyGestureHoldTime").setValue(myDelay);
  }

  public void reset() {
    int delay = getHoldTime();
    myQaEnabled = myQuickAccessSettings.isEnabled();
    myModifiers.clear();
    myModifiers.addAll(getModifierTexts());
    myDelay = delay;

    myEnabled.setSelected(myQaEnabled);
    myCtrl.readMask();
    myAlt.readMask();
    myShift.readMask();
    myMeta.readMask();

    myHoldTime.setText(String.valueOf(delay));

    processEnabled();

    updateConflicts();
  }

  private static int getHoldTime() {
    return Registry.intValue("actionSystem.keyGestureHoldTime");
  }

  public void disposeUIResources() {
  }

  private void processEnabled() {
    for (Component component : UIUtil.uiTraverser(this)) {
      if (component != myEnabled) {
        component.setEnabled(myQaEnabled);
      }
    }
  }

  private class ModifierBox extends JCheckBox {

    private String myModifierText;

    private ModifierBox(String modifierText, String text) {
      setText(text);
      myModifierText = modifierText;
      addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          applyMask();
          updateConflicts();
        }
      });
    }

    private void applyMask() {
      if (isSelected()) {
        myModifiers.add(myModifierText);
      }
      else {
        myModifiers.remove(myModifierText);
      }
    }

    public boolean readMask() {
      boolean selected = myModifiers.contains(myModifierText);
      setSelected(selected);
      return selected;
    }
  }
}
