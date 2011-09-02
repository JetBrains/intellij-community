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

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/26/11
 * Time: 4:11 PM
 */
public class GraphGutter {
  private static final int ourLineWidth = 10;
  private static final int ourInterLineWidth = 1;
  
  private int myHeaderHeight;
  private int myRowHeight;
  private final BigTableTableModel myModel;
  private MyComponent myComponent;
  private JViewport myTableViewPort;
  private JBTable myJBTable;
  private boolean myStarted;

  public GraphGutter(BigTableTableModel model) {
    myModel = model;
    myComponent = new MyComponent();
  }

  public void setHeaderHeight(int headerHeight) {
    myHeaderHeight = headerHeight;
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

  public void setJBTable(JBTable JBTable) {
    myJBTable = JBTable;
  }

  public void start() {
    myStarted = true;
  }

  // will lay near but not inside scroll pane
  class MyComponent extends JPanel {
    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      if (! myStarted) return preferredSize;
      int totalWires = myModel.getTotalWires();
      return new Dimension(endPoint(totalWires), preferredSize.height);
    }
    
    private int endPoint(final int wireNumber) {
      return (wireNumber + 1) * ourLineWidth + wireNumber * ourInterLineWidth;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (! myStarted) return;

      Graphics graphics = g.create();
      try {
        int height = getHeight();

        // separators
        List<Integer> wiresGroups = myModel.getWiresGroups();
        int running = 0;
        if (wiresGroups.size() > 1) {
          for (int i = 0; i < wiresGroups.size() - 1; i++) {
            Integer integer = wiresGroups.get(i);
            int x1 = (running + integer) == 0 ? 0 : endPoint(running + integer - 1);
            graphics.setColor(UIUtil.getBorderColor());
            graphics.drawLine(x1, 0, x1, height);
            running += integer;
          }
        }
        // cells
        int yOffset = (int) myTableViewPort.getViewPosition().getY();
        int integerPart = yOffset / myRowHeight;
        int firstCellPart = yOffset - integerPart * myRowHeight;

        if (firstCellPart > 0) {
          drawFirst(graphics, firstCellPart, integerPart);
        }
        int width = getWidth();
        int upBound = firstCellPart == 0 ? 0 : myRowHeight - firstCellPart;  // todo +-
        upBound += myHeaderHeight;
        int idx = integerPart + (firstCellPart > 0 ? 1 : 0);

        BigTableTableModel.WiresGroupIterator groupIterator;
        try {
          groupIterator = myModel.getGroupIterator(idx);
        } catch (Exception e) {
          return;
          //
          // TODO remove!!!
        }
        List<Integer> firstUsed = groupIterator.getFirstUsed();
        while (upBound < height && idx < myModel.getRowCount()) {
          CommitI commitAt = myModel.getCommitAt(idx);

          WireEvent eventForRow = groupIterator.getEventForRow(idx);
          if (eventForRow != null) {
            //todo minus, plus..
          }
          // todo temp out
          /*for (Integer i : firstUsed) {
            int start = startPoint(i);
            graphics.setColor(UIUtil.getBorderColor());
            graphics.drawRect(start - 1, upBound, start + 1, upBound + myRowHeight);
          }*/

          if (! commitAt.holdsDecoration()) {
            int correctedWire = myModel.getCorrectedWire(commitAt);
            int startXPoint = startPoint(correctedWire);
            graphics.setColor(Color.black);
            ((Graphics2D) graphics).drawArc(startXPoint + ourLineWidth / 2 - 4, upBound + myRowHeight / 2 - 4, 8, 8, 0, 360);
            graphics.setColor(correctedWire == 0 ? Color.red : Color.yellow);
            ((Graphics2D) graphics).fillArc(startXPoint + ourLineWidth / 2 - 4, upBound + myRowHeight / 2 - 4, 8, 8, 0, 360);
            //graphics.drawString("" + correctedWire, startXPoint, upBound + myRowHeight);
          } else {
            graphics.setColor(Color.black);
            //((Graphics2D) graphics).drawArc(startXPoint + ourLineWidth/2 - 2, upBound + myRowHeight/2 - 2, 4, 4, 0, 360);
            //graphics.drawString("H", 0, upBound + myRowHeight);
          }
          graphics.setColor(UIUtil.getBorderColor());
          graphics.drawLine(0, upBound, width, upBound);
          ++ idx;
          upBound += myRowHeight;
        }
      } finally {
        graphics.dispose();
      }
    }

    private int startPoint(int correctedWire) {
      return correctedWire == 0 ? 0 : endPoint(correctedWire - 1);
    }

    private void drawFirst(Graphics graphics, int firstCellPart, int idx) {
      //To change body of created methods use File | Settings | File Templates.
    }
  }
}
