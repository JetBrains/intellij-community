/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class XDebuggerHistoryManager {
  public static final int MAX_RECENT_EXPRESSIONS = 10;
  private final Map<String, LinkedList<String>> myRecentExpressions = new HashMap<String, LinkedList<String>>();

  public static XDebuggerHistoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, XDebuggerHistoryManager.class);
  }

  public void addRecentExpression(@NotNull @NonNls String id, @NotNull String expression) {
    if (StringUtil.isEmptyOrSpaces(expression)) {
      return;
    }

    LinkedList<String> list = myRecentExpressions.get(id);
    if (list == null) {
      list = new LinkedList<String>();
      myRecentExpressions.put(id, list);
    }
    if (list.size() == MAX_RECENT_EXPRESSIONS) {
      list.removeLast();
    }

    String trimmedExpression = expression.trim();
    list.remove(trimmedExpression);
    list.addFirst(trimmedExpression);
  }

  public List<String> getRecentExpressions(@NonNls String id) {
    LinkedList<String> list = myRecentExpressions.get(id);
    return list != null ? list : Collections.<String>emptyList();
  }
}
