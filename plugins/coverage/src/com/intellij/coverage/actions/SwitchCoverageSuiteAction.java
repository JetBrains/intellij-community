package com.intellij.coverage.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.coverage.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author ven
 */
public class SwitchCoverageSuiteAction extends AnAction {
  public static final Icon CLEAN_ICON = IconLoader.getIcon("/actions/clean.png");

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final CoverageDataManager coverageManager = CoverageDataManager.getInstance(project);
    final ArrayList<CoverageSuite> model = new ArrayList<CoverageSuite>();
    final List<CoverageSuite> firstInGroup = new ArrayList<CoverageSuite>();
    fillCoverageSuites(model, firstInGroup, coverageManager);
    final ListPopupImpl listPopup = new DeletableItemsListPopup(new CoverageSuitesListPopupStep(model, coverageManager)){

      @Override
      protected void removeSelectedItem(final JList list) {
        final Object value = list.getSelectedValue();
        if (value != null) {
          final CoverageSuite suite = (CoverageSuite)value;
          JBPopupFactory.getInstance()
            .createConfirmation(CodeInsightBundle.message("prompt.remove.coverage", suite.getPresentableName()), new Runnable() {
              public void run() {
                coverageManager.removeCoverageSuite(suite);
                ((ListPopupModel)list.getModel()).deleteItem(value);
              }
            }, 0).showInCenterOf(list);
        }
      }

      @Override
      protected ListCellRenderer getListElementRenderer() {
        return new MyCellRenderer(coverageManager) {
          @Override
          protected boolean hasSeparatorAbove(CoverageSuite suite) {
            return suite == null || (suite != model.get(0) && firstInGroup.contains(suite));
          }
        };
      }
    };

    listPopup.showCenteredInCurrentWindow(project);
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null && CoverageDataManager.getInstance(project).getSuites().length > 0);
  }

  private static void fillCoverageSuites(final ArrayList<CoverageSuite> model,
                                         final List<CoverageSuite> firstInGroup,
                                         final CoverageDataManager coverageManager) {
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


    for (String provider : grouped.keySet()) {
      final List<CoverageSuite> toSort = grouped.get(provider);
      if (toSort.isEmpty()) continue;
      Collections.sort(toSort, new Comparator<CoverageSuite>() {
        public int compare(final CoverageSuite s1, final CoverageSuite s2) {
          return s1.getPresentableName().compareToIgnoreCase(s2.getPresentableName());
        }
      });
      for (CoverageSuite suite : toSort) {
        model.add(suite);
      }
      firstInGroup.add(toSort.get(0));
    }
    model.add(null);
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
      setPaintFocusBorder(false);
      final CoverageSuite suite = (CoverageSuite)value;
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(coloredComponent, BorderLayout.CENTER);
      if (hasSeparatorAbove(suite)) {
        final TitledSeparator titledSeparator = new TitledSeparator();
        titledSeparator.setOpaque(false);
        panel.add(titledSeparator, BorderLayout.NORTH);
      }
      if (isSelected && suite != null) {
        final JLabel label = new JLabel(CLEAN_ICON);
        label.setOpaque(true);
        label.setBackground(UIUtil.getListSelectionBackground());
        panel.add(label, BorderLayout.EAST);
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
        final String date = " (" + DateFormatUtil.formatDate(new Date(), new Date(suite.getLastCoverageTimeStamp())) + ")";
        append(date, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension preferredSize = super.getPreferredSize();
      return new Dimension(preferredSize.width + CLEAN_ICON.getIconWidth(), preferredSize.height);
    }
  }

  private static class CoverageSuitesListPopupStep extends BaseListPopupStep<CoverageSuite> {
    private final CoverageDataManager myCoverageDataManager;

    public CoverageSuitesListPopupStep(List<CoverageSuite> aValues, CoverageDataManager coverageDataManager) {
      super(CodeInsightBundle.message("title.popup.show.coverage"), aValues);
      myCoverageDataManager = coverageDataManager;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public PopupStep onChosen(CoverageSuite selectedValue, boolean finalChoice) {
      ((CoverageDataManagerImpl)myCoverageDataManager).fireBeforeSuiteChosen();
      myCoverageDataManager.chooseSuite(selectedValue);
      return FINAL_CHOICE;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
      return false;
    }

    @NotNull
    @Override
    public String getTextFor(CoverageSuite suite) {
      if (suite != null) {
        return suite.getPresentableName();
      }
      return CodeInsightBundle.message("no.coverage");
    }
  }

  private static abstract class DeletableItemsListPopup extends ListPopupImpl {

    public DeletableItemsListPopup(ListPopupStep step) {
      super(step);
      setAdText("Press DEL to delete");
    }

    @Override
    protected JComponent createContent() {
      final JList content = (JList)super.createContent();
      content.registerKeyboardAction(
        new AbstractAction() {
          public void actionPerformed(ActionEvent e) {
            removeSelectedItem(content);
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
      return content;
    }

    @Override
    public void handleSelect(final boolean handleFinalChoices, final InputEvent e) {
      if (!handleFinalChoices) {
        removeSelectedItem((JList)e.getComponent());
        disposeAllParents(e);
        return;
      }
      super.handleSelect(handleFinalChoices, e);
    }

    @Override
    protected boolean handleFinalChoices(MouseEvent e, Object selectedValue, ListPopupStep<Object> listStep) {
      if (selectedValue != null) {
        final Rectangle bounds = getCellBounds(getSelectedIndex());
        if (e.getPoint().getX() > bounds.width + bounds.getX() - CLEAN_ICON.getIconWidth()) {
          return false;
        }
      }
      return true;
    }

    protected abstract void removeSelectedItem(final JList list);

    @Override
    protected ListCellRenderer getListElementRenderer() {
      return new PopupListElementRenderer(this) {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          final Component component = super.getListCellRendererComponent(list, value, index, isSelected,
                                                                         cellHasFocus);
          final JPanel panel = new JPanel(new BorderLayout());
          panel.add(component, BorderLayout.CENTER);
          if (isSelected) {
            final JLabel label = new JLabel(CLEAN_ICON);
            label.setOpaque(true);
            label.setBackground(UIUtil.getListSelectionBackground());
            panel.add(label, BorderLayout.EAST);
          }
          panel.setBackground(UIUtil.getListBackground());
          return panel;
        }
      };
    }
  }
}
