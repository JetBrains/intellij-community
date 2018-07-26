/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.AsyncProcessIcon;
import com.sun.jna.platform.FileUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class DeleteOldDirectoriesDialog extends JDialog {
  private JPanel myTopLevelPanel;
  private JLabel myFoundUnusedLabel;
  private JPanel myTableUIPanel;
  private AsyncProcessIcon myBusyAnimationIcon;
  private JButton myDeleteButton;
  private JButton myCancelButton;

  private List<IdeDirectoriesInfo> myDirsInfoList;
  private JCheckBox myAggregateCheckBox;
  private List<JCheckBox> myCheckBoxList;
  private List<JLabel> myLastUsedLabelList;
  private List<JLabel> mySizeLabelList;
  private List<SwingWorker> mySizeCalculationWorkers;
  private SwingWorker myDeletionWorker;

  public DeleteOldDirectoriesDialog(List<IdeDirectoriesInfo> dirsInfoList) {
    setLabelTexts();
    createTableUI(dirsInfoList);
    setLastUsedLabels();
    addListeners();
    setDialogProperties();
    runSizeCalculationWorkers();
  }

  // This method is needed, because there is at least one component with custom-create set to true.
  private void createUIComponents() {
    myBusyAnimationIcon = new AsyncProcessIcon("delete old directories");
    myBusyAnimationIcon.setVisible(false);
  }

  private void setLabelTexts() {
    String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    myFoundUnusedLabel.setText(html(ApplicationBundle.message("label.found.unused", productName)));
    setPreferredSize(myFoundUnusedLabel, 600, 2);
  }

  private static String html(String text) {
    return "<html><body><p style=\"margin-top: 0\">" + text + "</p></body></html>";
  }

  private static String bold(String text) {
    return "<b>" + text + "</b>";
  }

  private static String linebreak(String text) {
    return text.replace("\n", "<br>");
  }

  private static void setPreferredSize(JComponent component, int width, int numberOfLines) {
    int lineHeight = component.getFontMetrics(component.getFont()).getHeight();
    component.setPreferredSize(new Dimension(width, numberOfLines * lineHeight));
  }

  private void createTableUI(List<IdeDirectoriesInfo> dirsInfoList) {
    myDirsInfoList = dirsInfoList;

    final int size = myDirsInfoList.size();
    myCheckBoxList = new ArrayList<>(size);
    myLastUsedLabelList = new ArrayList<>(size);
    mySizeLabelList = new ArrayList<>(size);

    final int rowCount = myDirsInfoList.size() + 1;
    final int columnCount = 5;
    myTableUIPanel.setLayout(new GridLayoutManager(rowCount, columnCount));
    GridConstraints constraints = new GridConstraints();
    constraints.setAnchor(GridConstraints.ANCHOR_WEST);
    constraints.setFill(GridConstraints.FILL_NONE);
    constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);

    // column 0 aggregate checkbox
    myAggregateCheckBox = new JCheckBox();
    constraints.setRow(0);
    constraints.setColumn(0);
    myTableUIPanel.add(myAggregateCheckBox, constraints);

    // column 1 title
    final JLabel directoryTitle = new JLabel(html(bold(ApplicationBundle.message("label.column.title.directory"))), SwingConstants.LEADING);
    setPreferredSize(directoryTitle, 270, 1);
    constraints.setRow(0);
    constraints.setColumn(1);
    myTableUIPanel.add(directoryTitle, constraints);

    // column 2 title
    final JLabel lastUsedTitle = new JLabel(html(bold(ApplicationBundle.message("label.column.title.lastused"))), SwingConstants.LEADING);
    setPreferredSize(lastUsedTitle, 100, 1);
    constraints.setRow(0);
    constraints.setColumn(2);
    myTableUIPanel.add(lastUsedTitle, constraints);

    // column 3 title
    final JLabel sizeTitle = new JLabel(html(bold(ApplicationBundle.message("label.column.title.size"))), SwingConstants.TRAILING);
    setPreferredSize(sizeTitle, 90, 1);
    constraints.setRow(0);
    constraints.setColumn(3);
    myTableUIPanel.add(sizeTitle, constraints);

    for (int i = 0; i < size; i++) {
      IdeDirectoriesInfo dirsInfo = myDirsInfoList.get(i);
      final int row = i + 1;

      // column 0
      final JCheckBox checkBox = new JCheckBox();
      myCheckBoxList.add(checkBox);
      constraints.setRow(row);
      constraints.setColumn(0);
      myTableUIPanel.add(checkBox, constraints);

      // column 1
      final JLabel directoryLabel = new JLabel(dirsInfo.getTopLevelDirectory().getDescriptor(), SwingConstants.LEADING);
      setPreferredSize(directoryLabel, 270, 1);
      setDirectoryLabelToolTip(directoryLabel, dirsInfo);
      constraints.setRow(row);
      constraints.setColumn(1);
      myTableUIPanel.add(directoryLabel, constraints);
      directoryLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (checkBox.isEnabled()) {
            checkBox.setSelected(!checkBox.isSelected());
          }
        }
      });

      // column 2
      final JLabel lastUsedLabel = new JLabel("", SwingConstants.LEADING);
      setPreferredSize(lastUsedLabel, 100, 1);
      myLastUsedLabelList.add(lastUsedLabel);
      constraints.setRow(row);
      constraints.setColumn(2);
      myTableUIPanel.add(lastUsedLabel, constraints);

      // column 3
      final JLabel sizeLabel = new JLabel("", SwingConstants.TRAILING);
      setPreferredSize(sizeLabel, 90, 1);
      mySizeLabelList.add(sizeLabel);
      constraints.setRow(row);
      constraints.setColumn(3);
      myTableUIPanel.add(sizeLabel, constraints);
    }

    GridConstraints spacerConstraints = new GridConstraints();
    spacerConstraints.setRow(0);
    spacerConstraints.setColumn(columnCount - 1);
    spacerConstraints.setRowSpan(size);
    spacerConstraints.setFill(GridConstraints.FILL_HORIZONTAL);
    spacerConstraints.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
    spacerConstraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    myTableUIPanel.add(new Spacer(), spacerConstraints);
  }

  private static void setDirectoryLabelToolTip(JLabel directoryLabel, IdeDirectoriesInfo dirsInfo) {
    String toolTip = dirsInfo.getTopLevelDirectory().getDirectory() != null
                     ? dirsInfo.getTopLevelDirectory().getDirectory().toString()
                     : dirsInfo.getSubDirectories()
                               .stream()
                               .map(dirInfo -> dirInfo.getDirectory().toString())
                               .collect(Collectors.joining("\n"));
    directoryLabel.setToolTipText(html(linebreak(toolTip)));
  }

  private void setLastUsedLabels() {
    LocalDate today = LocalDate.now();

    for (int i = 0; i < myDirsInfoList.size(); i++) {
      Instant lastUsedTime = myDirsInfoList.get(i).calculateLastUsedTime();
      LocalDate lastUsedDate = LocalDateTime.ofInstant(lastUsedTime, ZoneId.systemDefault()).toLocalDate();
      Period periodSinceLastUsed = Period.between(lastUsedDate, today);
      myLastUsedLabelList.get(i).setText(formatPeriod(periodSinceLastUsed));
    }
  }

  /**
   * Formats Period as "Never", "15 months ago", "1 month ago", "3 weeks ago", "1 week ago",
   * "4 days ago", "Yesterday", or "Today".
   *
   * @param period A Period to format.
   * @return Formatted string representing the Period.
   */
  private static String formatPeriod(Period period) {
    final int months = 12 * period.getYears() + period.getMonths();
    final int weeks = months == 0 ? period.getDays() / 7 : 0;
    final int days = months == 0 ? period.getDays() % 7 : 0;

    if (months > 0) {
      return months > 300 ? ApplicationBundle.message("label.format.period.never") :
             months > 1 ? ApplicationBundle.message("label.format.period.months.ago", months)
                        : ApplicationBundle.message("label.format.period.month.ago", months);
    }

    if (weeks > 0) {
      return weeks > 1 ? ApplicationBundle.message("label.format.period.weeks.ago", weeks)
                       : ApplicationBundle.message("label.format.period.week.ago", weeks);
    }

    if (days > 0) {
      return days > 1 ? ApplicationBundle.message("label.format.period.days.ago", days)
                      : ApplicationBundle.message("label.format.period.yesterday");
    }

    return ApplicationBundle.message("label.format.period.today");
  }

  private void addListeners() {
    myAggregateCheckBox.addItemListener(this::aggregateCheckBoxStateChanged);
    myCheckBoxList.forEach(cb -> cb.addItemListener(this::individualCheckBoxStateChanged));

    myDeleteButton.addActionListener(e -> onDelete());
    myCancelButton.addActionListener(e -> onCancel());

    // call onSkip() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    // call onSkip() on ESCAPE
    myTopLevelPanel.registerKeyboardAction(e -> onCancel(),
                                           KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                           JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void aggregateCheckBoxStateChanged(ItemEvent e) {
    if (e.getStateChange() == ItemEvent.SELECTED) {
      myCheckBoxList.forEach(cb -> cb.setSelected(true));
    }
    else {
      myCheckBoxList.forEach(cb -> cb.setSelected(false));
    }
  }

  private void individualCheckBoxStateChanged(ItemEvent e) {
    final long selectedCount = myCheckBoxList.stream()
                                             .filter(cb -> cb.isSelected())
                                             .count();
    if (selectedCount == myCheckBoxList.size()) {
      myAggregateCheckBox.setSelected(true);
    } else if (selectedCount == 0) {
      myAggregateCheckBox.setSelected(false);
    }
  }

  private void setDialogProperties() {
    String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    setTitle(ApplicationBundle.message("title.delete.unused.directories", productName));
    setModalityType(ModalityType.APPLICATION_MODAL);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myTopLevelPanel);
    getRootPane().setDefaultButton(myDeleteButton);

    pack();
    setLocationRelativeTo(null);
  }

  private void onDelete() {
    myAggregateCheckBox.setEnabled(false);
    myCheckBoxList.forEach(cb -> cb.setEnabled(false));
    myDeleteButton.setEnabled(false);
    myBusyAnimationIcon.setVisible(true);
    cancelSizeCalculationWorkers();
    runDeletionWorker();
  }

  private void onCancel() {
    cancelSizeCalculationWorkers();
    cancelDeletionWorker();
    close();
  }

  private void close() {
    if (myBusyAnimationIcon != null) {
      myBusyAnimationIcon.setVisible(false);
      Disposer.dispose(myBusyAnimationIcon);
      myBusyAnimationIcon = null;
    }
    dispose();
  }

  private void runSizeCalculationWorkers() {
    final int size = myDirsInfoList.size();
    mySizeCalculationWorkers = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      final IdeDirectoriesInfo dirsInfo = myDirsInfoList.get(i);
      final JLabel sizeLabel = mySizeLabelList.get(i);
      final Iterable<Path> directories = dirsInfo.getSubDirectories()
                                                 .stream()
                                                 .map(subDir -> subDir.getDirectory())
                                                 .collect(Collectors.toList());
      mySizeCalculationWorkers.add(new SizeCalculationWorker(directories, sizeLabel));
    }
    mySizeCalculationWorkers.forEach(worker -> worker.execute());
  }

  private void cancelSizeCalculationWorkers() {
    if (mySizeCalculationWorkers != null) {
      mySizeCalculationWorkers.forEach(worker -> worker.cancel(true));
    }
  }

  /**
   * Background worker for computing and displaying the total size in bytes of a list of directories.
   */
  private static class SizeCalculationWorker extends SwingWorker<Long, Long> {

    private final Iterable<Path> myDirectories;
    private final JLabel mySizeLabel;

    public SizeCalculationWorker(Iterable<Path> directories, JLabel sizeLabel) {
      myDirectories = directories;
      mySizeLabel = sizeLabel;
    }

    @Override
    protected Long doInBackground() throws Exception {
      SizeCalculator sizeCalculator = new SizeCalculator();
      for (Path directory : myDirectories) {
        Files.walkFileTree(directory, sizeCalculator);
      }
      return sizeCalculator.getTotalSize();
    }

    @Override
    protected void process(List<Long> chunks) {
      long totalSize = chunks.get(chunks.size() - 1);
      mySizeLabel.setText(formatSize(totalSize));
    }

    @Override
    protected void done() {
      try {
        long totalSize = get();
        mySizeLabel.setText(formatSize(totalSize));
      }
      catch (InterruptedException | ExecutionException ignored) {
      }
    }

    private static String formatSize(long size) {
      if (size <= 0) {
        return "0 MB";
      }
      if (size <= 1024 * 1024) {
        return "1 MB";
      }
      return String.format("%d MB", size / (1024 * 1024));
    }

    private class SizeCalculator implements FileVisitor<Path> {

      private long totalSize = 0;

      public long getTotalSize() {
        return totalSize;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        totalSize += attrs.size();
        return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        publish(totalSize);
        return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
      }
    }
  }

  private void runDeletionWorker() {
    final List<IdeDirectoriesInfo> deleteDirsInfoList = new ArrayList<>();
    for (int i = 0; i < myDirsInfoList.size(); i++) {
      if (myCheckBoxList.get(i).isSelected()) {
        deleteDirsInfoList.add(myDirsInfoList.get(i));
      }
    }

    myDeletionWorker = new DeletionWorker(deleteDirsInfoList);

    myDeletionWorker.addPropertyChangeListener(event -> {
      if (event.getPropertyName().equals("state")
          && event.getNewValue().equals(DeletionWorker.StateValue.DONE)) {
        close();
      }
    });

    myDeletionWorker.execute();
  }

  private void cancelDeletionWorker() {
    if (myDeletionWorker != null) {
      myDeletionWorker.cancel(true);
    }
  }

  /**
   * Background worker for recursively deleting directories mentioned in a list of infos on IDE directories.
   */
  private static class DeletionWorker extends SwingWorker<Void, Void> {
    private final List<IdeDirectoriesInfo> myDeleteDirsInfoList;

    public DeletionWorker(@NotNull List<IdeDirectoriesInfo> deleteDirsInfoList) {
      myDeleteDirsInfoList = deleteDirsInfoList;
    }

    @Override
    protected Void doInBackground() {
      for (IdeDirectoriesInfo dirsInfo : myDeleteDirsInfoList) {
        for (IdeDirectoriesInfo.DirectoryInfo subDirInfo : dirsInfo.getSubDirectories()) {
          if (isCancelled()) {
            return null;
          }
          deleteDirectory(subDirInfo.getDirectory());
        }
        deleteDirectory(dirsInfo.getTopLevelDirectory().getDirectory());
      }
      return null;
    }
  }

  private static void deleteDirectory(Path directory) {
    if (directory == null
        || !directory.isAbsolute()
        || Files.isSymbolicLink(directory)
        || !Files.isDirectory(directory)) {
      return;
    }

    try {
      if (FileUtils.getInstance().hasTrash()) {
        FileUtils.getInstance().moveToTrash(new File[] { directory.toFile() });
      } else {
        MoreFiles.deleteRecursively(directory, RecursiveDeleteOption.ALLOW_INSECURE);
      }
    }
    catch (IOException ignored) {
    }
  }
}
