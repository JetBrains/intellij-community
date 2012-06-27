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
package com.intellij.android.designer.propertyTable.editors;

import com.android.resources.ResourceType;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ExtractStyleDialog extends DialogWrapper {
  private final Module myModule;
  private final String myFileName;
  private final List<String> myDirNames;

  private final JPanel myPanel;
  private final JTextField myNameText;
  private final CheckboxTree myTree;

  private final CheckedTreeNode myRootNode;

  public ExtractStyleDialog(Module module, String fileName, List<String> dirNames, XmlTag tag) {
    super(module.getProject());
    myModule = module;
    myFileName = fileName;
    myDirNames = dirNames;

    myPanel = new JPanel(new GridLayoutManager(3, 2));

    JLabel nameLabel = new JLabel("Style name:");
    nameLabel.setDisplayedMnemonic('n');
    myPanel.add(nameLabel,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null, null));

    myNameText = new JTextField();
    myPanel.add(myNameText, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_GROW, 0, null, null, null));

    nameLabel.setLabelFor(myNameText);

    myNameText.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        checkFinish();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        checkFinish();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        checkFinish();
      }
    });

    JLabel attributesLabel = new JLabel("Attributes:");
    attributesLabel.setDisplayedMnemonic('A');
    myPanel.add(attributesLabel,
                new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null, null));

    myRootNode = new CheckedTreeNode(null);
    for (XmlAttribute attribute : tag.getAttributes()) {
      String name = attribute.getName();
      if (!"style".equalsIgnoreCase(name)) {
        CheckedTreeNode treeNode = new CheckedTreeNode(attribute);
        treeNode.setChecked(!"android:layout_width".equalsIgnoreCase(name) && !"android:layout_height".equalsIgnoreCase(name));
        myRootNode.add(treeNode);
      }
    }

    CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof CheckedTreeNode) {
          XmlAttribute attribute = (XmlAttribute)((CheckedTreeNode)value).getUserObject();
          if (attribute != null) {
            getTextRenderer().append(attribute.getLocalName());
            getTextRenderer().append(" [" + attribute.getValue() + "]", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
          }
        }
      }
    };
    myTree = new CheckboxTree(renderer, myRootNode) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        checkFinish();
      }

      protected void installSpeedSearch() {
        new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
          public String convert(TreePath path) {
            Object object = path.getLastPathComponent();
            if (object instanceof CheckedTreeNode) {
              XmlAttribute attribute = (XmlAttribute)((CheckedTreeNode)object).getUserObject();
              if (attribute != null) {
                return attribute.getLocalName();
              }
            }
            return "";
          }
        });
      }
    };
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    TreeUtil.expandAll(myTree);

    attributesLabel.setLabelFor(myTree);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree);
    decorator.setToolbarPosition(ActionToolbarPosition.RIGHT);
    decorator.setEditAction(null);
    decorator.disableUpDownActions();

    AnActionButton selectAll = new AnActionButton("Select All", null, PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        setChecked(true);
      }
    };
    decorator.addExtraAction(selectAll);

    AnActionButton unselectAll = new AnActionButton("Unselect All", null, PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        setChecked(false);
      }
    };
    decorator.addExtraAction(unselectAll);


    myPanel.add(decorator.createPanel(),
                new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                    GridConstraints.SIZEPOLICY_CAN_GROW, 0, null, null, null));

    myPanel.setPreferredSize(new Dimension(400, -1));

    setTitle("Extract Style");
    checkFinish();
    init();
  }

  private void checkFinish() {
    if (AndroidResourceUtil.isCorrectAndroidResourceName(myNameText.getText().trim())) {
      int count = myRootNode.getChildCount();
      for (int i = 0; i < count; i++) {
        CheckedTreeNode treeNode = (CheckedTreeNode)myRootNode.getChildAt(i);
        if (treeNode.isChecked()) {
          setOKActionEnabled(true);
          return;
        }
      }
    }
    setOKActionEnabled(false);
  }

  private void setChecked(boolean value) {
    int count = myRootNode.getChildCount();
    for (int i = 0; i < count; i++) {
      ((CheckedTreeNode)myRootNode.getChildAt(i)).setChecked(value);
    }
    myTree.repaint();
    checkFinish();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameText;
  }

  @Override
  protected ValidationInfo doValidate() {
    return CreateXmlResourceDialog.checkIfResourceAlreadyExists(myModule, getStyleName(), ResourceType.STYLE, myDirNames, myFileName);
  }

  @NotNull
  public String getStyleName() {
    return myNameText.getText();
  }

  @NotNull
  public List<XmlAttribute> getStyledAttributes() {
    List<XmlAttribute> attributes = new ArrayList<XmlAttribute>();

    int count = myRootNode.getChildCount();
    for (int i = 0; i < count; i++) {
      CheckedTreeNode treeNode = (CheckedTreeNode)myRootNode.getChildAt(i);
      if (treeNode.isChecked()) {
        attributes.add((XmlAttribute)treeNode.getUserObject());
      }
    }

    return attributes;
  }
}