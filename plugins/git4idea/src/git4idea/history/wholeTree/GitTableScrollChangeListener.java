/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TableScrollingUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
* @author irengrig
*/
public class GitTableScrollChangeListener implements ChangeListener {
  private final Speedometer mySpeedometer;
  private Timer myTimer;
  private long myRefreshMark;
  private final DetailsCache myDetailsCache;
  private final BigTableTableModel myTableModel;
  private final Runnable myCheckSelection;

  public GitTableScrollChangeListener(final JBTable table,
                                      final DetailsCache detailsCache,
                                      final BigTableTableModel tableModel,
                                      Runnable checkSelection, final Runnable fastListener) {
    myDetailsCache = detailsCache;
    myTableModel = tableModel;
    myCheckSelection = checkSelection;
    mySpeedometer = new Speedometer();
    myRefreshMark = 0;
    myTimer = UIUtil.createNamedTimer("Git table scroll timer",100, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (fastListener != null) {
          fastListener.run();
        }
        final boolean shouldPing = (System.currentTimeMillis() - myRefreshMark) > 300;
        //final boolean shouldPing = false;
        if (((mySpeedometer.getSpeed() < 0.1) && mySpeedometer.hasData()) || shouldPing) {
          myRefreshMark = System.currentTimeMillis();
          if (myCheckSelection != null) {
            myCheckSelection.run();
          }
          mySpeedometer.clear();
          Pair<Integer,Integer> visibleRows = TableScrollingUtil.getVisibleRows(table);
          if (visibleRows.getSecond() < 0) {
            // todo check
            // we cut the table, so leading/trailing compare number of rows, returned by model, with point and get incorrect results
            if (visibleRows.getFirst() < 0) return; // nothing to do
            visibleRows = new Pair<Integer, Integer>(visibleRows.getFirst(), myTableModel.getRowCount() - 1);
          }
          int difference = visibleRows.getSecond() - visibleRows.getFirst();
          int start = Math.max(0, visibleRows.getFirst() - difference);
          int end = Math.min(myTableModel.getRowCount() - 1, visibleRows.getSecond() + difference);
          final MultiMap<VirtualFile,AbstractHash> missing = myTableModel.getMissing(start, end);
          if (! missing.isEmpty()) {
            myDetailsCache.acceptQuestion(missing);
          }
        }
      }
    });
  }

  public void start() {
    myTimer.start();
  }

  public void stop() {
    myTimer.stop();
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    mySpeedometer.event();
  }
}
