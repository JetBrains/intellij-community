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
package git4idea.history.wholeTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/26/11
 * Time: 4:11 PM
 */
public class GraphGutter {
  private static final int ourLineWidth = 8;
  private static final int ourInterLineWidth = 2; // todo!
  private static final int ourInterRepoLineWidth = 5;
  public static final int ourIndent = 2;

  private int myHeaderHeight;
  private int myRowHeight;
  private final BigTableTableModel myModel;
  private MyComponent myComponent;
  private JViewport myTableViewPort;
  private JBTable myJBTable;
  private boolean myStarted;
  private static final Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.GraphGutter");
  private PresentationStyle myStyle;

  public GraphGutter(BigTableTableModel model) {
    myModel = model;
    myComponent = new MyComponent();
    myStyle = PresentationStyle.calm;
  }

  public void setHeaderHeight(int headerHeight) {
    //myHeaderHeight = headerHeight;
    myHeaderHeight = 0;
  }

  public void setRowHeight(int rowHeight) {
    myRowHeight = rowHeight;
  }

  public MyComponent getComponent() {
    return myComponent;
  }

  public void setTableViewPort(JViewport tableViewPort) {
    myTableViewPort = tableViewPort;
  }

  public void setJBTable(JBTable jbTable) {
    myJBTable = jbTable;
  }

  public void start() {
    myStarted = true;
  }

  public PresentationStyle getStyle() {
    return myStyle;
  }

  public void setStyle(PresentationStyle style) {
    myStyle = style;
    myComponent.repaint();
  }

  // will lay near but not inside scroll pane
  class MyComponent extends JPanel {

    public static final int diameter = 7;

    MyComponent() {
      setDoubleBuffered(true);
      setBackground(UIUtil.getTableBackground());
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          myJBTable.dispatchEvent(e);
        }
      });
      addMouseWheelListener(new MouseWheelListener() {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          myTableViewPort.dispatchEvent(e);
        }
      });
      setBorder(BorderFactory.createMatteBorder(0,0,1,0, UIUtil.getBorderColor()));
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      if (! myStarted) return preferredSize;
      int totalWires = myModel.getTotalWires();
      List<Integer> wiresGroups = getWiresGroups();
      return new Dimension(endPoint(totalWires, wiresGroups), preferredSize.height);
    }
    
    private int endPoint(final int wireNumber, List<Integer> wiresGroups) {
      int add = 0;
      for (Integer wiresGroup : wiresGroups) {
        if (wiresGroup - 1 <= wireNumber) {
          add += ourInterRepoLineWidth - ourInterLineWidth;
        }
      }
      return (wireNumber + 1) * ourLineWidth + wireNumber * ourInterLineWidth + add + ourIndent;
    }

    private void drawConnectorsFragment(final Graphics g,
                                        final int idxFrom,
                                        final int yOffset,
                                        final Set<Integer> wires,
                                        @Nullable final WireEvent event,
                                        HashSet<Integer> selected, final List<Integer> wiresGroups) {
      int rowYOffset = yOffset;
      for (int i = idxFrom; i < event.getCommitIdx(); i++) {
        for (Integer wire : wires) {
          g.setColor(selected.contains(i) ? Color.white : myStyle.getColorForWire(wire));
          int startXPoint = startPoint(wire, wiresGroups) + ourLineWidth/2;
          doubleLine(g, startXPoint, rowYOffset, startXPoint, rowYOffset + myRowHeight);
        }
        rowYOffset += myRowHeight;
      }

      final int eventWire = myModel.getCorrectedWire(myModel.getCommitAt(event.getCommitIdx()));
      g.setColor(selected.contains(event.getCommitIdx()) ? Color.white : myStyle.getColorForWire(eventWire));
      int eventStartXPoint = startPoint(eventWire, wiresGroups) + ourLineWidth/2;

      final Set<Integer> skip = new HashSet<Integer>();
      if (event.isStart() && event.isEnd()) {
        return;
      }
      if (event.isStart()) {
        skip.add(eventWire);
        doubleLine(g, eventStartXPoint, rowYOffset + myRowHeight / 2, eventStartXPoint, rowYOffset + myRowHeight);
      }
      if (event.isEnd()) {
        skip.add(eventWire);
        doubleLine(g, eventStartXPoint, rowYOffset, eventStartXPoint, rowYOffset + myRowHeight / 2);
      }
      
      
      final int[] commitsStarts = event.getCommitsStarts();
      if (commitsStarts != null) {
        for (int commitsStart : commitsStarts) {
          if (commitsStart == -1) continue;
          final CommitI commitAt = myModel.getCommitAt(commitsStart);
          final int wire = myModel.getCorrectedWire(commitAt);
          int startXPoint = startPoint(wire, wiresGroups) + ourLineWidth/2;
          g.setColor(selected.contains(event.getCommitIdx()) ? Color.white : myStyle.getColorForWire(wire));
          doubleLine(g, startXPoint, rowYOffset + myRowHeight, eventStartXPoint, rowYOffset + myRowHeight / 2);
        }
      }
      final int[] wireEnds = event.getWireEnds();
      if (wireEnds != null) {
        for (int end : wireEnds) {
          if (end == -1) continue;
          final int wire = myModel.getCorrectedWire(myModel.getCommitAt(end));
          skip.add(wire);
          int startXPoint = startPoint(wire, wiresGroups) + ourLineWidth/2;
          g.setColor(selected.contains(event.getCommitIdx()) ? Color.white : myStyle.getColorForWire(wire));
          doubleLine(g, startXPoint, rowYOffset, eventStartXPoint, rowYOffset + myRowHeight / 2);
        }
      }

      for (Integer wire : wires) {
        if (skip.contains(wire)) continue;
        int startXPoint = startPoint(wire, wiresGroups) + ourLineWidth/2;
        g.setColor(selected.contains(event.getCommitIdx()) ? Color.white : myStyle.getColorForWire(wire));
        doubleLine(g, startXPoint, rowYOffset, startXPoint, rowYOffset + myRowHeight);
      }
    }
    
    private void doubleLine(final Graphics g, final int x1, final int y1, final int x2, final int y2) {
      final Color color = g.getColor();
      g.setColor(Color.gray.brighter());
      //g.drawLine(x1 - 1,y1,x2 - 1,y2);
      g.drawLine(x1 + 1, y1, x2 + 1, y2);
      g.setColor(color);
      g.drawLine(x1,y1,x2,y2);
    }

    @Override
    protected void paintComponent(Graphics g) {
      try {
        super.paintComponent(g);
        if (! myStarted) return;

        Graphics graphics = g.create();
        try {
          int height = getHeight();

          // cells
          int yOffset = (int) myTableViewPort.getViewPosition().getY();
          int integerPart = yOffset / myRowHeight;
          int firstCellPart = yOffset - integerPart * myRowHeight;
          int width = getWidth();
          int upBound = firstCellPart == 0 ? 0 : - firstCellPart;
          upBound += myHeaderHeight;
          //int idx = integerPart + (firstCellPart > 0 ? 1 : 0);
          int idx = integerPart;
          int lastIdx = idx + (getHeight()/myRowHeight + 1);
          
          final int[] selectedRows = myJBTable.getSelectedRows();
          drawSelection(graphics, width, upBound, idx, lastIdx, selectedRows);
          final HashSet<Integer> selected = new HashSet<Integer>();
          for (int selectedRow : selectedRows) {
            selected.add(selectedRow);
          }

          // separators
          List<Integer> wiresGroups = getWiresGroups();

          drawRepoBounds(graphics, height, wiresGroups);

          drawConnectors(graphics, lastIdx, upBound, idx, selected, wiresGroups);

          drawPoints(graphics, lastIdx, upBound, idx, selected, wiresGroups);
        } finally {
          graphics.dispose();
        }
      } catch (Exception e) {
        LOG.info(e);
      }
    }

    private List<Integer> getWiresGroups() {
      List<Integer> wiresGroups = myModel.getWiresGroups();
      if (wiresGroups == null) return Collections.emptyList();
      int running = 0;
      for (int i = 0; i < wiresGroups.size(); i++) {
        Integer integer = wiresGroups.get(i);
        wiresGroups.set(i, running + integer);
        running += integer;
      }
      return wiresGroups;
    }

    private void drawSelection(Graphics graphics, int width, int upBound, int idx, int lastIdx, int[] selectedRows) {
      for (int selectedRow : selectedRows) {
        if (selectedRow >= idx && selectedRow <= lastIdx) {
          graphics.setColor(UIUtil.getTableSelectionBackground());
          graphics.fillRect(0, upBound + (selectedRow - idx) * myRowHeight + 1,width,myRowHeight - 1);
        }
      }
    }

    private void drawPoints(Graphics graphics, int lastIdx, int upBound, int idx, HashSet<Integer> selected, List<Integer> wiresGroups) {
      final Color darker = UIUtil.getTableSelectionBackground().darker();
      final Color fill = new Color(176,230,255);
      while (idx <= lastIdx && idx < myModel.getRowCount()) {
        CommitI commitAt = myModel.getCommitAt(idx);
        final boolean contains = selected.contains(idx);

        final boolean isInActiveRoots = myModel.getActiveRoots().contains(commitAt.selectRepository(myModel.getRootsHolder().getRoots()));
        if (! commitAt.holdsDecoration() && isInActiveRoots) {
          int correctedWire = myModel.getCorrectedWire(commitAt);
          int startXPoint = startPoint(correctedWire, wiresGroups);
          graphics.setColor(contains ? Color.white : fill);
          ((Graphics2D) graphics).fillArc(startXPoint + ourLineWidth / 2 - 4, upBound + myRowHeight / 2 - 4, diameter, diameter, 0, 360);
          //graphics.setColor(contains ? Color.white : UIUtil.getTableSelectionBackground().brighter());

          graphics.setColor(contains ? Color.white : UIUtil.getTableSelectionBackground());
          ((Graphics2D) graphics).drawArc(startXPoint + ourLineWidth / 2 - 4 + 1, upBound + myRowHeight / 2 - 4, diameter, diameter, 0, 360);

          if (! contains) {
            graphics.setColor(Color.white);
            ((Graphics2D) graphics).drawArc(startXPoint + ourLineWidth / 2 - 4 + 1, upBound + myRowHeight / 2 - 4 + 1, diameter - 1, diameter - 1, 100, 90);
          }

          graphics.setColor(contains ? Color.white : darker);
          ((Graphics2D) graphics).drawArc(startXPoint + ourLineWidth / 2 - 4, upBound + myRowHeight / 2 - 4, diameter, diameter, 0, 360);

          //graphics.drawString("" + correctedWire, startXPoint, upBound + myRowHeight);
        } else {
          //((Graphics2D) graphics).drawArc(startXPoint + ourLineWidth/2 - 2, upBound + myRowHeight/2 - 2, 4, 4, 0, 360);
          //graphics.drawString("H", 0, upBound + myRowHeight);
        }
        ++ idx;
        upBound += myRowHeight;
      }
    }

    private void drawConnectors(Graphics graphics, int lastIdx, int upBound, int idx, HashSet<Integer> selected, List<Integer> wiresGroups) {
      final Map<VirtualFile,WireEventsIterator> groupIterators = myModel.getGroupIterators(idx);
      for (Map.Entry<VirtualFile, WireEventsIterator> entry : groupIterators.entrySet()) {
        final WireEventsIterator eventsIterator = entry.getValue();
        int idxFrom = idx;
        int yOff = upBound;
        Set<Integer> used = new HashSet<Integer>(eventsIterator.getFirstUsed());
        final Iterator<WireEvent> iterator = eventsIterator.getWireEventsIterator();
        while (iterator.hasNext()) {
          final WireEvent wireEvent = iterator.next();

          if (wireEvent.getCommitIdx() >= idx) {
            drawConnectorsFragment(graphics, idxFrom, yOff, used, wireEvent, selected, wiresGroups);
            int delta = wireEvent.getCommitIdx() + 1 - idxFrom;
            delta = delta < 0 ? 0 : delta;
            yOff += delta * myRowHeight;
            idxFrom = wireEvent.getCommitIdx() + 1;
          }
          // add starts, minus ended
          final int[] wireEnds = wireEvent.getWireEnds();
          if (wireEnds != null) {
            for (int wireEnd : wireEnds) {
              used.remove(Integer.valueOf(myModel.getCorrectedWire(myModel.getCommitAt(wireEnd))));
            }
          }
          if (wireEvent.isEnd()) {
            used.remove(Integer.valueOf(myModel.getCorrectedWire(myModel.getCommitAt(wireEvent.getCommitIdx()))));
          }
          final int[] commitsStarts = wireEvent.getCommitsStarts();
          if (commitsStarts != null) {
            for (int commitsStart : commitsStarts) {
              if (commitsStart == -1) continue;
              used.add(myModel.getCorrectedWire(myModel.getCommitAt(commitsStart)));
            }
          }
          if (wireEvent.isStart() && ! wireEvent.isEnd()) {
            used.add(myModel.getCorrectedWire(myModel.getCommitAt(wireEvent.getCommitIdx())));
          }
          
          if (wireEvent.getCommitIdx() > lastIdx) break;
        }
      }
    }

    private void drawRepoBounds(Graphics graphics, int height, List<Integer> wiresGroups) {
      graphics.setColor(UIUtil.getBorderColor());
      if (wiresGroups.size() > 1) {
        for (int i = 0; i < wiresGroups.size() - 1; i++) {
          Integer integer = wiresGroups.get(i);
          int x1 = integer == 0 ? 0 : endPoint(integer - 1, wiresGroups);
          if (x1 > 0) {
            x1 -= ourInterRepoLineWidth/2 + 1;
          }
          graphics.drawLine(x1, 0, x1, height);
        }
      }
    }

    private int startPoint(int correctedWire, List<Integer> wiresGroups) {
      return correctedWire == 0 ? ourIndent : endPoint(correctedWire - 1, wiresGroups);
    }
  }

  public static enum PresentationStyle {
    multicolour() {
      final Color[] ourColors = {new Color(255,128,0), new Color(0,255,128), new Color(128,0,255), new Color(255,0,0), new Color(0,0,255),
        new Color(128,64,0), new Color(255,0,255), new Color(255,255,0), new Color(0, 255,255)};
      @Override
      public Color getColorForWire(int wire) {
        return ourColors[wire % ourColors.length];
      }
    },
    calm() {
      final Color darker2 = UIUtil.getTableSelectionBackground().darker();
      final Color darker = new Color(255,128,0);
      
      @Override
      public Color getColorForWire(int wire) {
        return (wire/2 % 2 == 1 ? darker : darker2);
      }
    };

    public abstract Color getColorForWire(int wire);
  }
}
