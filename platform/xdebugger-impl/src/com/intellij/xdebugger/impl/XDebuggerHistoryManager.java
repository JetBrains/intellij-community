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

import com.intellij.lang.Language;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author nik
 */
@State(name = "debuggerHistoryManager", storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE))
public class XDebuggerHistoryManager implements PersistentStateComponent<Element> {
  public static final int MAX_RECENT_EXPRESSIONS = 10;
  private static final SerializationFilter SERIALIZATION_FILTER = new SkipDefaultsSerializationFilter();
  private static final String STATE_TAG = "root";
  private static final String ID_ATTRIBUTE = "id";
  private static final String EXPRESSIONS_TAG = "expressions";
  private static final String EXPRESSION_TAG = "expression";

  private final Map<String, LinkedList<XExpression>> myRecentExpressions = new HashMap<>();

  public static XDebuggerHistoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, XDebuggerHistoryManager.class);
  }

  public boolean addRecentExpression(@NotNull @NonNls String id, @Nullable XExpression expression) {
    if (XDebuggerUtilImpl.isEmptyExpression(expression)) {
      return false;
    }

    LinkedList<XExpression> list = myRecentExpressions.computeIfAbsent(id, k -> new LinkedList<>());
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

  @Nullable
  @Override
  public Element getState() {
    Element state = new Element(STATE_TAG);
    for (String id : myRecentExpressions.keySet()) {
      LinkedList<XExpression> expressions = myRecentExpressions.get(id);
      List<ExpressionState> states = expressions.stream().map(ExpressionState::new).collect(Collectors.toList());
      Element entryElement = new Element(EXPRESSIONS_TAG);
      entryElement.setAttribute(ID_ATTRIBUTE, id);
      for (ExpressionState expressionState : states) {
        entryElement.addContent(XmlSerializer.serialize(expressionState, SERIALIZATION_FILTER));
      }
      state.addContent(entryElement);
    }
    return state;
  }

  @Override
  public void loadState(Element state) {
    myRecentExpressions.clear();
    for (Element expression : state.getChildren(EXPRESSIONS_TAG)) {
      String id = expression.getAttributeValue(ID_ATTRIBUTE);
      LinkedList<XExpression> expressions = new LinkedList<>();
      for (Element expressionElement : expression.getChildren(EXPRESSION_TAG)) {
        expressions.add(XmlSerializer.deserialize(expressionElement, ExpressionState.class).toXExpression());
      }

      myRecentExpressions.put(id, expressions);
    }
  }

  @Tag(EXPRESSION_TAG)
  private static class ExpressionState {
    @Tag("expression-string") String myExpression;
    @Tag("language-id") String myLanguageId;
    @Tag("custom-info") String myCustomInfo;
    @Tag("evaluation-mode") EvaluationMode myEvaluationMode;

    @SuppressWarnings("unused")
    ExpressionState() {
    }

    ExpressionState(@NotNull XExpression expression) {
      myExpression = expression.getExpression();
      Language language = expression.getLanguage();
      myLanguageId = language == null ? null : language.getID();
      myCustomInfo = expression.getCustomInfo();
      myEvaluationMode = expression.getMode();
    }

    @NotNull
    XExpression toXExpression() {
      return new XExpressionImpl(myExpression, Language.findLanguageByID(myLanguageId), myCustomInfo, myEvaluationMode);
    }
  }
}
