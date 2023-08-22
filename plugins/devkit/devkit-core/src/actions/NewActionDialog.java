// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;
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
import java.util.Collections;
import java.util.List;

public class NewActionDialog extends DialogWrapper implements ActionData {
  private JPanel myRootPanel;
  private JList<ActionGroup> myGroupList;
  private JList<AnAction> myActionList;
  private JTextField myActionClassNameEdit;
  private JTextField myActionIdEdit;
  private JTextField myActionNameEdit;
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
  private final ShortcutTextField myFirstKeystrokeEdit;
  private final ShortcutTextField mySecondKeystrokeEdit;
  private final Project myProject;
  private final PsiDirectory myDirectory;
  private ButtonGroup myAnchorButtonGroup;


  public NewActionDialog(@NotNull PsiClass actionClass) {
    this(actionClass.getProject(), null);

    myActionClassNameEdit.setText(actionClass.getQualifiedName());
    myActionClassNameEdit.setEditable(false);
    myActionIdEdit.setText(actionClass.getQualifiedName());
    if (ActionType.GROUP.isOfType(actionClass)) {
      myShortcutPanel.setVisible(false);
    }
  }

  NewActionDialog(Project project, @Nullable PsiDirectory directory) {
    super(project, false);
    myProject = project;
    myDirectory = directory;
    init();
    setTitle(DevKitBundle.message("new.action.dialog.title"));
    ActionManager actionManager = ActionManager.getInstance();

    List<String> actionIds = actionManager.getActionIdList("");
    actionIds.sort(null);
    List<ActionGroup> actionGroups = new ArrayList<>();
    for (String actionId : actionIds) {
      if (actionManager.isGroup(actionId)) {
        AnAction anAction = actionManager.getAction(actionId);
        if (anAction instanceof DefaultActionGroup) {
          boolean hasDefinedId = !IdeaPluginRegistrationIndex.processGroup(project, actionId, GlobalSearchScope.allScope(project),
                                                                           group -> false);
          if (hasDefinedId) {
            actionGroups.add((ActionGroup)anAction);
          }
        }
      }
    }
    myGroupList.setListData(actionGroups.toArray(new ActionGroup[0]));
    myGroupList.setCellRenderer(new MyActionRenderer());
    myGroupList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        ActionGroup group = myGroupList.getSelectedValue();
        if (group == null) {
          myActionList.setListData(AnAction.EMPTY_ARRAY);
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
          myActionList.setListData(realActions.toArray(AnAction.EMPTY_ARRAY));
        }
      }
    });
    ListSpeedSearch.installOn(myGroupList, o -> ActionManager.getInstance().getId(o));

    myActionList.setCellRenderer(new MyActionRenderer());
    myActionList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateControls();
      }
    });

    MyDocumentListener listener = new MyDocumentListener();
    myActionIdEdit.getDocument().addDocumentListener(listener);
    myActionClassNameEdit.getDocument().addDocumentListener(listener);
    myActionNameEdit.getDocument().addDocumentListener(listener);

    myAnchorButtonGroup.setSelected(myAnchorFirstRadio.getModel(), true);

    myFirstKeystrokeEdit = new ShortcutTextField();
    myFirstKeystrokeEditPlaceholder.setLayout(new BorderLayout());
    myFirstKeystrokeEditPlaceholder.add(myFirstKeystrokeEdit, BorderLayout.CENTER);
    myClearFirstKeystroke.addActionListener(new ActionListener() {
      @Override
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
      @Override
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


  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myActionIdEdit;
  }

  @Override
  @NotNull
  public String getActionId() {
    return myActionIdEdit.getText();
  }

  @Override
  @NotNull
  public String getActionText() {
    @NlsSafe String text = myActionNameEdit.getText();
    return text;
  }

  @Override
  public String getActionDescription() {
    @NlsSafe String description = myActionDescriptionEdit.getText();
    return description;
  }

  @Override
  @Nullable
  public String getSelectedGroupId() {
    ActionGroup group = myGroupList.getSelectedValue();
    return group == null ? null : ActionManager.getInstance().getId(group);
  }

  @Override
  @Nullable
  public String getSelectedActionId() {
    AnAction action = myActionList.getSelectedValue();
    return action == null ? null : ActionManager.getInstance().getId(action);
  }

  @Override
  @NonNls
  public String getSelectedAnchor() {
    ButtonModel selection = myAnchorButtonGroup.getSelection();
    if (selection == myAnchorFirstRadio.getModel()) return "first";
    if (selection == myAnchorLastRadio.getModel()) return "last";
    if (selection == myAnchorBeforeRadio.getModel()) return "before";
    if (selection == myAnchorAfterRadio.getModel()) return "after";
    return null;
  }

  @Override
  protected String getHelpId() {
    return "reference.new.action.dialog";
  }

  @Override
  public String getFirstKeyStroke() {
    return getKeystrokeText(myFirstKeystrokeEdit.getKeyStroke());
  }

  @Override
  public String getSecondKeyStroke() {
    return getKeystrokeText(mySecondKeystrokeEdit.getKeyStroke());
  }

  private static @NonNls String getKeystrokeText(KeyStroke keyStroke) {
    return keyStroke != null ?
           keyStroke.toString().replaceAll("pressed ", "").replaceAll("released ", "") :
           null;
  }

  String getActionName() {
    return myActionClassNameEdit.getText();
  }


  private void updateControls() {
    myAnchorBeforeRadio.setEnabled(myActionList.getSelectedValue() != null);
    myAnchorAfterRadio.setEnabled(myActionList.getSelectedValue() != null);

    boolean enabled = myFirstKeystrokeEdit.getDocument().getLength() > 0;
    myClearFirstKeystroke.setEnabled(enabled);
    mySecondKeystrokeEdit.setEnabled(enabled);
    myClearSecondKeystroke.setEnabled(enabled);

    enabled = enabled && mySecondKeystrokeEdit.getDocument().getLength() > 0;
    myClearSecondKeystroke.setEnabled(enabled);
  }

  private boolean isActionIdValid() {
    return myActionIdEdit.getText().length() > 0;
  }

  private boolean isActionNameValid() {
    return myActionNameEdit.getText().length() > 0;
  }

  private boolean isActionClassNameValid() {
    return myActionClassNameEdit.getText().length() > 0 &&
           (!myActionClassNameEdit.isEditable() || PsiNameHelper.getInstance(myProject).isQualifiedName(myActionClassNameEdit.getText()));
  }

  @Nullable
  private @NlsSafe String checkCanCreateActionClass() {
    if (myDirectory != null) {
      try {
        DevkitActionsUtil.checkCanCreateClass(myDirectory, myActionClassNameEdit.getText());
      } catch (IncorrectOperationException e) {
        String exceptionMessage = ExceptionUtil.getMessage(e);
        return exceptionMessage != null ? exceptionMessage : DevKitBundle.message("new.action.cannot.create.class");
      }
    }
    return null;
  }

  @NotNull
  @Override
  protected List<ValidationInfo> doValidateAll() {
    boolean actionIdValid = isActionIdValid();
    boolean actionNameValid = isActionNameValid();
    boolean actionClassNameValid = isActionClassNameValid();
    String createClassErrorMessage = checkCanCreateActionClass();
    if (actionIdValid && actionNameValid && actionClassNameValid && createClassErrorMessage == null) {
      return Collections.emptyList();
    }

    List<ValidationInfo> result = new ArrayList<>();
    if (!actionIdValid) {
      result.add(new ValidationInfo(DevKitBundle.message("new.action.invalid.id"), myActionIdEdit));
    }
    if (!actionClassNameValid) {
      result.add(new ValidationInfo(DevKitBundle.message("new.action.invalid.class.name"), myActionClassNameEdit));
    }
    if (!actionNameValid) {
      result.add(new ValidationInfo(DevKitBundle.message("new.action.invalid.name"), myActionNameEdit));
    }
    if (createClassErrorMessage != null) {
      result.add(new ValidationInfo(createClassErrorMessage, myActionClassNameEdit));
    }
    return result;
  }

  private class MyDocumentListener implements DocumentListener {
    @Override
    public void insertUpdate(DocumentEvent e) {
      updateControls();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      updateControls();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      updateControls();
    }
  }

  private static class MyActionRenderer extends ColoredListCellRenderer<AnAction> {
    @Override
    protected void customizeCellRenderer(@NotNull JList list, AnAction value, int index, boolean selected, boolean hasFocus) {
      @NlsSafe String actionId = ActionManager.getInstance().getId(value);
      append(actionId, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      String text = value.getTemplatePresentation().getText();
      if (StringUtil.isNotEmpty(text)) {
        append(" (" + text + ")", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private static class ShortcutTextField extends JTextField {
    private KeyStroke myKeyStroke;

    ShortcutTextField() {
      enableEvents(AWTEvent.KEY_EVENT_MASK);
      setFocusTraversalKeysEnabled(false);
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        int keyCode = e.getKeyCode();

        if (keyCode != KeyEvent.VK_SHIFT &&
            keyCode != KeyEvent.VK_ALT &&
            keyCode != KeyEvent.VK_CONTROL &&
            keyCode != KeyEvent.VK_ALT_GRAPH &&
            keyCode != KeyEvent.VK_META)
        {
          setKeyStroke(KeyStroke.getKeyStroke(keyCode, e.getModifiers()));
        }
      }
      // Ensure TAB/Shift-TAB work as focus traversal keys, otherwise
      // there is no proper way to move the focus outside the text field.
      if (ScreenReader.isActive()) {
        setFocusTraversalKeysEnabled(true);
        try {
          KeyboardFocusManager.getCurrentKeyboardFocusManager().processKeyEvent(this, e);
        }
        finally {
          setFocusTraversalKeysEnabled(false);
        }
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
}


