// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;

/**
 * @author Sergey Evdokimov
 */
@State(
    name = "mvcRunTargetHistory",
    storages = @Storage("other.xml")
)
public class MvcRunTargetHistoryService implements PersistentStateComponent<String[]> {

  private static final int MAX_HISTORY_LENGTH = 20;

  private final LinkedList<String> myHistory = new LinkedList<>();

  private String myVmOptions = "";

  @Override
  public String[] getState() {
    synchronized (myHistory) {
      if (myHistory.isEmpty() && StringUtil.isEmpty(myVmOptions)) {
        return ArrayUtilRt.EMPTY_STRING_ARRAY;
      }

      String[] res = new String[myHistory.size() + 1];
      res[0] = '#' + myVmOptions;
      int i = 1;
      for (String s : myHistory) {
        res[i++] = s;
      }

      return res;
    }
  }

  @NotNull
  public String getVmOptions() {
    synchronized (myHistory) {
      return myVmOptions;
    }
  }

  @Override
  public void loadState(String @NotNull [] state) {
    synchronized (myHistory) {
      myHistory.clear();
      int start = 0;
      if (state.length > 0 && state[0].charAt(0) == '#') {
        myVmOptions = state[0].substring(1);
        start = 1;
      }
      else {
        myVmOptions = "";
      }

      for (int i = start; i < state.length; i++) {
        myHistory.add(state[i].trim());
      }
    }
  }

  public String[] getHistory() {
    synchronized (myHistory) {
      return ArrayUtilRt.toStringArray(myHistory);
    }
  }

  public static MvcRunTargetHistoryService getInstance() {
    return ServiceManager.getService(MvcRunTargetHistoryService.class);
  }

  public void addCommand(@NotNull String command, @NotNull String vmOptions) {
    command = command.trim();

    synchronized (myHistory) {
      myVmOptions = vmOptions;

      myHistory.remove(command);
      myHistory.addFirst(command);
      if (myHistory.size() > MAX_HISTORY_LENGTH) {
        myHistory.removeLast();
      }
    }
  }
}
