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
package com.intellij.lang.ant.config.explorer;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.ExecuteCompositeTargetEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

  protected String getDimensionServiceKey() {
    return "#com.intellij.ant.explorer.SaveMetaTargetDialog";
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

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

  public JComponent getPreferredFocusedComponent() {
    return myTfName;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JLabel nameLabel = new JLabel(AntBundle.message("save.meta.data.name.label"));
    panel.add(nameLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    myTfName = new JTextField(myInitialEvent.getPresentableName());
    nameLabel.setLabelFor(myTfName);
    myTfName.selectAll();
    panel.add(myTfName, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                               GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0));

    final DefaultListModel dataModel = new DefaultListModel();
    myTargetList = new JBList(dataModel);
    final String[] targetNames = myInitialEvent.getTargetNames();
    for (String name : targetNames) {
      dataModel.addElement(name);
    }
    panel.add(new JLabel(AntBundle.message("save.meta.data.targets.label")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1,
                                                                                                    0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                                                                    GridBagConstraints.NONE,
                                                                                                    new Insets(6, 0, 0, 0), 0, 0));
    panel.add(ScrollPaneFactory.createScrollPane(myTargetList), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 2, 1.0, 1.0,
                                                                    GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                                    new Insets(4, 0, 0, 0), 0, 0));

    final JButton upButton = new JButton(AntBundle.message("button.move.up"));
    panel.add(upButton, new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                               new Insets(6, 6, 0, 0), 0, 0));
    final JButton downButton = new JButton(AntBundle.message("button.move.down"));
    panel.add(downButton, new GridBagConstraints(1, 4, 1, GridBagConstraints.REMAINDER, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                 GridBagConstraints.HORIZONTAL, new Insets(0, 6, 0, 0), 0, 0));

    class UpdateAction implements ActionListener {
      public void actionPerformed(ActionEvent e) {
        upButton.setEnabled(ListUtil.canMoveSelectedItemsUp(myTargetList));
        downButton.setEnabled(ListUtil.canMoveSelectedItemsDown(myTargetList));
      }
    }

    upButton.addActionListener(new UpdateAction() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsUp(myTargetList);
        super.actionPerformed(e);
      }
    });

    downButton.addActionListener(new UpdateAction() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.moveSelectedItemsDown(myTargetList);
        super.actionPerformed(e);
      }
    });

    myTargetList.addListSelectionListener(new ListSelectionListener() {
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
    final String[] names = ArrayUtil.newStringArray(size);
    for (int idx = 0; idx < size; idx++) {
      names[idx] = (String)model.getElementAt(idx);
    }
    final ExecuteCompositeTargetEvent event = new ExecuteCompositeTargetEvent(names);
    event.setPresentableName(myTfName.getText().trim());
    return event;
  }
}
