// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.actions;

import com.intellij.CommonBundle;
import com.intellij.featureStatistics.*;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.ui.text.StyledTextPane;
import com.intellij.ide.ui.text.paragraph.TextParagraph;
import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.ide.util.TipUtils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.table.TableView;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;

public final class ShowFeatureUsageStatisticsDialog extends DialogWrapper {
  private static final Comparator<FeatureDescriptor> DISPLAY_NAME_COMPARATOR = Comparator.comparing(FeatureDescriptor::getDisplayName);
  private static final Comparator<FeatureDescriptor> GROUP_NAME_COMPARATOR = Comparator.comparing(ShowFeatureUsageStatisticsDialog::getGroupName);
  private static final Comparator<FeatureDescriptor> USAGE_COUNT_COMPARATOR = Comparator.comparingInt(FeatureDescriptor::getUsageCount);
  private static final Comparator<FeatureDescriptor> LAST_USED_COMPARATOR =
    (fd1, fd2) -> new Date(fd2.getLastTimeUsed()).compareTo(new Date(fd1.getLastTimeUsed()));

  private static final ColumnInfo<FeatureDescriptor, String> DISPLAY_NAME = new ColumnInfo<>(FeatureStatisticsBundle.message("feature.statistics.column.feature")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      return featureDescriptor.getDisplayName();
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return DISPLAY_NAME_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> GROUP_NAME = new ColumnInfo<>(FeatureStatisticsBundle.message("feature.statistics.column.group")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      return getGroupName(featureDescriptor);
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return GROUP_NAME_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> USED_TOTAL = new ColumnInfo<>(FeatureStatisticsBundle.message("feature.statistics.column.usage.count")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      int count = featureDescriptor.getUsageCount();
      return FeatureStatisticsBundle.message("feature.statistics.usage.count", count);
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return USAGE_COUNT_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> LAST_USED = new ColumnInfo<>(FeatureStatisticsBundle.message("feature.statistics.column.last.used")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      long tm = featureDescriptor.getLastTimeUsed();
      if (tm <= 0) return FeatureStatisticsBundle.message("feature.statistics.not.applicable");
      return DateFormatUtil.formatBetweenDates(tm, System.currentTimeMillis());
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return LAST_USED_COMPARATOR;
    }
  };

  private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{DISPLAY_NAME, GROUP_NAME, USED_TOTAL, LAST_USED};

  public ShowFeatureUsageStatisticsDialog(Project project) {
    super(project, true);
    setTitle(FeatureStatisticsBundle.message("feature.statistics.dialog.title"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.featureStatistics.actions.ShowFeatureUsageStatisticsDialog";
  }

  @Override
  public Dimension getInitialSize() {
    return new JBDimension(800, 600);
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getCancelAction(), getHelpAction()};
  }

  @Override
  protected String getHelpId() {
    return "editing.productivityGuide";
  }

  @Override
  protected JComponent createCenterPanel() {
    Splitter splitter = new Splitter(true);
    splitter.setShowDividerControls(true);

    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    ArrayList<FeatureDescriptor> features = new ArrayList<>();
    for (String id : registry.getFeatureIds()) {
      FeatureDescriptor feature = registry.getFeatureDescriptor(id);
      if (feature.isNeedToBeShownInGuide()) {
        TipAndTrickBean tip = TipUtils.getTip(feature);
        if (tip != null && TipUtils.checkTipFileExist(tip)) {
          features.add(feature);
        }
      }
    }
    TableView<FeatureDescriptor> table = new TableView<>(new ListTableModel<>(COLUMNS, features, 0));
    new TableViewSpeedSearch<>(table) {
      @Override
      protected String getItemText(@NotNull FeatureDescriptor element) {
        return element.getDisplayName();
      }
    };

    JPanel controlsPanel = new JPanel(new VerticalFlowLayout());


    Application app = ApplicationManager.getApplication();
    long uptime = System.currentTimeMillis() - app.getStartTime();
    long idleTime = app.getIdleTime();

    final String uptimeS = FeatureStatisticsBundle.message("feature.statistics.application.uptime",
                                                           ApplicationNamesInfo.getInstance().getFullProductName(),
                                                           NlsMessages.formatDurationApproximate(uptime));

    final String idleTimeS = FeatureStatisticsBundle.message("feature.statistics.application.idle.time",
                                                             NlsMessages.formatDurationApproximate(idleTime));

    String labelText = uptimeS + ", " + idleTimeS;
    CompletionStatistics stats = ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getCompletionStatistics();
    if (stats.dayCount > 0 && stats.sparedCharacters > 0) {
      String total = formatCharacterCount(stats.sparedCharacters, true);
      String perDay = formatCharacterCount(stats.sparedCharacters / stats.dayCount, false);
      labelText += "<br>" + IdeBundle.message("label.text.code.completion.saved", total, DateFormatUtil.formatDate(stats.startDate), perDay);
    }

    CumulativeStatistics fstats = ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFixesStats();
    if (fstats.dayCount > 0 && fstats.invocations > 0) {
      labelText += "<br>" + IdeBundle.message("label.text.quick.fixes.saved", fstats.invocations,DateFormatUtil.formatDate(fstats.startDate),fstats.invocations / fstats.dayCount);
    }

    controlsPanel.add(new JLabel(XmlStringUtil.wrapInHtml(labelText)), BorderLayout.NORTH);

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(controlsPanel, BorderLayout.NORTH);
    topPanel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);

    splitter.setFirstComponent(topPanel);

    final StyledTextPane textPane = new StyledTextPane();
    textPane.setBackground(UIUtil.getTextFieldBackground());
    Disposer.register(getDisposable(), textPane);
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(textPane));

    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Collection<FeatureDescriptor> selection = table.getSelection();
        if (selection.isEmpty()) {
          textPane.clear();
        }
        else {
          TipAndTrickBean tip = TipUtils.getTip(selection.iterator().next());
          List<TextParagraph> paragraphs = TipUtils.loadAndParseTip(tip);
          textPane.setParagraphs(paragraphs);
        }
      }
    });

    ScrollingUtil.ensureSelectionExists(table);
    return splitter;
  }

  private static String formatCharacterCount(int count, boolean full) {
    DecimalFormat oneDigit = new DecimalFormat("0.0");
    String result = count > 1024 * 1024 ? oneDigit.format((double)count / 1024 / 1024) + "M" :
               count > 1024 ? oneDigit.format((double)count / 1024) + "K" :
               String.valueOf(count);
    if (full) {
      return IdeBundle.message("label.text.character.count", result);
    }
    return result;
  }

  private static String getGroupName(@NotNull FeatureDescriptor featureDescriptor) {
    final ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    final String groupId = featureDescriptor.getGroupId();
    if (registry != null && groupId != null) {
      final GroupDescriptor groupDescriptor = registry.getGroupDescriptor(groupId);
      return groupDescriptor != null ? groupDescriptor.getDisplayName() : "";
    }
    return "";
  }
}
