/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class XDebuggerHistoryManager {
  public static final int MAX_RECENT_EXPRESSIONS = 10;
  private final Map<String, LinkedList<XExpression>> myRecentExpressions = new HashMap<String, LinkedList<XExpression>>();

  public static XDebuggerHistoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, XDebuggerHistoryManager.class);
  }

  public boolean addRecentExpression(@NotNull @NonNls String id, @Nullable XExpression expression) {
    if (XDebuggerUtilImpl.isEmptyExpression(expression)) {
      return false;
    }

    LinkedList<XExpression> list = myRecentExpressions.get(id);
    if (list == null) {
      list = new LinkedList<XExpression>();
      myRecentExpressions.put(id, list);
    }
    if (list.size() == MAX_RECENT_EXPRESSIONS) {
      list.removeLast();
    }

    XExpression trimmedExpression = new XExpressionImpl(expression.getExpression().trim(), expression.getLanguage(), expression.getCustomInfo(), expression.getMode());
    list.remove(trimmedExpression);
    list.addFirst(trimmedExpression);
    return true;
  }

  public List<XExpression> getRecentExpressions(@NonNls String id) {
    return ContainerUtil.notNullize(myRecentExpressions.get(id));
  }
}
