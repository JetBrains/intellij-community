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
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.componentTree.TreeNodeDescriptor;
import com.intellij.designer.utils.SizedIcon;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.AndroidBaseLayoutRefactoringAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.resourceManagers.FileResourceProcessor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResourceDialog extends DialogWrapper implements TreeSelectionListener {
  private static final String ANDROID = "@android:";
  private static final String TYPE_KEY = "ResourceType";

  private static final String TEXT = "Text";
  private static final String TABS = "Tabs";
  private static final String IMAGE = "Image";
  private static final String NONE = "None";

  private static final Icon RESOURCE_ITEM_ICON = AllIcons.Css.Property;

  private final Module myModule;
  private final RadViewComponent myComponent;

  private final JBTabbedPane myContentPanel;
  private final ResourcePanel myProjectPanel;
  private final ResourcePanel mySystemPanel;
  private ColorPicker myColorPicker;

  private final Action myNewResourceAction = new AbstractAction("New Resource", AllIcons.General.ComboArrowDown) {
    @Override
    public void actionPerformed(ActionEvent e) {
      JComponent component = (JComponent)e.getSource();
      ActionPopupMenu popupMenu = createNewResourcePopupMenu();
      popupMenu.getComponent().show(component, 0, component.getHeight());
    }
  };
  private final AnAction myNewResourceValueAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceValue(type);
    }
  };
  private final AnAction myNewResourceFileAction = new AnAction() {
    @Override
    public void actionPerformed(AnActionEvent e) {
      ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
      createNewResourceFile(type);
    }
  };
  private final AnAction myExtractStyleAction = new AnAction("Extract Style...") {
    @Override
    public void actionPerformed(AnActionEvent e) {
      extractStyle();
    }
  };

  private String myResultResourceName;

  public ResourceDialog(Module module, ResourceType[] types, String value, RadViewComponent component) {
    super(module.getProject());
    myModule = module;
    myComponent = component;

    setTitle("Resources");

    AndroidFacet facet = AndroidFacet.getInstance(module);
    myProjectPanel = new ResourcePanel(facet, types, false);
    mySystemPanel = new ResourcePanel(facet, types, true);

    myContentPanel = new JBTabbedPane();
    myContentPanel.setPreferredSize(new Dimension(600, 500));
    myContentPanel.addTab("Project", myProjectPanel.myComponent);
    myContentPanel.addTab("System", mySystemPanel.myComponent);

    myProjectPanel.myTreeBuilder.expandAll(null);
    mySystemPanel.myTreeBuilder.expandAll(null);

    boolean doSelection = value != null;

    if (types == ResourceEditor.COLOR_TYPES) {
      Color color = ResourceRenderer.parseColor(value);
      myColorPicker = new ColorPicker(myDisposable, color, true);
      myContentPanel.addTab("Color", myColorPicker);
      if (color != null) {
        myContentPanel.setSelectedIndex(2);
        doSelection = false;
      }
    }
    if (doSelection && value.startsWith("@")) {
      value = StringUtil.replace(value, "+", "");
      int index = value.indexOf('/');
      if (index != -1) {
        ResourcePanel panel;
        String type;
        String name = value.substring(index + 1);
        if (value.startsWith(ANDROID)) {
          panel = mySystemPanel;
          type = value.substring(ANDROID.length(), index);
        }
        else {
          panel = myProjectPanel;
          type = value.substring(1, index);
        }
        myContentPanel.setSelectedComponent(panel.myComponent);
        panel.select(type, name);
      }
    }

    myContentPanel.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        valueChanged(null);
      }
    });

    valueChanged(null);
    init();
  }

  private ActionPopupMenu createNewResourcePopupMenu() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ResourceGroup resourceGroup = getSelectedElement(myProjectPanel.myTreeBuilder, ResourceGroup.class);
    if (resourceGroup == null) {
      resourceGroup = getSelectedElement(myProjectPanel.myTreeBuilder, ResourceItem.class).getGroup();
    }

    if (AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resourceGroup.getType())) {
      myNewResourceValueAction.getTemplatePresentation().setText("New " + resourceGroup + " Value...");
      myNewResourceValueAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceGroup.getType());
      actionGroup.add(myNewResourceValueAction);
    }
    if (AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.contains(resourceGroup.getType())) {
      myNewResourceFileAction.getTemplatePresentation().setText("New " + resourceGroup + " File...");
      myNewResourceFileAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceGroup.getType());
      actionGroup.add(myNewResourceFileAction);
    }
    if (myComponent != null && ResourceType.STYLE.equals(resourceGroup.getType())) {
      final XmlTag componentTag = myComponent.getTag();
      final boolean enabled = AndroidBaseLayoutRefactoringAction.getLayoutViewElement(componentTag) != null &&
                              AndroidExtractStyleAction.doIsEnabled(componentTag);
      myExtractStyleAction.getTemplatePresentation().setEnabled(enabled);
      actionGroup.add(myExtractStyleAction);
    }

    return actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup);
  }

  private void createNewResourceValue(ResourceType resourceType) {
    CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(myModule, resourceType, null, null, true);
    dialog.setTitle("New " + StringUtil.capitalize(resourceType.getDisplayName()) + " Value Resource");
    dialog.show();

    if (!dialog.isOK()) {
      return;
    }

    Module moduleToPlaceResource = dialog.getModule();
    if (moduleToPlaceResource == null) {
      return;
    }

    String fileName = dialog.getFileName();
    List<String> dirNames = dialog.getDirNames();
    String resValue = dialog.getValue();
    String resName = dialog.getResourceName();
    if (!AndroidResourceUtil.createValueResource(moduleToPlaceResource, resName, resourceType, fileName, dirNames, resValue)) {
      return;
    }

    PsiDocumentManager.getInstance(myModule.getProject()).commitAllDocuments();

    myResultResourceName = "@" + resourceType.getName() + "/" + resName;
    close(OK_EXIT_CODE);
  }

  private void createNewResourceFile(ResourceType resourceType) {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    XmlFile newFile = CreateResourceFileAction.createFileResource(facet, resourceType, null, null, null, true, null);

    if (newFile != null) {
      String name = newFile.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      }
      myResultResourceName = "@" + resourceType.getName() + "/" + name;
      close(OK_EXIT_CODE);
    }
  }

  private void extractStyle() {
    final String resName = AndroidExtractStyleAction.doExtractStyle(myModule, myComponent.getTag(), false, null);
    if (resName == null) {
      return;
    }
    myResultResourceName = "@style/" + resName;
    close(OK_EXIT_CODE);
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
  protected void doOKAction() {
    valueChanged(null);
    super.doOKAction();
  }

  @Nullable
  private static <T> T getSelectedElement(AbstractTreeBuilder treeBuilder, Class<T> elementClass) {
    Set<T> elements = treeBuilder.getSelectedElements(elementClass);
    return elements.isEmpty() ? null : elements.iterator().next();
  }

  @Override
  public void valueChanged(@Nullable TreeSelectionEvent e) {
    Component selectedComponent = myContentPanel.getSelectedComponent();

    if (selectedComponent == myColorPicker) {
      Color color = myColorPicker.getColor();
      setOKActionEnabled(color != null);
      myNewResourceAction.setEnabled(false);
      myResultResourceName = color == null ? null : "#" + toHex(color.getRed()) + toHex(color.getGreen()) + toHex(color.getBlue());
    }
    else {
      boolean isProjectPanel = selectedComponent == myProjectPanel.myComponent;
      ResourcePanel panel = isProjectPanel ? myProjectPanel : mySystemPanel;
      ResourceItem element = getSelectedElement(panel.myTreeBuilder, ResourceItem.class);
      setOKActionEnabled(element != null);
      myNewResourceAction.setEnabled(isProjectPanel && !panel.myTreeBuilder.getSelectedElements().isEmpty());

      if (element == null) {
        myResultResourceName = null;
      }
      else {
        String prefix = panel == myProjectPanel ? "@" : ANDROID;
        myResultResourceName = prefix + element.getName();
      }

      panel.showPreview(element);
    }
  }

  private static String toHex(int value) {
    String hex = Integer.toString(value, 16);
    return hex.length() == 1 ? "0" + hex : hex;
  }

  private class ResourcePanel {
    public final Tree myTree;
    public final AbstractTreeBuilder myTreeBuilder;
    public final JBSplitter myComponent;

    private final JPanel myPreviewPanel;
    private final JTextArea myTextArea;
    private final JBTabbedPane myTabbedPane;
    private final JLabel myImageComponent;
    private final JLabel myNoPreviewComponent;

    private final ResourceGroup[] myGroups;
    private final ResourceManager myManager;

    public ResourcePanel(AndroidFacet facet, ResourceType[] types, boolean system) {
      myTree = new Tree();
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
      myTree.setScrollsOnExpand(true);
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          if (!myTreeBuilder.getSelectedElements(ResourceItem.class).isEmpty()) {
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(myTree);

      ToolTipManager.sharedInstance().registerComponent(myTree);
      TreeUtil.installActions(myTree);

      myManager = facet.getResourceManager(system ? AndroidUtils.SYSTEM_RESOURCE_PACKAGE : null);
      myGroups = new ResourceGroup[types.length];

      for (int i = 0; i < types.length; i++) {
        myGroups[i] = new ResourceGroup(types[i], myManager);
      }

      myTreeBuilder =
        new AbstractTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), new TreeContentProvider(myGroups), null);
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

      myComponent = new JBSplitter(true, 0.8f);
      myComponent.setSplitterProportionKey("android.resource_dialog_splitter");

      myComponent.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));

      myPreviewPanel = new JPanel(new CardLayout());
      myComponent.setSecondComponent(myPreviewPanel);

      myTextArea = new JTextArea(5, 20);
      myPreviewPanel.add(ScrollPaneFactory.createScrollPane(myTextArea), TEXT);

      myPreviewPanel.add(myTabbedPane = new JBTabbedPane(JTabbedPane.BOTTOM, JTabbedPane.SCROLL_TAB_LAYOUT), TABS);

      myImageComponent = new JLabel();
      myImageComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myImageComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myImageComponent, IMAGE);

      myNoPreviewComponent = new JLabel("No Preview");
      myNoPreviewComponent.setHorizontalAlignment(SwingConstants.CENTER);
      myNoPreviewComponent.setVerticalAlignment(SwingConstants.CENTER);
      myPreviewPanel.add(myNoPreviewComponent, NONE);
    }

    public void showPreview(@Nullable ResourceItem element) {
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();

      if (element == null || element.getGroup().getType() == ResourceType.ID) {
        layout.show(myPreviewPanel, NONE);
        return;
      }

      try {
        VirtualFile file = element.getFile();
        if (file == null) {
          String value = element.getPreviewString();
          if (value == null) {
            String[] values = element.getPreviewStrings();

            if (values == null) {
              long time = System.currentTimeMillis();
              List<ResourceElement> resources = myManager.findValueResources(element.getGroup().getType().getName(), element.toString());
              if (ApplicationManagerEx.getApplicationEx().isInternal()) {
                System.out.println("Time: " + (System.currentTimeMillis() - time)); // XXX
              }

              int size = resources.size();
              if (size == 1) {
                value = getResourceElementValue(resources.get(0));
                element.setPreviewString(value);
              }
              else if (size > 1) {
                values = new String[size];
                String[] tabNames = new String[size];
                for (int i = 0; i < size; i++) {
                  ResourceElement resource = resources.get(i);
                  values[i] = getResourceElementValue(resource);

                  PsiDirectory directory = resource.getXmlTag().getContainingFile().getParent();
                  String tabName = directory == null ? "unknown-" + i : directory.getName();
                  tabNames[i] = tabName.substring(tabName.indexOf('-') + 1);
                }
                element.setPreviewStrings(values, tabNames);
              }
              else {
                layout.show(myPreviewPanel, NONE);
                return;
              }
            }

            if (values != null) {
              int selectedIndex = myTabbedPane.getSelectedIndex();
              myTabbedPane.removeAll();

              String[] tabNames = element.getTabNames();

              if (selectedIndex == -1) {
                for (int i = 0; i < tabNames.length; i++) {
                  if (tabNames[i].startsWith("en")) {
                    selectedIndex = i;
                    break;
                  }
                }
              }

              for (int i = 0; i < tabNames.length; i++) {
                JTextArea textArea = new JTextArea(5, 20);
                textArea.setText(values[i]);
                textArea.setEditable(false);
                myTabbedPane.addTab(tabNames[i], ScrollPaneFactory.createScrollPane(textArea));
              }

              if (selectedIndex >= 0 && selectedIndex < tabNames.length) {
                myTabbedPane.setSelectedIndex(selectedIndex);
              }
              layout.show(myPreviewPanel, TABS);
              return;
            }
          }
          if (value == null) {
            layout.show(myPreviewPanel, NONE);
            return;
          }

          myTextArea.setText(value);
          myTextArea.setEditable(false);
          layout.show(myPreviewPanel, TEXT);
        }
        else if (ImageFileTypeManager.getInstance().isImage(file)) {
          Icon icon = element.getPreviewIcon();
          if (icon == null) {
            icon = new SizedIcon(100, 100, new ImageIcon(file.getPath()));
            element.setPreviewIcon(icon);
          }
          myImageComponent.setIcon(icon);
          layout.show(myPreviewPanel, IMAGE);
        }
        else if (file.getFileType() == XmlFileType.INSTANCE) {
          String value = element.getPreviewString();
          if (value == null) {
            value = new String(file.contentsToByteArray());
            element.setPreviewString(value);
          }
          myTextArea.setText(value);
          myTextArea.setEditable(false);
          layout.show(myPreviewPanel, TEXT);
        }
        else {
          layout.show(myPreviewPanel, NONE);
        }
      }
      catch (IOException e) {
        layout.show(myPreviewPanel, NONE);
      }
    }

    private void select(String type, String name) {
      for (ResourceGroup group : myGroups) {
        if (type.equalsIgnoreCase(group.getName())) {
          for (ResourceItem item : group.getItems()) {
            if (name.equals(item.toString())) {
              myTreeBuilder.select(item);
              return;
            }
          }
          return;
        }
      }
    }
  }

  private static String getResourceElementValue(ResourceElement element) {
    String text = element.getRawText();
    if (StringUtil.isEmpty(text)) {
      return element.getXmlTag().getText();
    }
    return text;
  }

  private static class ResourceGroup {
    private List<ResourceItem> myItems = new ArrayList<ResourceItem>();
    private final ResourceType myType;

    public ResourceGroup(ResourceType type, ResourceManager manager) {
      myType = type;

      final String resourceType = type.getName();

      Collection<String> resourceNames = manager.getValueResourceNames(resourceType);
      for (String resourceName : resourceNames) {
        myItems.add(new ResourceItem(this, resourceName, null, RESOURCE_ITEM_ICON));
      }
      final Set<String> fileNames = new HashSet<String>();

      manager.processFileResources(resourceType, new FileResourceProcessor() {
        @Override
        public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
          if (fileNames.add(resName)) {
            myItems.add(new ResourceItem(ResourceGroup.this, resName, resFile, resFile.getFileType().getIcon()));
          }
          return true;
        }
      });

      if (type == ResourceType.ID) {
        for (String id : manager.getIds()) {
          if (!resourceNames.contains(id)) {
            myItems.add(new ResourceItem(this, id, null, RESOURCE_ITEM_ICON));
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

    public ResourceType getType() {
      return myType;
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
    private final VirtualFile myFile;
    private final Icon myIcon;
    private String myPreviewString;
    private String[] myPreviewStrings;
    private String[] myNames;
    private Icon myPreviewIcon;

    public ResourceItem(@NotNull ResourceGroup group, @NotNull String name, @Nullable VirtualFile file, Icon icon) {
      myGroup = group;
      myName = name;
      myFile = file;
      myIcon = icon;
    }

    public ResourceGroup getGroup() {
      return myGroup;
    }

    public String getName() {
      return myGroup.getName() + "/" + myName;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getPreviewString() {
      return myPreviewString;
    }

    public void setPreviewString(String previewString) {
      myPreviewString = previewString;
    }

    public String[] getPreviewStrings() {
      return myPreviewStrings;
    }

    public String[] getTabNames() {
      return myNames;
    }

    public void setPreviewStrings(String[] previewStrings, String[] names) {
      myPreviewStrings = previewStrings;
      myNames = names;
    }

    public Icon getPreviewIcon() {
      return myPreviewIcon;
    }

    public void setPreviewIcon(Icon previewIcon) {
      myPreviewIcon = previewIcon;
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
      TreeNodeDescriptor descriptor = new TreeNodeDescriptor(parentDescriptor, element, element == null ? null : element.toString());
      if (element instanceof ResourceGroup) {
        descriptor.setIcon(AllIcons.Nodes.TreeClosed);
      }
      else if (element instanceof ResourceItem) {
        descriptor.setIcon(((ResourceItem)element).getIcon());
      }
      return descriptor;
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
