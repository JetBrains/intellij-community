package com.intellij.coverage.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.coverage.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * @author ven
 */
public class SwitchCoverageSuiteAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final DefaultListModel model = new DefaultListModel();
    final CoverageDataManagerImpl coverageManager = (CoverageDataManagerImpl)CoverageDataManager.getInstance(project);
    coverageManager.fireBeforeSuiteChosen();
    final CoverageSuite[] suites = coverageManager.getSuites();
    final Map<String, List<CoverageSuite>> grouped = new TreeMap<String, List<CoverageSuite>>();
    for (CoverageSuite suite : suites) {
      final CoverageFileProvider coverageFileProvider = ((CoverageSuiteImpl)suite).getCoverageDataFileProvider();
      if (suite.isValid()) {
        final String sourceProvider =
          coverageFileProvider instanceof DefaultCoverageFileProvider ? ((DefaultCoverageFileProvider)coverageFileProvider)
            .getSourceProvider() : coverageFileProvider.getClass().getName();
        List<CoverageSuite> coverageSuiteList = grouped.get(sourceProvider);
        if (coverageSuiteList == null) {
          coverageSuiteList = new ArrayList<CoverageSuite>();
          grouped.put(sourceProvider, coverageSuiteList);
        }
        coverageSuiteList.add(suite);
      }
    }

    final List<CoverageSuite> firstInGroup = new ArrayList<CoverageSuite>();
    for (String provider : grouped.keySet()) {
      final List<CoverageSuite> toSort = grouped.get(provider);
      if (toSort.isEmpty()) continue;
      Collections.sort(toSort, new Comparator<CoverageSuite>() {
        public int compare(final CoverageSuite s1, final CoverageSuite s2) {
          return s1.getPresentableName().compareToIgnoreCase(s2.getPresentableName());
        }
      });
      for (CoverageSuite suite : toSort) {
        model.addElement(suite);
      }
      firstInGroup.add(toSort.get(0));
    }
    model.addElement(null);

    final JList list = new JList(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new MyCellRenderer(coverageManager) {
      protected boolean hasSeparatorAbove(final CoverageSuite suite) {
        return suite == null || (suite != model.getElementAt(0) && firstInGroup.contains(suite));
      }
    });

    final Runnable chosen = new Runnable(){
      public void run() {
        final CoverageSuite suite = (CoverageSuite)list.getSelectedValue();
        coverageManager.chooseSuite(suite);
      }
    };

    final JBPopup popup = new PopupChooserBuilder(list).
      setTitle(CodeInsightBundle.message("title.popup.show.coverage")).
      setMovable(true).
      setItemChoosenCallback(chosen).
      createPopup();

    list.registerKeyboardAction(
      new RemoveSuiteAction(list, coverageManager, model, popup, chosen),
      KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );

    popup.showCenteredInCurrentWindow(project);
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null && CoverageDataManager.getInstance(project).getSuites().length > 0);
  }

  private static abstract class MyCellRenderer extends ColoredListCellRenderer {
    private final CoverageDataManager myCoverageManager;

    public MyCellRenderer(final CoverageDataManager coverageManager) {
      myCoverageManager = coverageManager;
    }

    protected abstract boolean hasSeparatorAbove(CoverageSuite suite);

    public Component getListCellRendererComponent(final JList list,
                                                  final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
      final Component coloredComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      CoverageSuite suite = (CoverageSuite)value;
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(coloredComponent, BorderLayout.CENTER);
      if (hasSeparatorAbove(suite)) {
        final TitledSeparator titledSeparator = new TitledSeparator();
        titledSeparator.setOpaque(false);
        panel.add(titledSeparator, BorderLayout.NORTH);
      }
      panel.setBackground(UIUtil.getListBackground());
      return panel;
    }

    protected void customizeCellRenderer(final JList list,
                                         final Object value, final int index, final boolean selected, final boolean hasFocus) {
      CoverageSuite suite = (CoverageSuite)value;
      final SimpleTextAttributes attributes = suite == myCoverageManager.getCurrentSuite() ?
                                              SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES :
                                              SimpleTextAttributes.REGULAR_ATTRIBUTES;
      append(suite != null ? suite.getPresentableName() : CodeInsightBundle.message("no.coverage"), attributes);
      if (suite != null) {
        final String date = " ( " + new Date(suite.getLastCoverageTimeStamp()).toString() + " )";
        append(date, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }
  }

  private static class RemoveSuiteAction extends AbstractAction {
    private final JList myList;
    private final CoverageDataManager myCoverageManager;
    private final DefaultListModel myModel;
    private final JBPopup myPopup;
    private final Runnable myChooseSuiteRunnable;

    public RemoveSuiteAction(final JList list, final CoverageDataManager coverageManager,
                             final DefaultListModel model,
                             final JBPopup popup, final Runnable chooseSuite) {
      myList = list;
      myCoverageManager = coverageManager;
      myModel = model;
      myPopup = popup;
      myChooseSuiteRunnable = chooseSuite;
    }

    public void actionPerformed(ActionEvent e) {
      final Object value = myList.getSelectedValue();
      if (value != null) {
        final CoverageSuite suite = (CoverageSuite)value;
        JBPopupFactory.getInstance()
          .createConfirmation(CodeInsightBundle.message("prompt.remove.coverage", suite.getPresentableName()), new Runnable() {
            public void run() {
              myCoverageManager.removeCoverageSuite(suite);
              myModel.removeElement(value);
              if (myModel.getSize() == 1) {
                myPopup.cancel();
                myChooseSuiteRunnable.run();
              }
            }
          }, 0).showInCenterOf(myList);
      }
    }
  }
}
