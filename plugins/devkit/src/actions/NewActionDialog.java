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
package org.jetbrains.idea.devkit.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameHelper;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.ActionData;
import org.jetbrains.idea.devkit.util.ActionType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class NewActionDialog extends DialogWrapper implements ActionData {
  private JPanel myRootPanel;
  private JList myGroupList;
  private JList myActionList;
  private JTextField myActionNameEdit;
  private JTextField myActionIdEdit;
  private JTextField myActionTextEdit;
  private JTextField myActionDescriptionEdit;
  private JRadioButton myAnchorFirstRadio;
  private JRadioButton myAnchorLastRadio;
  private JRadioButton myAnchorBeforeRadio;
  private JRadioButton myAnchorAfterRadio;
  private JPanel myShortcutPanel;
  private JPanel myFirstKeystrokeEditPlaceholder;
  private JPanel mySecondKeystrokeEditPlaceholder;
  private JButton myClearFirstKeystroke;
  private JButton myClearSecondKeystroke;
  private ShortcutTextField myFirstKeystrokeEdit;
  private ShortcutTextField mySecondKeystrokeEdit;
  private TextFieldWithBrowseButton myIconEdit;
  private Project myProject;
  private ButtonGroup myAnchorButtonGroup;

  public NewActionDialog(@NotNull PsiClass actionClass) {
    this(actionClass.getProject());

    myActionNameEdit.setText(actionClass.getQualifiedName());
    myActionNameEdit.setEditable(false);
    myActionIdEdit.setText(actionClass.getQualifiedName());
    if (ActionType.GROUP.isOfType(actionClass)) {
      myShortcutPanel.setVisible(false);
    }
  }

  protected NewActionDialog(Project project) {
    super(project, false);
    myProject = project;
    init();
    setTitle(DevKitBundle.message("new.action.dialog.title"));
    final ActionManager actionManager = ActionManager.getInstance();
    String[] actionIds = actionManager.getActionIds("");
    Arrays.sort(actionIds);
    List<ActionGroup> actionGroups = new ArrayList<>();
    for(String actionId: actionIds) {
      if (actionManager.isGroup(actionId)) {
        AnAction anAction = actionManager.getAction(actionId);
        if (anAction instanceof DefaultActionGroup) {
          actionGroups.add((ActionGroup) anAction);
        }
      }
    }
    myGroupList.setListData(actionGroups.toArray());
    myGroupList.setCellRenderer(new MyActionRenderer());
    myGroupList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        ActionGroup group = (ActionGroup) myGroupList.getSelectedValue();
        if (group == null) {
          myActionList.setListData(ArrayUtil.EMPTY_OBJECT_ARRAY);
        }
        else {
          AnAction[] actions = group.getChildren(null);
          // filter out actions that don't have IDs - they can't be used for anchoring in plugin.xml
          List<AnAction> realActions = new ArrayList<>();
          for(AnAction action: actions) {
            if (actionManager.getId(action) != null) {
              realActions.add(action);
            }
          }
          myActionList.setListData(realActions.toArray());
        }
      }
    });
    new ListSpeedSearch(myGroupList, new Function<Object, String>() {
      public String fun(Object o) {
        return ActionManager.getInstance().getId((AnAction) o);
      }
    });

    myActionList.setCellRenderer(new MyActionRenderer());
    myActionList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateControls();
      }
    });

    MyDocumentListener listener = new MyDocumentListener();
    myActionIdEdit.getDocument().addDocumentListener(listener);
    myActionNameEdit.getDocument().addDocumentListener(listener);
    myActionTextEdit.getDocument().addDocumentListener(listener);

    myAnchorButtonGroup.setSelected(myAnchorFirstRadio.getModel(), true);

    myFirstKeystrokeEdit = new ShortcutTextField();
    myFirstKeystrokeEditPlaceholder.setLayout(new BorderLayout());
    myFirstKeystrokeEditPlaceholder.add(myFirstKeystrokeEdit, BorderLayout.CENTER);
    myClearFirstKeystroke.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myFirstKeystrokeEdit.setKeyStroke(null);
      }
    });
    myFirstKeystrokeEdit.getDocument().addDocumentListener(listener);
    myClearFirstKeystroke.setText(null);

    Icon icon = AllIcons.Actions.Cancel;
    Dimension size = new Dimension(icon.getIconWidth(), icon.getIconHeight());
    myClearFirstKeystroke.setIcon(icon);
    myClearFirstKeystroke.setPreferredSize(size);
    myClearFirstKeystroke.setMaximumSize(size);

    mySecondKeystrokeEdit = new ShortcutTextField();
    mySecondKeystrokeEditPlaceholder.setLayout(new BorderLayout());
    mySecondKeystrokeEditPlaceholder.add(mySecondKeystrokeEdit, BorderLayout.CENTER);
    myClearSecondKeystroke.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySecondKeystrokeEdit.setKeyStroke(null);
      }
    });
    mySecondKeystrokeEdit.getDocument().addDocumentListener(listener);
    myClearSecondKeystroke.setText(null);
    myClearSecondKeystroke.setIcon(icon);
    myClearSecondKeystroke.setPreferredSize(size);
    myClearSecondKeystroke.setMaximumSize(size);

    updateControls();
  }

  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override public JComponent getPreferredFocusedComponent() {
    return myActionIdEdit;
  }

  @NotNull
  public String getActionId() {
    return myActionIdEdit.getText();
  }

  public String getActionName() {
    return myActionNameEdit.getText();
  }

  @NotNull
  public String getActionText() {
    return myActionTextEdit.getText();
  }

  public String getActionDescription() {
    return myActionDescriptionEdit.getText();
  }

  @Nullable
  public String getSelectedGroupId() {
    ActionGroup group = (ActionGroup) myGroupList.getSelectedValue();
    return group == null ? null : ActionManager.getInstance().getId(group);
  }

  @Nullable
  public String getSelectedActionId() {
    AnAction action = (AnAction) myActionList.getSelectedValue();
    return action == null ? null : ActionManager.getInstance().getId(action);
  }

  @NonNls
  public String getSelectedAnchor() {
    ButtonModel selection = myAnchorButtonGroup.getSelection();
    if (selection == myAnchorFirstRadio.getModel()) return "first";
    if (selection == myAnchorLastRadio.getModel()) return "last";
    if (selection == myAnchorBeforeRadio.getModel()) return "before";
    if (selection == myAnchorAfterRadio.getModel()) return "after";
    return null;
  }

  public String getFirstKeyStroke() {
    return getKeystrokeText(myFirstKeystrokeEdit.getKeyStroke());
  }

  public String getSecondKeyStroke() {
    return getKeystrokeText(mySecondKeystrokeEdit.getKeyStroke());
  }

  private static String getKeystrokeText(KeyStroke keyStroke) {
    //noinspection HardCodedStringLiteral
    return keyStroke != null ?
            keyStroke.toString().replaceAll("pressed ", "").replaceAll("released ", "") :
            null;
  }

  private void updateControls() {
    setOKActionEnabled(myActionIdEdit.getText().length() > 0 &&
                       myActionNameEdit.getText().length() > 0 &&
                       myActionTextEdit.getText().length() > 0 &&
                       (!myActionNameEdit.isEditable() || PsiNameHelper.getInstance(myProject).isIdentifier(myActionNameEdit.getText())));

    myAnchorBeforeRadio.setEnabled(myActionList.getSelectedValue() != null);
    myAnchorAfterRadio.setEnabled(myActionList.getSelectedValue() != null);

    boolean enabled = myFirstKeystrokeEdit.getDocument().getLength() > 0;
    myClearFirstKeystroke.setEnabled(enabled);
    mySecondKeystrokeEdit.setEnabled(enabled);
    myClearSecondKeystroke.setEnabled(enabled);

    enabled = enabled && mySecondKeystrokeEdit.getDocument().getLength() > 0;
    myClearSecondKeystroke.setEnabled(enabled);
  }

  private static class MyActionRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      AnAction group = (AnAction) value;
      append(ActionManager.getInstance().getId(group), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      String text = group.getTemplatePresentation().getText();
      if (text != null) {
        append(" (" + text + ")", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private class MyDocumentListener implements DocumentListener {
    public void insertUpdate(DocumentEvent e) {
      updateControls();
    }

    public void removeUpdate(DocumentEvent e) {
      updateControls();
    }

    public void changedUpdate(DocumentEvent e) {
      updateControls();
    }
  }

  private static class ShortcutTextField extends JTextField {
    private KeyStroke myKeyStroke;

    public ShortcutTextField() {
      enableEvents(AWTEvent.KEY_EVENT_MASK);
      setFocusTraversalKeysEnabled(false);
    }

    protected void processKeyEvent(KeyEvent e) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        int keyCode = e.getKeyCode();
        if (
          keyCode == KeyEvent.VK_SHIFT ||
          keyCode == KeyEvent.VK_ALT ||
          keyCode == KeyEvent.VK_CONTROL ||
          keyCode == KeyEvent.VK_ALT_GRAPH ||
          keyCode == KeyEvent.VK_META
        ){
          return;
        }

        setKeyStroke(KeyStroke.getKeyStroke(keyCode, e.getModifiers()));
      }
    }

    public void setKeyStroke(KeyStroke keyStroke) {
      myKeyStroke = keyStroke;
      if (keyStroke == null) {
        setText("");
      }
      else {
        setText(KeymapUtil.getKeystrokeText(keyStroke));
      }
    }

    public KeyStroke getKeyStroke() {
      return myKeyStroke;
    }
  }

  @Override
  protected String getHelpId() {
    return "reference.new.action.dialog";
  }
}


