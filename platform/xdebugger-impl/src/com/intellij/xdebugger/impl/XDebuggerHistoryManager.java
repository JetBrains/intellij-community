// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.lang.Language;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@State(name = "debuggerHistoryManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class XDebuggerHistoryManager implements PersistentStateComponent<Element> {
  private static final String STATE_TAG = "root";
  private static final String ID_ATTRIBUTE = "id";
  private static final String EXPRESSIONS_TAG = "expressions";
  private static final String EXPRESSION_TAG = "expression";

  private final Map<String, LinkedList<XExpression>> myRecentExpressions = new HashMap<>();

  public static XDebuggerHistoryManager getInstance(@NotNull Project project) {
    return project.getService(XDebuggerHistoryManager.class);
  }

  public boolean addRecentExpression(@NotNull @NonNls String id, @Nullable XExpression expression) {
    if (XDebuggerUtilImpl.isEmptyExpression(expression) || expression.getExpression().length() > 100000) {
      return false;
    }

    LinkedList<XExpression> list = myRecentExpressions.computeIfAbsent(id, k -> new LinkedList<>());
    int max = AdvancedSettings.getInt("debugger.max.recent.expressions");
    while (list.size() >= max) {
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
      List<ExpressionState> states = ContainerUtil.map(expressions, ExpressionState::new);
      Element entryElement = new Element(EXPRESSIONS_TAG);
      entryElement.setAttribute(ID_ATTRIBUTE, id);
      for (ExpressionState expressionState : states) {
        entryElement.addContent(XmlSerializer.serialize(expressionState));
      }
      state.addContent(entryElement);
    }
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
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

  //TODO: unify with com.intellij.xdebugger.impl.breakpoints.XExpressionState
  @Tag(EXPRESSION_TAG)
  private static class ExpressionState {
    @Tag("expression-string") String myExpression;
    @Tag("language-id") String myLanguageId;
    @Tag("custom-info") String myCustomInfo;

    // we must save it always for backward compatibility
    @Tag("evaluation-mode") EvaluationMode myEvaluationMode/* = EvaluationMode.EXPRESSION*/;

    @SuppressWarnings("unused")
    ExpressionState() {
    }

    ExpressionState(@NotNull XExpression expression) {
      myExpression = XmlStringUtil.escapeIllegalXmlChars(expression.getExpression());
      Language language = expression.getLanguage();
      myLanguageId = language == null ? null : language.getID();
      myCustomInfo = expression.getCustomInfo();
      myEvaluationMode = expression.getMode();
    }

    @NotNull
    XExpression toXExpression() {
      if (myEvaluationMode == null) {
        myEvaluationMode = EvaluationMode.EXPRESSION;
      }
      return new XExpressionImpl(XmlStringUtil.unescapeIllegalXmlChars(myExpression),
                                 Language.findLanguageByID(myLanguageId),
                                 myCustomInfo,
                                 myEvaluationMode);
    }
  }
}
