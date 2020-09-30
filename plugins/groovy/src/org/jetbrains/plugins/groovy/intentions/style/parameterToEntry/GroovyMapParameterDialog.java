// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.event.*;
import java.util.EventListener;

public class GroovyMapParameterDialog extends DialogWrapper {
  private JPanel contentPane;
  private ComboBox myNameComboBox;
  private JLabel myNameLabel;
  private JCheckBox myCbTypeSpec;
  private JCheckBox myCreateNew;
  private final Project myProject;
  private final EventListenerList myListenerList = new EventListenerList();

  public GroovyMapParameterDialog(Project project, final @NlsSafe String[] possibleNames, final boolean createNew) {
    super(true);
    myProject = project;
    setUpDialog(possibleNames, createNew);
    init();
  }

  private void setUpDialog(final @NlsSafe String[] possibleNames, boolean createNew) {
    setTitle(GroovyBundle.message("convert.param.to.map.entry"));

    if (GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_PARAM != null) {
      myCreateNew.setSelected(createNew = GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_PARAM.booleanValue());
    } else {
      myCreateNew.setSelected(createNew);
    }

    myNameLabel.setLabelFor(myNameComboBox);
    myCbTypeSpec.setMnemonic(KeyEvent.VK_T);
    myCbTypeSpec.setFocusable(false);
    setUpNameComboBox(possibleNames);
    setModal(true);
    if (GroovyApplicationSettings.getInstance().CONVERT_PARAM_SPECIFY_MAP_TYPE != null) {
      myCbTypeSpec.setSelected(GroovyApplicationSettings.getInstance().CONVERT_PARAM_SPECIFY_MAP_TYPE.booleanValue());
    } else {
      myCbTypeSpec.setSelected(true);
    }

    myCbTypeSpec.setEnabled(createNew);
    myNameComboBox.setEnabled(createNew);
    myNameLabel.setEnabled(createNew);

    myCreateNew.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(final ChangeEvent e) {
        final boolean flag = myCreateNew.isSelected();
        myCbTypeSpec.setEnabled(flag);
        myNameComboBox.setEnabled(flag);
        myNameLabel.setEnabled(flag);
      }
    });
  }

  public boolean createNewFirst() {
    return myCreateNew.isSelected();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  public boolean specifyTypeExplicitly() {
    return myCbTypeSpec.isSelected();
  }

  @Override
  protected void doOKAction() {
    if (myCbTypeSpec.isEnabled()) {
      GroovyApplicationSettings.getInstance().CONVERT_PARAM_SPECIFY_MAP_TYPE = myCbTypeSpec.isSelected();
    }
    GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_PARAM = myCreateNew.isSelected();
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  public JComponent getContentPane() {
    return contentPane;
  }

  @Nullable
  protected String getEnteredName() {
    if (myNameComboBox.getEditor().getItem() instanceof String && !((String)myNameComboBox.getEditor().getItem()).isEmpty()) {
      return (String)myNameComboBox.getEditor().getItem();
    } else {
      return null;
    }
  }

  private void setUpNameComboBox(@NlsSafe String[] possibleNames) {

    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, GroovyFileType.GROOVY_FILE_TYPE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);
    myListenerList.add(DataChangedListener.class, new DataChangedListener());

    myNameComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        fireNameDataChanged();
      }
    });

    ((EditorTextField)myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        fireNameDataChanged();
      }
    });

    contentPane.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myNameComboBox, true));
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (var possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener)aList).dataChanged();
      }
    }
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(GroovyNamesUtil.isIdentifier(text));
  }

}
