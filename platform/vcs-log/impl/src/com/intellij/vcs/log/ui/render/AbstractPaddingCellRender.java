package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class AbstractPaddingCellRender extends ColoredTableCellRenderer {

  public static final Color MARKED_BACKGROUND = new Color(200, 255, 250);

  @NotNull protected final RefPainter myRefPainter;
  @NotNull private final IssueLinkRenderer myIssueLinkRenderer;
  @Nullable private Object myValue;

  protected AbstractPaddingCellRender(@NotNull Project project, VcsLogColorManager colorManager) {
    myRefPainter = new RefPainter(colorManager, false);
    myIssueLinkRenderer = new IssueLinkRenderer(project, this);
  }

  protected abstract int getLeftPadding(JTable table, Object value);

  @NotNull
  protected abstract String getCellText(@Nullable Object value);

  protected abstract void additionPaint(Graphics g, Object value);

  public static boolean isMarked(@Nullable Object value) {
    if (!(value instanceof GraphCommitCell)) {
      return false;
    }
    GraphCommitCell cell = (GraphCommitCell)value;
    for (SpecialPrintElement printElement : cell.getPrintCell().getSpecialPrintElements()) {
      if (printElement.getType() == SpecialPrintElement.Type.COMMIT_NODE && printElement.isMarked()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void customizeCellRenderer(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value == null) {
      return;
    }
    myValue = value;
    append("");
    appendFixedTextFragmentWidth(getLeftPadding(table, value));
    myIssueLinkRenderer.appendTextWithLinks(getCellText(value));
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    additionPaint(g, myValue);
  }

  protected void drawRefs(@NotNull Graphics2D g2, @NotNull Collection<VcsRef> refs, int padding) {
    myRefPainter.drawLabels(g2, collectLabelsForRefs(refs), padding);
  }

  @NotNull
  private static Map<String, Color> collectLabelsForRefs(@NotNull Collection<VcsRef> refs) {
    List<VcsRef> branches = getBranches(refs);
    Collection<VcsRef> tags = ContainerUtil.subtract(refs, branches);
    return getLabelsForRefs(branches, tags);
  }

  protected int calcRefsPadding(@NotNull Collection<VcsRef> refs, @NotNull Graphics2D g2) {
    return myRefPainter.padding(collectLabelsForRefs(refs).keySet(), g2);
  }

  @NotNull
  private static Map<String, Color> getLabelsForRefs(@NotNull List<VcsRef> branches, @NotNull Collection<VcsRef> tags) {
    Map<String, Color> labels = ContainerUtil.newLinkedHashMap();
    for (VcsRef branch : branches) {
      labels.put(branch.getName(), branch.getType().getBackgroundColor());
    }
    if (!tags.isEmpty()) {
      VcsRef firstTag = tags.iterator().next();
      Color color = firstTag.getType().getBackgroundColor();
      if (tags.size() > 1) {
        labels.put(firstTag.getName() + " +", color);
      }
      else {
        labels.put(firstTag.getName(), color);
      }
    }
    return labels;
  }

  private static List<VcsRef> getBranches(Collection<VcsRef> refs) {
    return ContainerUtil.filter(refs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isBranch();
      }
    });
  }
}
