package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerImpl;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import com.intellij.remoteServer.impl.runtime.ui.tree.TreeBuilderBase;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionListener;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author michael.golubev
 */
public class ServersToolWindowContent extends JPanel implements Disposable {
  public static final DataKey<ServersToolWindowContent> KEY = DataKey.create("serversToolWindowContent");
  @NonNls private static final String PLACE_TOOLBAR = "ServersToolWindowContent#Toolbar";
  @NonNls private static final String SERVERS_TOOL_WINDOW_TOOLBAR = "RemoteServersViewToolbar";
  @NonNls private static final String SERVERS_TOOL_WINDOW_POPUP = "RemoteServersViewPopup";

  @NonNls
  private static final String HELP_ID = "Application_Servers_tool_window";
  private static final String MESSAGE_CARD = "message";
  private static final String EMPTY_SELECTION_MESSAGE = "Select a server or deployment in the tree to view details";

  private static final int POLL_DEPLOYMENTS_DELAY = 2000;

  private final Tree myTree;
  private final CardLayout myPropertiesPanelLayout;
  private final JPanel myPropertiesPanel;
  private final JLabel myMessageLabel;
  private final Map<String, JComponent> myLogComponents = new HashMap<String, JComponent>();

  private final DefaultTreeModel myTreeModel;
  private TreeBuilderBase myBuilder;
  private AbstractTreeNode<?> myLastSelection;

  private final Project myProject;

  public ServersToolWindowContent(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;

    myTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);

    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new NodeRenderer());
    myTree.setLineStyleAngled();

    getMainPanel().add(createToolbar(), BorderLayout.WEST);
    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT));
    myPropertiesPanelLayout = new CardLayout();
    myPropertiesPanel = new JPanel(myPropertiesPanelLayout);
    myMessageLabel = new JLabel(EMPTY_SELECTION_MESSAGE, SwingConstants.CENTER);
    myPropertiesPanel.add(MESSAGE_CARD, new Wrapper(myMessageLabel));
    splitter.setSecondComponent(myPropertiesPanel);
    getMainPanel().add(splitter, BorderLayout.CENTER);

    setupBuilder(project);

    for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
      contributor.setupTree(myProject, myTree, myBuilder);
    }

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        onSelectionChanged();
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        AnAction connectAction = ActionManager.getInstance().getAction("RemoteServers.ConnectServer");
        AnActionEvent actionEvent = AnActionEvent.createFromInputEvent(connectAction, event, ActionPlaces.UNKNOWN);
        connectAction.actionPerformed(actionEvent);
        return true;
      }
    }.installOn(myTree);

    DefaultActionGroup popupActionGroup = new DefaultActionGroup();
    popupActionGroup.add(ActionManager.getInstance().getAction(SERVERS_TOOL_WINDOW_TOOLBAR));
    popupActionGroup.add(ActionManager.getInstance().getAction(SERVERS_TOOL_WINDOW_POPUP));
    PopupHandler.installPopupHandler(myTree, popupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());

    new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);
  }

  private void onSelectionChanged() {
    Set<AbstractTreeNode> nodes = myBuilder.getSelectedElements(AbstractTreeNode.class);
    if (nodes.size() != 1) {
      showMessageLabel(EMPTY_SELECTION_MESSAGE);
      myLastSelection = null;
      return;
    }

    AbstractTreeNode<?> node = nodes.iterator().next();
    if (Comparing.equal(node, myLastSelection)) {
      return;
    }

    myLastSelection = node;
    if (node instanceof ServersTreeStructure.LogProvidingNode) {
      ServersTreeStructure.LogProvidingNode logNode = (ServersTreeStructure.LogProvidingNode)node;
      JComponent logComponent = logNode.getComponent();
      if (logComponent != null) {
        String cardName = logNode.getLogId();
        JComponent oldComponent = myLogComponents.get(cardName);
        if (!logComponent.equals(oldComponent)) {
          myLogComponents.put(cardName, logComponent);
          if (oldComponent != null) {
            myPropertiesPanel.remove(oldComponent);
          }
          myPropertiesPanel.add(cardName, logComponent);
        }
        myPropertiesPanelLayout.show(myPropertiesPanel, cardName);
      }
      else {
        showMessageLabel("");
      }
    }
    else if (node instanceof ServersTreeStructure.RemoteServerNode) {
      updateServerDetails((ServersTreeStructure.RemoteServerNode)node);
    }
    else {
      showMessageLabel("");
    }
  }

  private void updateServerDetails(ServersTreeStructure.RemoteServerNode node) {
    RemoteServer<?> server = ((ServersTreeStructure.RemoteServerNode)node).getValue();
    ServerConnection connection = ServerConnectionManager.getInstance().getConnection(server);
    if (connection == null) {
      showMessageLabel("Double-click on the server node to connect");
    }
    else {
      showMessageLabel(connection.getStatusText());
    }
  }

  private void showMessageLabel(final String text) {
    myMessageLabel.setText(UIUtil.toHtml(text));
    myPropertiesPanelLayout.show(myPropertiesPanel, MESSAGE_CARD);
  }

  private void setupBuilder(final @NotNull Project project) {
    ServersTreeStructure structure = new ServersTreeStructure(project);
    myBuilder = new TreeBuilderBase(myTree, structure, myTreeModel) {
      @Override
      protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
        return nodeDescriptor instanceof ServersTreeStructure.RemoteServerNode || nodeDescriptor instanceof ServersTreeStructure.DeploymentNodeImpl;
      }
    };
    Disposer.register(this, myBuilder);

    project.getMessageBus().connect().subscribe(ServerConnectionListener.TOPIC, new ServerConnectionListener() {
      @Override
      public void onConnectionCreated(@NotNull ServerConnection<?> connection) {
        getBuilder().queueUpdate();
      }

      @Override
      public void onConnectionStatusChanged(@NotNull ServerConnection<?> connection) {
        getBuilder().queueUpdate();
        updateSelectedServerDetails();
        if (connection.getStatus() == ConnectionStatus.CONNECTED) {
          pollDeployments(connection);
        }
      }

      @Override
      public void onDeploymentsChanged(@NotNull ServerConnection<?> connection) {
        getBuilder().queueUpdate();
        updateSelectedServerDetails();
      }
    });
  }

  private void updateSelectedServerDetails() {
    if (myLastSelection instanceof ServersTreeStructure.RemoteServerNode) {
      updateServerDetails((ServersTreeStructure.RemoteServerNode)myLastSelection);
    }
  }

  private static void pollDeployments(final ServerConnection connection) {
    connection.computeDeployments(new Runnable() {

      @Override
      public void run() {
        new Alarm().addRequest(new Runnable() {

          @Override
          public void run() {
            if (connection == ServerConnectionManager.getInstance().getConnection(connection.getServer())) {
              pollDeployments(connection);
            }
          }
        }, POLL_DEPLOYMENTS_DELAY, ModalityState.any());
      }
    });
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(SERVERS_TOOL_WINDOW_TOOLBAR));
    group.add(new Separator());
    group.add(new ContextHelpAction(HELP_ID));

    ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, group, false);


    myTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new DataProvider() {

      @Override
      public Object getData(@NonNls String dataId) {
        if (KEY.getName().equals(dataId)) {
          return ServersToolWindowContent.this;
        }
        for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensions()) {
          Object data = contributor.getData(dataId, ServersToolWindowContent.this);
          if (data != null) {
            return data;
          }
        }
        return null;
      }
    });
    actionToolBar.setTargetComponent(myTree);
    return actionToolBar.getComponent();
  }

  public JPanel getMainPanel() {
    return this;
  }

  @Override
  public void dispose() {
  }

  public TreeBuilderBase getBuilder() {
    return myBuilder;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void select(@NotNull final ServerConnection<?> connection) {
    myBuilder.select(ServersTreeStructure.RemoteServerNode.class, new TreeVisitor<ServersTreeStructure.RemoteServerNode>() {
      @Override
      public boolean visit(@NotNull ServersTreeStructure.RemoteServerNode node) {
        return isServerNodeMatch(node, connection);
      }
    }, null, false);
  }

  public void select(@NotNull final ServerConnection<?> connection, @NotNull final String deploymentName) {
    myBuilder.getUi().queueUpdate(connection).doWhenDone(new Runnable() {
      @Override
      public void run() {
        myBuilder.select(ServersTreeStructure.DeploymentNodeImpl.class, new TreeVisitor<ServersTreeStructure.DeploymentNodeImpl>() {
          @Override
          public boolean visit(@NotNull ServersTreeStructure.DeploymentNodeImpl node) {
            return isDeploymentNodeMatch(node, connection, deploymentName);
          }
        }, null, false);
      }
    });
  }

  public void select(@NotNull final ServerConnection<?> connection,
                     @NotNull final String deploymentName,
                     @NotNull final String logName) {
    myBuilder.getUi().queueUpdate(connection).doWhenDone(new Runnable() {
      @Override
      public void run() {
        myBuilder.select(ServersTreeStructure.DeploymentLogNode.class, new TreeVisitor<ServersTreeStructure.DeploymentLogNode>() {
          @Override
          public boolean visit(@NotNull ServersTreeStructure.DeploymentLogNode node) {
            AbstractTreeNode parent = node.getParent();
            return parent instanceof ServersTreeStructure.DeploymentNodeImpl
                   && isDeploymentNodeMatch((ServersTreeStructure.DeploymentNodeImpl)parent, connection, deploymentName)
                   && node.getValue().getPresentableName().equals(logName);
          }
        }, null, false);
      }
    });
  }

  private static boolean isServerNodeMatch(@NotNull final ServersTreeStructure.RemoteServerNode node,
                                           @NotNull final ServerConnection<?> connection) {
    return node.getValue().equals(connection.getServer());
  }

  private static boolean isDeploymentNodeMatch(@NotNull ServersTreeStructure.DeploymentNodeImpl node,
                                               @NotNull final ServerConnection<?> connection, @NotNull final String deploymentName) {
    AbstractTreeNode parent = node.getParent();
    return parent instanceof ServersTreeStructure.RemoteServerNode &&
           isServerNodeMatch((ServersTreeStructure.RemoteServerNode)parent, connection)
           && node.getValue().getName().equals(deploymentName);
  }
}
