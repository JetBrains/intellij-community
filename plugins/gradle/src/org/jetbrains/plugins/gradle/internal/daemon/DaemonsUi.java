// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.GradleConnectorService;
import org.jetbrains.plugins.gradle.statistics.GradleActionsUsagesCollector;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

import static org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServicesKt.*;

/**
 * @author Vladislav.Soroka
 */
public class DaemonsUi implements Disposable {

  private final Project myProject;
  private final TableView<DaemonState> myTable;
  private final ListTableModel<DaemonState> myTableModel;
  private final RefreshAction myRefreshAction;
  private final StopAllAction myStopAllAction;
  private final StopWhenIdleAllAction myStopWhenIdleAllAction;
  private final StopSelectedAction myStopSelectedAction;
  private final JTextArea myDescriptionLabel;

  private final JBLoadingPanel myContent;
  private MyDialogWrapper myDialog;
  private boolean myShowStopped;

  public DaemonsUi(Project project) {
    myProject = project;
    myRefreshAction = new RefreshAction();
    myStopWhenIdleAllAction = new StopWhenIdleAllAction();
    myStopAllAction = new StopAllAction();
    myStopSelectedAction = new StopSelectedAction();
    myContent = new JBLoadingPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP), myProject);
    myTable = new TableView<>(createListModel());
    myTableModel = myTable.getListTableModel();
    myDescriptionLabel = new JTextArea(6, 50);
    myDescriptionLabel.setWrapStyleWord(true);
    myDescriptionLabel.setLineWrap(true);
    myDescriptionLabel.setEditable(false);

    final JScrollPane label = ScrollPaneFactory.createScrollPane(myDescriptionLabel);
    final JPanel descriptionPanel = new JPanel(new BorderLayout());

    descriptionPanel.add(label, BorderLayout.CENTER);
    JBCheckBox showStoppedCb = new JBCheckBox(GradleBundle.message("gradle.daemons.show.stopped"));
    showStoppedCb.addActionListener(e -> {
      if (myShowStopped != showStoppedCb.isSelected()) {
        myShowStopped = showStoppedCb.isSelected();
        updateDaemonsList();
      }
    });
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, showStoppedCb);
    descriptionPanel.add(showStoppedCb, BorderLayout.SOUTH);
    descriptionPanel.setBorder(IdeBorderFactory.createTitledBorder(GradleBundle.message("gradle.daemons.description.title"), false));

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        DaemonState daemonState = myTable.getSelectedObject();
        if (daemonState != null) {
          String desc = daemonState.getDescription();
          myDescriptionLabel.setText(desc);
          myDescriptionLabel.setCaretPosition(0);
        }
        else {
          myDescriptionLabel.setText(null);
        }
      }
    });

    myContent.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    myContent.add(descriptionPanel, BorderLayout.SOUTH);
  }

  @Override
  public void dispose() { }

  public void show() {
    myDialog = new MyDialogWrapper();
    myDialog.show();
    updateDaemonsList();
  }

  private void updateDaemonsList() {
    Runnable updateDaemons = () -> {
      Set<String> gradleUserHomes = GradleConnectorService.getKnownGradleUserHomes(myProject);
      List<DaemonState> daemonStateList = ContainerUtil.filter(getDaemonsStatus(gradleUserHomes),
                                                               state -> myShowStopped || state.getToken() != null);
      ApplicationManager.getApplication().invokeLater(() -> {
        myTableModel.setItems(new ArrayList<>(daemonStateList));
        myContent.stopLoading();
        invalidateActions();
      });
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      myContent.startLoading();
      ApplicationManager.getApplication().executeOnPooledThread(updateDaemons);
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        myContent.startLoading();
        ApplicationManager.getApplication().executeOnPooledThread(updateDaemons);
      });
    }



  }

  private void invalidateActions() {
    if (myDialog != null) {
      myDialog.invalidateActions();
    }
  }
  protected ListTableModel<DaemonState> createListModel() {
    final ColumnInfo<DaemonState, String> pidColumn = new TableColumn(GradleBundle.message("column.name.daemon.PID"), 80) {
      @Nullable
      @Override
      public String valueOf(DaemonState daemonState) {
        return String.valueOf(daemonState.getPid());
      }

      @Nullable
      @Override
      public Comparator<DaemonState> getComparator() {
        return Comparator.comparing(DaemonState::getPid);
      }
    };
    final ColumnInfo<DaemonState, String> statusColumn = new TableColumn(GradleBundle.message("column.name.daemon.status"), 100) {
      @Nullable
      @Override
      public String valueOf(DaemonState daemonState) {
        return daemonState.getStatus();
      }

      @Nullable
      @Override
      public Comparator<DaemonState> getComparator() {
        return Comparator.comparing(DaemonState::getStatus);
      }
    };
    final ColumnInfo<DaemonState, String> timeColumn = new TableColumn(GradleBundle.message("column.name.daemon.timestamp"), 150) {
      @NotNull
      @Override
      public String valueOf(DaemonState daemonState) {
        return DateFormatUtil.formatPrettyDateTime(daemonState.getTimestamp());
      }

      @Nullable
      @Override
      public Comparator<DaemonState> getComparator() {
        return Comparator.comparing(DaemonState::getTimestamp);
      }
    };
    final ColumnInfo<DaemonState, String> infoColumn = new TableColumn(GradleBundle.message("column.name.daemon.info"), -1) {
      @NotNull
      @Override
      public String valueOf(DaemonState daemonState) {
        return daemonState.getVersion() != null ? daemonState.getVersion() : StringUtil.capitalize(daemonState.getReason());
      }
    };

    ColumnInfo[] columnInfos = new ColumnInfo[]{pidColumn, statusColumn, infoColumn, timeColumn};
    return new ListTableModel<>(columnInfos, new ArrayList<>(), 3, SortOrder.DESCENDING);
  }

  private abstract static class TableColumn extends ColumnInfo<DaemonState, @NlsContexts.ListItem String> {
    private final int myWidth;
    private DefaultTableCellRenderer myRenderer;

    TableColumn(@NlsContexts.ColumnName final String name, int width) {
      super(name);
      myWidth = width;
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
    }

    @Override
    public TableCellRenderer getRenderer(DaemonState element) {
      if (myRenderer == null) {
        myRenderer = new DefaultTableCellRenderer();
      }
      if (element != null) {
        myRenderer.setText(valueOf(element));
      }
      return myRenderer;
    }
  }

  private class RefreshAction extends AbstractAction {
    RefreshAction() {
      super(GradleBundle.message("gradle.daemons.refresh"), AllIcons.Actions.Refresh);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      GradleActionsUsagesCollector.trigger(myProject, GradleActionsUsagesCollector.REFRESH_DAEMONS);
      updateDaemonsList();
    }
  }

  private class StopAllAction extends AbstractAction {
    StopAllAction() {
      super(GradleBundle.message("gradle.daemons.stop.all"));
      setEnabled(false);
    }

    @Override
    public boolean isEnabled() {
      return ContainerUtil.exists(myTableModel.getItems(), state -> state.getToken() != null && !"Stopped".equals(state.getStatus()));
    }

    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      GradleActionsUsagesCollector.trigger(myProject, GradleActionsUsagesCollector.STOP_ALL_DAEMONS);
      ApplicationManager.getApplication().invokeLater(() -> {
        stopDaemons(GradleConnectorService.getKnownGradleUserHomes(myProject));
        updateDaemonsList();
      });
    }
  }

  private class StopSelectedAction extends AbstractAction {
    StopSelectedAction() {
      super(GradleBundle.message("gradle.daemons.stop.selected"));
      setEnabled(false);
    }

    @Override
    public boolean isEnabled() {
      Collection<DaemonState> selection = myTable.getSelection();
      return !selection.isEmpty() &&
             ContainerUtil.exists(selection, state -> state.getToken() != null && !"Stopped".equals(state.getStatus()));
    }

    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      GradleActionsUsagesCollector.trigger(myProject, GradleActionsUsagesCollector.STOP_SELECTED_DAEMONS);
      List<DaemonState> selectedObjects = myTable.getSelectedObjects();
      ApplicationManager.getApplication().invokeLater(() -> {
        stopDaemons(GradleConnectorService.getKnownGradleUserHomes(myProject), selectedObjects);
        updateDaemonsList();
      });
    }
  }

  private class StopWhenIdleAllAction extends AbstractAction {
    StopWhenIdleAllAction() {
      super(GradleBundle.message("gradle.daemons.stopWhenIdle.all"));
      setEnabled(false);
    }

    @Override
    public boolean isEnabled() {
      return ContainerUtil.exists(myTableModel.getItems(), state -> state.getToken() != null && !"Stopped".equals(state.getStatus()));
    }

    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      GradleActionsUsagesCollector.trigger(myProject, GradleActionsUsagesCollector.GRACEFUL_STOP_ALL_DAEMONS);
      ApplicationManager.getApplication().invokeLater(() -> {
        gracefulStopDaemons(GradleConnectorService.getKnownGradleUserHomes(myProject));
        updateDaemonsList();
      });
    }
  }

  private class MyDialogWrapper extends DialogWrapper {
    {
      setTitle(GradleBundle.message("gradle.daemons.gradle.daemons"));
      setModal(false);
      init();

      myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(@NotNull ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) return;
          invalidateActions();
        }
      });
    }

    private AbstractAction myCloseAction;

    MyDialogWrapper() {super(true);}

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      JLabel infoLabel = new JLabel(XmlStringUtil.wrapInHtml(
        GradleBundle.message("daemons.started.by.are.displayed", ApplicationNamesInfo.getInstance().getFullProductName())));
      infoLabel.setIcon(UIUtil.getInformationIcon());
      panel.add(infoLabel, BorderLayout.CENTER);
      return panel;
    }

    @Override
    protected JComponent createCenterPanel() {
      return myContent;
    }

    @Override
    protected void dispose() {
      super.dispose();
      myDialog = null;
      Disposer.dispose(DaemonsUi.this);
    }

    @Override
    protected String getDimensionServiceKey() {
      return "GradleDaemons";
    }


    @Override
    public JComponent getPreferredFocusedComponent() {
      return myTable;
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{myStopWhenIdleAllAction, myStopAllAction, myStopSelectedAction, myCloseAction};
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
      return new Action[]{myRefreshAction};
    }

    @Override
    protected void createDefaultActions() {
      super.createDefaultActions();
      myCloseAction = new AbstractAction(GradleBundle.message("gradle.daemons.close")) {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          doOKAction();
        }
      };
      myCloseAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
    }

    public void invalidateActions() {
      myStopSelectedAction.setEnabled(myStopSelectedAction.isEnabled());
      myStopAllAction.setEnabled(myStopAllAction.isEnabled());
      myStopWhenIdleAllAction.setEnabled(myStopWhenIdleAllAction.isEnabled());
    }
  }
}
