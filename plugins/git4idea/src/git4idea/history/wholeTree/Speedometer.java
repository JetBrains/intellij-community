/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.util.LinkedList;

/**
 * @author irengrig
 */
public class Speedometer {
  private final int myHistorySize;
  private final int myMillisInterval;
  private final LinkedList<Long> myEvents;

  public Speedometer() {
    this(-1, -1);
  }

  public Speedometer(int historySize, int millisInterval) {
    myHistorySize = historySize == -1 ? 20 : historySize;
    myMillisInterval = millisInterval == -1 ? 500 : millisInterval;
    myEvents = new LinkedList<Long>();
  }

  public void event() {
    while (myEvents.size() >= myHistorySize) {
      myEvents.removeLast();
    }
    myEvents.addFirst(System.currentTimeMillis());
  }

  // events per 100ms during last myMillisInterval OR last myHistorySize
  public double getSpeed() {
    if (myEvents.isEmpty()) return 0;

    final long current = System.currentTimeMillis();
    final long boundary = current - myMillisInterval;
    int cnt = 0;
    final long end = myEvents.getFirst();
    long last = end;
    for (Long event : myEvents) {
      if (cnt > myHistorySize) break;
      if (event < boundary) break;

      ++ cnt;
      last = event;
    }
    if (cnt == 0) return 0;
    return ((double) end - last) / (cnt * 100);
  }

  public void clear() {
    myEvents.clear();
  }

  public boolean hasData() {
    return ! myEvents.isEmpty();
  }
}
