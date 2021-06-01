// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowLogAction;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ConfigImportHelper.ConfigDirsSearchResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.IoErrorText;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.ide.IdeBundle.message;
import static com.intellij.internal.statistic.eventLog.events.EventFields.Int;
import static com.intellij.internal.statistic.eventLog.events.EventFields.Long;
import static com.intellij.notification.NotificationAction.createSimpleExpiring;

@SuppressWarnings("NonConstantLogger")
public final class OldDirectoryCleaner {
  @ApiStatus.Internal
  public static final class Stats extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("leftover.dirs", 1);
    private static final EventId SCHEDULED = GROUP.registerEvent("scan.scheduled");
    private static final EventId1<Integer> STARTED = GROUP.registerEvent("scan.started", Int("delay_days"));
    private static final EventId2<Integer, Long> COMPLETE = GROUP.registerEvent("cleanup.complete", Int("groups"), Long("total_mb"));

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }

    public static void scheduled() {
      SCHEDULED.log();
    }

    public static void started(int actualDelayDays) {
      STARTED.log(actualDelayDays);
    }

    public static void completed(int groups, long totalBytes) {
      COMPLETE.log(groups, (totalBytes + 500_000) / 1_000_000L);
    }
  }

  private final Logger myLogger = Logger.getInstance(OldDirectoryCleaner.class);
  private final long myBestBefore;

  public OldDirectoryCleaner(long bestBefore) {
    myBestBefore = bestBefore;
  }

  @RequiresBackgroundThread
  public void seekAndDestroy(@Nullable Project project, @Nullable ProgressIndicator indicator) {
    ConfigDirsSearchResult result = ConfigImportHelper.findConfigDirectories(PathManager.getConfigDir());
    List<DirectoryGroup> groups = collectDirectoryData(result, indicator);
    if (myLogger.isDebugEnabled()) {
      myLogger.debug("configs: " + result.getPaths());
      myLogger.debug("groups: " + groups);
    }

    if (myBestBefore != 0) {
      deleteCowardly(groups);
      Stats.completed(groups.size(), groups.stream().mapToLong(g -> g.size).sum());
    }
    else if (!groups.isEmpty()) {
      UpdateChecker.getNotificationGroup()
        .createNotification(message("old.dirs.notification.text"), NotificationType.INFORMATION)
        .addAction(createSimpleExpiring(message("old.dirs.notification.action"), () -> confirmAndDelete(project, groups)))
        .notify(project);
    }
  }

  private static class DirectoryGroup {
    private final @NlsSafe String name;
    private final List<Path> directories;
    private final long lastUpdated;
    private final long size;
    private final int entriesToDelete;

    private DirectoryGroup(String name, List<Path> directories, long lastUpdated, long size, int entriesToDelete) {
      this.name = name;
      this.directories = directories;
      this.lastUpdated = lastUpdated;
      this.size = size;
      this.entriesToDelete = entriesToDelete;
    }

    @Override
    public String toString() {
      return "{" + directories + ' ' + lastUpdated + '}';
    }
  }

  private List<DirectoryGroup> collectDirectoryData(ConfigDirsSearchResult result, @Nullable ProgressIndicator indicator) {
    List<Path> configs = result.getPaths();
    List<DirectoryGroup> groups = new ArrayList<>(configs.size());

    for (Path config : configs) {
      List<Path> directories = result.findRelatedDirectories(config, myBestBefore != 0);
      if (directories.isEmpty()) continue;

      long lastUpdated = 0, size = 0;
      int entriesToDelete = 0;
      for (Path directory : directories) {
        CollectingVisitor visitor = new CollectingVisitor(indicator);
        try {
          Files.walkFileTree(directory, visitor);
        }
        catch (IOException e) {
          myLogger.debug(e);
        }
        lastUpdated = Math.max(lastUpdated, visitor.lastUpdated);
        size += visitor.size;
        entriesToDelete += visitor.entriesToDelete;
      }
      if (myBestBefore == 0 || lastUpdated <= myBestBefore) {
        groups.add(new DirectoryGroup(result.getNameAndVersion(config), directories, lastUpdated, size, entriesToDelete));
      }
    }

    return groups;
  }

  private static class CollectingVisitor extends SimpleFileVisitor<Path> {
    private final @Nullable ProgressIndicator indicator;
    long lastUpdated = 0, size = 0;
    int entriesToDelete = 0;

    CollectingVisitor(@Nullable ProgressIndicator indicator) {
      this.indicator = indicator;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      if (indicator != null) indicator.checkCanceled();
      lastUpdated = Math.max(lastUpdated, attrs.lastModifiedTime().toMillis());
      size += attrs.size();
      entriesToDelete++;
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      entriesToDelete++;
      return FileVisitResult.CONTINUE;
    }
  }

  private void deleteCowardly(List<DirectoryGroup> groups) {
    for (DirectoryGroup group : groups) {
      for (Path directory : group.directories) {
        myLogger.info("deleting " + directory);
        try {
          NioFiles.deleteRecursively(directory);
        }
        catch (IOException e) {
          myLogger.error(e);
        }
      }
    }
  }

  private void confirmAndDelete(Project project, List<DirectoryGroup> groups) {
    MenuDialog dialog = new MenuDialog(project, groups);
    if (dialog.showAndGet()) {
      List<DirectoryGroup> selectedGroups = dialog.getSelectedGroups();
      if (!selectedGroups.isEmpty()) {
        new Task.Backgroundable(project, message("old.dirs.delete.progress")) {
          private Path currentRoot;
          private int progress = 0;

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.setIndeterminate(false);
            int total = selectedGroups.stream().mapToInt(g -> g.entriesToDelete).sum();
            List<String> errors = new ArrayList<>();

            for (DirectoryGroup group : selectedGroups) {
              for (Path directory : group.directories) {
                indicator.checkCanceled();
                indicator.setText(directory.toString());
                if (myLogger.isDebugEnabled()) myLogger.debug("deleting " + directory);
                currentRoot = directory;
                try {
                  NioFiles.deleteRecursively(directory, p -> {
                    indicator.checkCanceled();
                    indicator.setFraction((double)(progress++) / total);
                    indicator.setText2(currentRoot.relativize(p).toString());
                  });
                }
                catch (IOException e) {
                  myLogger.error(e);
                  errors.add(directory + " (" + IoErrorText.message(e) + ')');
                }
              }
            }

            if (!errors.isEmpty()) {
              @NlsSafe String content = String.join("<br>", errors);
              UpdateChecker.getNotificationGroup()
                .createNotification(message("old.dirs.delete.error"), content, NotificationType.WARNING)
                .addAction(ShowLogAction.notificationAction())
                .notify(project);
            }
          }
        }.queue();
      }
    }
  }

  private static class MenuDialog extends DialogWrapper {
    private final MenuTableModel myModel;

    MenuDialog(Project project, List<DirectoryGroup> groups) {
      super(project, false);
      myModel = new MenuTableModel(groups);
      setTitle(message("old.dirs.dialog.title"));
      setOKButtonText(message("old.dirs.dialog.delete.button", groups.size()));
      init();
    }

    List<DirectoryGroup> getSelectedGroups() {
      return myModel.getSelectedGroups();
    }

    @Override
    protected JComponent createCenterPanel() {
      JBTable table = new JBTable(myModel);
      table.setShowGrid(false);
      table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(30));
      table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(300));
      table.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(120));
      table.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(120));
      TableCellRenderer renderer = (tbl, value, selected, focused, row, col) -> {
        int alignment = col == 1 ? SwingConstants.LEFT : SwingConstants.RIGHT;
        return new JBLabel((String)value, alignment).withBorder(JBUI.Borders.empty(0, 5));
      };
      table.getColumnModel().getColumn(1).setHeaderRenderer(renderer);
      table.getColumnModel().getColumn(1).setCellRenderer(renderer);
      table.getColumnModel().getColumn(2).setHeaderRenderer(renderer);
      table.getColumnModel().getColumn(2).setCellRenderer(renderer);
      table.getColumnModel().getColumn(3).setHeaderRenderer(renderer);
      table.getColumnModel().getColumn(3).setCellRenderer(renderer);
      myModel.addTableModelListener(e -> {
        int n = myModel.mySelected.cardinality();
        setOKButtonText(message("old.dirs.dialog.delete.button", n));
        setOKActionEnabled(n > 0);
      });
      JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(5)));
      panel.add(new MultiLineLabel(message("old.dirs.dialog.text")), BorderLayout.NORTH);
      JBScrollPane tableScroll = new JBScrollPane(table);
      table.setFillsViewportHeight(true);
      panel.add(tableScroll, BorderLayout.CENTER);
      return panel;
    }

    private static class MenuTableModel extends AbstractTableModel {
      private final List<DirectoryGroup> myGroups;
      private final BitSet mySelected = new BitSet();
      private final @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String[] myColumnNames =
        {"old.dirs.column.name", "old.dirs.column.updated", "old.dirs.column.size"};
      private final long myNow = System.currentTimeMillis();

      MenuTableModel(List<DirectoryGroup> groups) {
        myGroups = groups;
        mySelected.set(0, myGroups.size());
      }

      List<DirectoryGroup> getSelectedGroups() {
        return IntStream.range(0, myGroups.size()).filter(mySelected::get).mapToObj(myGroups::get).collect(Collectors.toList());
      }

      @Override
      public int getRowCount() {
        return myGroups.size();
      }

      @Override
      public int getColumnCount() {
        return 4;
      }

      @Override
      public String getColumnName(int column) {
        return column == 0 ? "" : message(myColumnNames[column - 1]);
      }

      @Override
      public Class<?> getColumnClass(int column) {
        return column == 0 ? Boolean.class : String.class;
      }

      @Override
      public Object getValueAt(int row, int column) {
        switch (column) {
          case 0:  return mySelected.get(row);
          case 1:  return myGroups.get(row).name;
          case 2:  return DateFormatUtil.formatBetweenDates(myGroups.get(row).lastUpdated, myNow);
          case 3:  return StringUtil.formatFileSize(myGroups.get(row).size);
          default: return null;
        }
      }

      @Override
      public boolean isCellEditable(int row, int column) {
        return column == 0;
      }

      @Override
      public void setValueAt(Object value, int row, int column) {
        mySelected.set(row, (Boolean)value);
        fireTableCellUpdated(row, column);
      }
    }
  }
}
