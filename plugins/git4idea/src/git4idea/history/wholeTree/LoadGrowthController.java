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

import java.util.HashMap;
import java.util.Map;

/**
 * @author irengrig
 */
public class LoadGrowthController {
  private int myCnt;
  private final Map<Integer, Long> myPassedTimeMap;
  private final Object myLock;

  public LoadGrowthController() {
    myPassedTimeMap = new HashMap<Integer, Long>();
    myLock = new Object();
    myCnt = 0;
  }

  public void reset() {
    synchronized (myLock) {
      myPassedTimeMap.clear();
      myCnt = 0;
    }
  }

  public ID getId() {
    synchronized (myLock) {
      int cnt = myCnt;
      ++ myCnt;
      myPassedTimeMap.put(cnt, -1L);
      return new ID(cnt, this);
    }
  }

  private void registerTime(final ID id, final long time) {
    synchronized (myLock) {
      myPassedTimeMap.put(id.getId(), time);
    }
  }

  private void finished(final ID id) {
    synchronized (myLock) {
      myPassedTimeMap.remove(id.getId());
    }
  }

  public boolean isEverybodyLoadedMoreThan(final long time) {
    synchronized (myLock) {
      for (Long aLong : myPassedTimeMap.values()) {
        if (aLong == -1 || aLong > time) return false;
      }
      return true;
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myPassedTimeMap.isEmpty();
    }
  }

  public static class ID {
    private final int myId;
    private final LoadGrowthController myController;

    private ID(int id, final LoadGrowthController controller) {
      myId = id;
      myController = controller;
    }

    private int getId() {
      return myId;
    }

    public void finished() {
      myController.finished(this);
    }

    public void registerTime(long time) {
      myController.registerTime(this, time);
    }
  }
}
