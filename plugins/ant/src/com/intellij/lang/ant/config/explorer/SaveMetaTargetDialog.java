// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.explorer;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.ExecuteCompositeTargetEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class SaveMetaTargetDialog extends DialogWrapper {
  private JList myTargetList;
  private JTextField myTfName;
  private final ExecuteCompositeTargetEvent myInitialEvent;
  private final AntConfigurationBase myAntConfiguration;
  private final AntBuildFile myBuildFile;

  public SaveMetaTargetDialog(final Component parent,
                              final ExecuteCompositeTargetEvent event,
                              final AntConfigurationBase antConfiguration,
                              final AntBuildFile buildFile) {
    super(parent, true);
    myInitialEvent = event;
    myAntConfiguration = antConfiguration;
    myBuildFile = buildFile;
    setModal(true);
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ant.explorer.SaveMetaTargetDialog";
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected void doOKAction() {
    final ExecuteCompositeTargetEvent eventObject = createEventObject();
    if (myAntConfiguration.getTargetForEvent(eventObject) == null) {
      myAntConfiguration.setTargetForEvent(myBuildFile, eventObject.getMetaTargetName(), eventObject);
      super.doOKAction();
    }
    else {
      Messages.showInfoMessage(getContentPane(), AntBundle.message("save.meta.data.such.sequence.of.targets.already.exists.error.message"),
                               getTitle());
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTfName;
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JLabel nameLabel = new JLabel(AntBundle.message("save.meta.data.name.label"));
    panel.add(nameLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
    myTfName = new JTextField(myInitialEvent.getPresentableName());
    nameLabel.setLabelFor(myTfName);
    myTfName.selectAll();
    panel.add(myTfName, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                               GridBagConstraints.HORIZONTAL, JBUI.insetsTop(4), 0, 0));

    final DefaultListModel dataModel = new DefaultListModel();
    myTargetList = new JBList(dataModel);
    final List<String> targetNames = myInitialEvent.getTargetNames();
    for (@NlsSafe String name : targetNames) {
      dataModel.addElement(name);
    }
    panel.add(new JLabel(AntBundle.message("save.meta.data.targets.label")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1,
                                                                                                    0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                                                                    GridBagConstraints.NONE,
                                                                                                    JBUI.insetsTop(6), 0, 0));
    panel.add(ScrollPaneFactory.createScrollPane(myTargetList), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 2, 1.0, 1.0,
                                                                                       GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                                                       JBUI.insetsTop(4), 0, 0));

    final JButton upButton = new JButton(AntBundle.message("button.move.up"));
    panel.add(upButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                               JBUI.insets(6, 6, 0, 0), 0, 0));
    final JButton downButton = new JButton(AntBundle.message("button.move.down"));
    panel.add(downButton, new GridBagConstraints(1, 4, 1, GridBagConstraints.REMAINDER, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                 GridBagConstraints.HORIZONTAL, JBUI.insetsLeft(6), 0, 0));

    class UpdateAction implements ActionListener {
      @Override
      public void actionPerformed(ActionEvent e) {
        upButton.setEnabled(ListUtil.canMoveSelectedItemsUp(myTargetList));
        downButton.setEnabled(ListUtil.canMoveSelectedItemsDown(myTargetList));
      }
    }

    upButton.addActionListener(new UpdateAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsUp(myTargetList);
        super.actionPerformed(e);
      }
    });

    downButton.addActionListener(new UpdateAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsDown(myTargetList);
        super.actionPerformed(e);
      }
    });

    myTargetList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        upButton.setEnabled(ListUtil.canMoveSelectedItemsUp(myTargetList));
        downButton.setEnabled(ListUtil.canMoveSelectedItemsDown(myTargetList));
      }
    });
    myTargetList.setSelectedIndex(0);
    return panel;
  }

  private ExecuteCompositeTargetEvent createEventObject() {
    final ListModel model = myTargetList.getModel();
    final int size = model.getSize();
    final List<@NlsSafe String> names  = new ArrayList<>();
    for (int idx = 0; idx < size; idx++) {
      names.add((String)model.getElementAt(idx));
    }
    final ExecuteCompositeTargetEvent event = new ExecuteCompositeTargetEvent(names);
    event.setPresentableName(myTfName.getText().trim());
    return event;
  }
}
