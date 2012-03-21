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
import com.intellij.designer.componentTree.TreeNodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResourceDialog extends DialogWrapper implements TreeSelectionListener {
  private final JBTabbedPane myContentPanel;
  private final ResourcePanel myProjectPanel;
  private final ResourcePanel mySystemPanel;
  private final Action myNewResourceAction = new AbstractAction("New Resource...") {
    @Override
    public void actionPerformed(ActionEvent e) {
      // TODO: Auto-generated method stub
    }
  };
  private String myResultResourceName;

  public ResourceDialog(Module module, ResourceType[] types) {
    super(module.getProject());

    setTitle("Resource Dialog");
    getOKAction().setEnabled(false);

    AndroidFacet facet = AndroidFacet.getInstance(module);
    myProjectPanel = new ResourcePanel(facet, types, false);
    mySystemPanel = new ResourcePanel(facet, types, true);

    myContentPanel = new JBTabbedPane();
    myContentPanel.setPreferredSize(new Dimension(500, 400));
    myContentPanel.addTab("Project", myProjectPanel.myComponent);
    myContentPanel.addTab("System", mySystemPanel.myComponent);

    myContentPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myNewResourceAction.setEnabled(myContentPanel.getSelectedComponent() == myProjectPanel.myComponent);
        valueChanged(null);
      }
    });

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectPanel.myTree;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  protected Action[] createLeftSideActions() {
    return new Action[]{myNewResourceAction};
  }

  @Override
  protected void dispose() {
    super.dispose();
    Disposer.dispose(myProjectPanel.myTreeBuilder);
    Disposer.dispose(mySystemPanel.myTreeBuilder);
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    ResourcePanel panel = myContentPanel.getSelectedComponent() == myProjectPanel.myComponent ? myProjectPanel : mySystemPanel;
    Set<ResourceItem> elements = panel.myTreeBuilder.getSelectedElements(ResourceItem.class);
    getOKAction().setEnabled(!elements.isEmpty());

    if (elements.isEmpty()) {
      myResultResourceName = null;
    }
    else {
      String prefix = panel == myProjectPanel ? "@" : "@android:";
      myResultResourceName = prefix + elements.iterator().next().getName();
    }
  }

  private class ResourcePanel {
    public final Tree myTree;
    public final AbstractTreeBuilder myTreeBuilder;
    public final JScrollPane myComponent;

    public ResourcePanel(AndroidFacet facet, ResourceType[] types, boolean system) {
      myTree = new Tree();
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
      myTree.setScrollsOnExpand(true);
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
      ToolTipManager.sharedInstance().registerComponent(myTree);
      TreeUtil.installActions(myTree);

      ResourceManager manager = facet.getResourceManager(system ? AndroidUtils.SYSTEM_RESOURCE_PACKAGE : null);
      ResourceGroup[] groups = new ResourceGroup[types.length];

      for (int i = 0; i < types.length; i++) {
        groups[i] = new ResourceGroup(types[i], manager);
      }

      myTreeBuilder =
        new AbstractTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), new TreeContentProvider(groups), null);
      myTreeBuilder.initRootNode();

      TreeSelectionModel selectionModel = myTree.getSelectionModel();
      selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      selectionModel.addTreeSelectionListener(ResourceDialog.this);

      myTree.setCellRenderer(new NodeRenderer() {
        @Override
        protected void doAppend(@NotNull @Nls String fragment,
                                @NotNull SimpleTextAttributes attributes,
                                boolean isMainText,
                                boolean selected) {
          SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, attributes, selected, this);
        }

        @Override
        public void doAppend(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean selected) {
          SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, attributes, selected, this);
        }

        @Override
        public void doAppend(String fragment, boolean selected) {
          SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES, selected, this);
        }
      });
      new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);

      myComponent = ScrollPaneFactory.createScrollPane(myTree);
    }
  }

  private static class ResourceGroup {
    private List<ResourceItem> myItems = new ArrayList<ResourceItem>();
    private final ResourceType myType;

    public ResourceGroup(ResourceType type, ResourceManager manager) {
      myType = type;

      Collection<String> resourceNames = manager.getValueResourceNames(type.getName());
      for (String resourceName : resourceNames) {
        myItems.add(new ResourceItem(this, resourceName));
      }

      for (String file : manager.getFileResourcesNames(type.getName())) {
        myItems.add(new ResourceItem(this, file));
      }

      if (type == ResourceType.ID) {
        for (String id : manager.getIds()) {
          if (!resourceNames.contains(id)) {
            myItems.add(new ResourceItem(this, id));
          }
        }
      }

      Collections.sort(myItems, new Comparator<ResourceItem>() {
        @Override
        public int compare(ResourceItem resource1, ResourceItem resource2) {
          return resource1.toString().compareTo(resource2.toString());
        }
      });
    }

    public String getName() {
      return myType.getName();
    }

    public List<ResourceItem> getItems() {
      return myItems;
    }

    @Override
    public String toString() {
      return myType.getDisplayName();
    }
  }

  private static class ResourceItem {
    private final ResourceGroup myGroup;
    private final String myName;

    public ResourceItem(@NotNull ResourceGroup group, @NotNull String name) {
      myGroup = group;
      myName = name;
    }

    public ResourceGroup getGroup() {
      return myGroup;
    }

    public String getName() {
      return myGroup.getName() + "/" + myName;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  private static class TreeContentProvider extends AbstractTreeStructure {
    private final Object myTreeRoot = new Object();
    private final ResourceGroup[] myGroups;

    public TreeContentProvider(ResourceGroup[] groups) {
      myGroups = groups;
    }

    @Override
    public Object getRootElement() {
      return myTreeRoot;
    }

    @Override
    public Object[] getChildElements(Object element) {
      if (element == myTreeRoot) {
        return myGroups;
      }
      if (element instanceof ResourceGroup) {
        ResourceGroup group = (ResourceGroup)element;
        return group.getItems().toArray();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public Object getParentElement(Object element) {
      if (element instanceof ResourceItem) {
        ResourceItem resource = (ResourceItem)element;
        return resource.getGroup();
      }
      return null;
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      return new TreeNodeDescriptor(parentDescriptor, element, element == null ? null : element.toString());
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public void commit() {
    }
  }
}