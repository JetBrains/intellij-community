/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.actions;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.SdkConstants;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.ui.JBDefaultTreeCellRenderer;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProcessChooserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.AndroidProcessChooserDialog");

  @NonNls private static final String DEBUGGABLE_PROCESS_PROPERTY = "DEBUGGABLE_PROCESS";
  @NonNls private static final String DEBUGGABLE_DEVICE_PROPERTY = "DEBUGGABLE_DEVICE";
  @NonNls private static final String RUN_CONFIGURATION_NAME_PATTERN = "Android Debugger (%s)";

  private final Project myProject;
  private JPanel myContentPanel;
  private Tree myProcessTree;

  private final MergingUpdateQueue myUpdatesQueue;
  private final AndroidDebugBridge.IClientChangeListener myClientChangeListener;
  private final AndroidDebugBridge.IDeviceChangeListener myDeviceChangeListener;

  protected AndroidProcessChooserDialog(@NotNull Project project) {
    super(project);
    setTitle("Choose process");

    myProject = project;
    myUpdatesQueue =
      new MergingUpdateQueue("AndroidProcessChooserDialogUpdatingQueue", 500, true, MergingUpdateQueue.ANY_COMPONENT, myProject);

    doUpdateTree();

    myClientChangeListener = new AndroidDebugBridge.IClientChangeListener() {
      @Override
      public void clientChanged(Client client, int changeMask) {
        updateTree();
      }
    };
    AndroidDebugBridge.addClientChangeListener(myClientChangeListener);

    myDeviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
      @Override
      public void deviceConnected(IDevice device) {
        updateTree();
      }

      @Override
      public void deviceDisconnected(IDevice device) {
        updateTree();
      }

      @Override
      public void deviceChanged(IDevice device, int changeMask) {
        updateTree();
      }
    };
    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener);

    myProcessTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        getOKAction().setEnabled(getSelectedDevice() != null && getSelectedClient() != null);
      }
    });

    myProcessTree.setCellRenderer(new JBDefaultTreeCellRenderer(myProcessTree) {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean sel,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof IDevice) {
            value = getPresentableName((IDevice)userObject);
          }
          else if (userObject instanceof Client) {
            value = getClientDescription((Client)userObject);
          }
        }

        return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      }

      @Override
      public Icon getLeafIcon() {
        return null;
      }

      @Override
      public Icon getOpenIcon() {
        return null;
      }

      @Override
      public Icon getClosedIcon() {
        return null;
      }
    });

    myProcessTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && isOKActionEnabled()) {
          doOKAction();
        }
      }
    });

    myProcessTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && isOKActionEnabled()) {
          doOKAction();
        }
      }
    });

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProcessTree;
  }

  @Override
  protected void dispose() {
    super.dispose();

    AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener);
    AndroidDebugBridge.removeClientChangeListener(myClientChangeListener);
  }

  @NotNull
  private static String getPresentableName(IDevice device) {
    String serialNumber = device.getSerialNumber();
    final String avdName = device.getAvdName();

    if (serialNumber == null || serialNumber.length() == 0) {
      serialNumber = "<unknown>";
    }

    return avdName == null || avdName.length() == 0
           ? serialNumber
           : serialNumber + " (" + avdName + ')';
  }

  private void updateTree() {
    myUpdatesQueue.queue(new Update(AndroidProcessChooserDialog.this) {
      @Override
      public void run() {
        final AndroidDebugBridge debugBridge = AndroidUtils.getDebugBridge(myProject);
        if (debugBridge != null && AndroidUtils.isDdmsCorrupted(debugBridge)) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(myContentPanel, AndroidBundle.message("ddms.corrupted.error"));
              AndroidProcessChooserDialog.this.close(1);
            }
          });
          return;
        }

        doUpdateTree();
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void doUpdateTree() {
    final AndroidDebugBridge debugBridge = AndroidUtils.getDebugBridge(myProject);

    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final DefaultTreeModel model = new DefaultTreeModel(root);

    if (debugBridge == null) {
      myProcessTree.setModel(model);
      return;
    }

    final Set<String> processNames = collectAllProcessNames(myProject);

    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    final String prevProcess = properties.getValue(DEBUGGABLE_PROCESS_PROPERTY);
    final String prevDevice = properties.getValue(DEBUGGABLE_DEVICE_PROPERTY);

    TreeNode selectedDeviceNode = null;
    TreeNode selectedClientNode = null;

    Object[] firstTreePath = null;

    final IDevice[] devices = debugBridge.getDevices();
    for (IDevice device : devices) {
      final DefaultMutableTreeNode deviceNode = new DefaultMutableTreeNode(device);
      root.add(deviceNode);

      for (Client client : device.getClients()) {
        final String clientDescription = getClientDescription(client);

        if (clientDescription != null && processNames.contains(clientDescription)) {
          final DefaultMutableTreeNode clientNode = new DefaultMutableTreeNode(client);
          deviceNode.add(clientNode);

          final String deviceName = getPresentableName(device);

          if (clientDescription.equals(prevProcess) &&
              (selectedDeviceNode == null || deviceName.equals(prevDevice))) {
            selectedClientNode = clientNode;
            selectedDeviceNode = deviceNode;
          }

          if (firstTreePath == null) {
            firstTreePath = new Object[]{root, deviceNode, clientNode};
          }
        }
      }
    }

    final Object[] pathToSelect =
      selectedDeviceNode != null ? new Object[]{root, selectedDeviceNode, selectedClientNode} : firstTreePath;

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myProcessTree.setModel(model);

        if (pathToSelect != null) {
          myProcessTree.getSelectionModel().setSelectionPath(new TreePath(pathToSelect));
        }
        else {
          getOKAction().setEnabled(false);
        }

        TreeUtil.expandAll(myProcessTree);
      }
    });
  }

  @NotNull
  private static Set<String> collectAllProcessNames(Project project) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    final Set<String> result = new HashSet<String>();

    for (AndroidFacet facet : facets) {
      final Manifest manifest = facet.getManifest();
      if (manifest != null) {

        final String packageName = manifest.getPackage().getValue();
        if (packageName != null) {
          result.add(packageName);
        }

        final XmlElement xmlElement = manifest.getXmlElement();
        if (xmlElement != null) {
          collectProcessNames(xmlElement, result);
        }
      }
    }

    return result;
  }

  @Nullable
  private static String getClientDescription(Client client) {
    final ClientData clientData = client.getClientData();
    return clientData != null ? clientData.getClientDescription() : null;
  }

  private static void collectProcessNames(XmlElement xmlElement, final Set<String> result) {
    xmlElement.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        if (!"process".equals(attribute.getName())) {
          return;
        }

        final String namespace = attribute.getNamespace();
        if (SdkConstants.NS_RESOURCES.equals(namespace)) {
          return;
        }

        final String value = attribute.getValue();
        if (value != null) {
          result.add(value);
        }
      }
    });
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    final IDevice selectedDevice = getSelectedDevice();
    assert selectedDevice != null;

    final Client selectedClient = getSelectedClient();
    assert selectedClient != null;

    properties.setValue(DEBUGGABLE_DEVICE_PROPERTY, getPresentableName(selectedDevice));
    properties.setValue(DEBUGGABLE_PROCESS_PROPERTY, selectedClient.getClientData().getClientDescription());

    final String debugPort = Integer.toString(selectedClient.getDebuggerListenPort());

    closeOldSessionAndRun(debugPort);
  }

  private void closeOldSessionAndRun(final String debugPort) {
    final String configurationName = getRunConfigurationName(debugPort);
    final Collection<RunContentDescriptor> descriptors =
      ExecutionHelper.findRunningConsoleByTitle(myProject, new NotNullFunction<String, Boolean>() {
        @NotNull
        @Override
        public Boolean fun(String title) {
          return configurationName.equals(title);
        }
      });

    if (descriptors.size() > 0) {
      final RunContentDescriptor descriptor = descriptors.iterator().next();
      final ProcessHandler processHandler = descriptor.getProcessHandler();
      final Content content = descriptor.getAttachedContent();

      if (processHandler != null && content != null) {
        final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();

        if (processHandler.isProcessTerminated()) {
          ExecutionManager.getInstance(myProject).getContentManager()
            .removeRunContent(executor, descriptor);
        }
        else {
          content.getManager().setSelectedContent(content);
          ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(executor.getToolWindowId());
          window.activate(null, false, true);
          return;
        }
      }
    }

    runSession(debugPort);
  }

  private void runSession(String debugPort) {
    final RunnerAndConfigurationSettings settings = createRunConfiguration(myProject, debugPort);
    ProgramRunnerUtil.executeConfiguration(myProject, settings, DefaultDebugExecutor.getDebugExecutorInstance());
  }

  private static RunnerAndConfigurationSettings createRunConfiguration(Project project, String debugPort) {
    final RemoteConfigurationType remoteConfigurationType = RemoteConfigurationType.getInstance();

    if (remoteConfigurationType == null) {
      LOG.error("Cannot create remote configuration");
    }

    final ConfigurationFactory factory = remoteConfigurationType.getFactory();
    final RunnerAndConfigurationSettings runSettings =
      RunManagerEx.getInstanceEx(project).createConfiguration(getRunConfigurationName(debugPort), factory);
    final RemoteConfiguration configuration = (RemoteConfiguration)runSettings.getConfiguration();

    configuration.HOST = "localhost";
    configuration.PORT = debugPort;
    configuration.USE_SOCKET_TRANSPORT = true;
    configuration.SERVER_MODE = false;

    return runSettings;
  }

  @NotNull
  private static String getRunConfigurationName(String debugPort) {
    return String.format(RUN_CONFIGURATION_NAME_PATTERN, debugPort);
  }

  @Nullable
  private IDevice getSelectedDevice() {
    final TreePath selectionPath = myProcessTree.getSelectionPath();
    if (selectionPath == null || selectionPath.getPathCount() < 2) {
      return null;
    }

    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)selectionPath.getPathComponent(1);
    final Object obj = selectedNode.getUserObject();
    return obj instanceof IDevice ? (IDevice)obj : null;
  }

  @Nullable
  private Client getSelectedClient() {
    final TreePath selectionPath = myProcessTree.getSelectionPath();
    if (selectionPath == null || selectionPath.getPathCount() < 3) {
      return null;
    }

    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)selectionPath.getPathComponent(2);
    final Object obj = selectedNode.getUserObject();
    return obj instanceof Client ? (Client)obj : null;
  }
}
