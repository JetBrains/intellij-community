package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.swing_ui.render.GraphCommitCellRender;
import org.hanuna.gitalk.swing_ui.render.PositionUtil;
import org.hanuna.gitalk.swing_ui.render.painters.GraphCellPainter;
import org.hanuna.gitalk.swing_ui.render.painters.SimpleGraphCellPainter;
import org.hanuna.gitalk.ui.DragDropListener;
import org.hanuna.gitalk.ui.UI_Controller;
import org.hanuna.gitalk.ui.tables.GraphCommitCell;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.BorderUIResource;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hanuna.gitalk.swing_ui.render.Print_Parameters.EDGE_FIELD;
import static org.hanuna.gitalk.swing_ui.render.Print_Parameters.HEIGHT_CELL;

/**
 * @author erokhins
 */
public class UI_GraphTable extends JTable {
  private final UI_Controller ui_controller;
  private final GraphCellPainter graphPainter = new SimpleGraphCellPainter();
  private final MouseAdapter mouseAdapter = new MyMouseAdapter();

  private List<Node> myNodesBeingDragged = null;
  private int[] myRowIndicesBeingDragged = null;
  private int[][] selectionHistory = new int[2][];
  private boolean dragged = false;

  public UI_GraphTable(UI_Controller ui_controller) {
    super(ui_controller.getGraphTableModel());
    UIManager.put("Table.focusCellHighlightBorder", new BorderUIResource(new LineBorder(new Color(255, 0, 0, 0))));
    this.ui_controller = ui_controller;
    prepare();
  }

  private void prepare() {
    setTableHeader(null);
    setDefaultRenderer(GraphCommitCell.class, new GraphCommitCellRender(graphPainter));
    setRowHeight(HEIGHT_CELL);
    setShowHorizontalLines(false);
    setIntercellSpacing(new Dimension(0, 0));

    getColumnModel().getColumn(0).setPreferredWidth(700);
    getColumnModel().getColumn(1).setMinWidth(90);
    getColumnModel().getColumn(2).setMinWidth(90);

    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (selectionHistory[0] == null) {
          selectionHistory[0] = getSelectedRows();
        }
        else if (selectionHistory[1] == null) {
          selectionHistory[1] = getSelectedRows();
        }
        else {
          selectionHistory[0] = selectionHistory[1];
          selectionHistory[1] = getSelectedRows();
        }
        int selectedRow = getSelectedRow();
        if (selectedRow >= 0) {
          ui_controller.click(selectedRow);
        }
      }
    });

    addMouseMotionListener(mouseAdapter);
    addMouseListener(mouseAdapter);
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          if (myNodesBeingDragged != null && dragged) {
            ui_controller.getDragDropListener().draggingCanceled(myNodesBeingDragged);
          }
          myNodesBeingDragged = null;
          myRowIndicesBeingDragged = null;
          dragged = false;
        }
      }
    });
  }

  public void jumpToRow(int rowIndex) {
    scrollRectToVisible(getCellRect(rowIndex, 0, false));
    setRowSelectionInterval(rowIndex, rowIndex);
    scrollRectToVisible(getCellRect(rowIndex, 0, false));
  }

  @Override
  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
    // HACK: table changes selection on drag-n-drop actions otherwise
    if (extend) return;
    super.changeSelection(rowIndex, columnIndex, toggle, extend); // TODO
  }

  private class MyMouseAdapter extends MouseAdapter {
    private final Cursor DEFAULT_CURSOR = new Cursor(Cursor.DEFAULT_CURSOR);
    private final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR);

    private GraphPrintCell getGraphPrintCell(MouseEvent e) {
      return PositionUtil.getGraphPrintCell(e, getModel());
    }

    private Node getNode(MouseEvent e) {
      return PositionUtil.getNode(e, getModel());
    }

    @Nullable
    private GraphElement overCell(MouseEvent e) {
      int y = PositionUtil.getYInsideRow(e);
      int x = e.getX();
      GraphPrintCell row = getGraphPrintCell(e);
      return graphPainter.mouseOver(row, x, y);
    }

    @Nullable
    private Node arrowToNode(MouseEvent e) {
      int y = PositionUtil.getYInsideRow(e);
      int x = e.getX();
      GraphPrintCell row = getGraphPrintCell(e);
      SpecialPrintElement printElement = graphPainter.mouseOverArrow(row, x, y);
      if (printElement != null) {
        if (printElement.getType() == SpecialPrintElement.Type.DOWN_ARROW) {
          return printElement.getGraphElement().getEdge().getDownNode();
        }
        else {
          return printElement.getGraphElement().getEdge().getUpNode();
        }
      }
      return null;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 1) {
        Node jumpToNode = arrowToNode(e);
        if (jumpToNode != null) {
          jumpToRow(jumpToNode.getRowIndex());
        }
        GraphElement graphElement = overCell(e);
        ui_controller.click(graphElement);
        if (graphElement == null) {
          ui_controller.click(PositionUtil.getRowIndex(e));
        }
      }
      else {
        int rowIndex = PositionUtil.getRowIndex(e);
        ui_controller.doubleClick(rowIndex);
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      Node jumpToNode = arrowToNode(e);
      if (jumpToNode != null) {
        setCursor(HAND_CURSOR);
      }
      else {
        setCursor(DEFAULT_CURSOR);
      }
      ui_controller.over(overCell(e));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mousePressed(MouseEvent e) {
      dragged = false;
      Node node = getNode(e);
      if (node != null) {

        // HACK: mousePressed changes current selection, so we take previous one if available
        int[] selection = selectionHistory[0] == null ? getSelectedRows() : selectionHistory[0];
        boolean contains = false;
        for (int i : selection) {
          if (i == getSelectedRow()) {
            contains = true;
            break;
          }
        }

        final int[] relevantSelection = contains ? selection : getSelectedRows();
        Arrays.sort(relevantSelection);

        List<Node> commitsBeingDragged = nodes(relevantSelection);
        myNodesBeingDragged = commitsBeingDragged;
        myRowIndicesBeingDragged = relevantSelection;
        ui_controller.getDragDropListener().draggingStarted(commitsBeingDragged);
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (dragged && myNodesBeingDragged != null) {
        handleEvent(e, ui_controller.getDragDropListener().drop(), myNodesBeingDragged);
      }
      dragged = false;
      myNodesBeingDragged = null;
      myRowIndicesBeingDragged = null;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (myNodesBeingDragged == null) return;
      dragged = true;
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          setSelection(myRowIndicesBeingDragged);
        }
      });

      handleEvent(e, ui_controller.getDragDropListener().drag(), myNodesBeingDragged);
    }

    private void handleEvent(MouseEvent e, DragDropListener.Handler handler, List<Node> selectedNodes) {
      Node commit = getNode(e);
      if (commit == null) {
        return;
      }
      int rowIndex = PositionUtil.getRowIndex(e);
      int yOffset = PositionUtil.getYInsideRow(e);

      for (SpecialPrintElement element : getGraphPrintCell(e).getSpecialPrintElements()) {
        if (element.getType() == SpecialPrintElement.Type.COMMIT_NODE) {
          if (PositionUtil.overNode(element.getPosition(), e.getX(), yOffset)) {
            handler.overNode(rowIndex, commit, e, selectedNodes);
            return;
          }
        }
      }

      if (yOffset <= EDGE_FIELD) {
        handler.above(rowIndex, commit, e, selectedNodes);
      }
      else if (yOffset >= HEIGHT_CELL - EDGE_FIELD) {
        handler.below(rowIndex, commit, e, selectedNodes);
      }
      else {
        handler.over(rowIndex, commit, e, selectedNodes);
      }
    }
  }

  private void setSelection(int[] nodesBeingDragged) {
    for (int index : nodesBeingDragged) {
      getSelectionModel().addSelectionInterval(index, index);
    }
  }

  private List<Node> getSelectedNodes() {
    int[] selectedRows = getSelectedRows();
    return nodes(selectedRows);
  }

  private List<Node> nodes(int[] selectedRows) {
    List<Node> result = new ArrayList<Node>();
    Arrays.sort(selectedRows);
    for (int rowIndex : selectedRows) {
      Node node = PositionUtil.getNode(PositionUtil.getGraphPrintCell(getModel(), rowIndex));
      if (node != null) {
        result.add(node);
      }
    }
    return result;
  }

}
