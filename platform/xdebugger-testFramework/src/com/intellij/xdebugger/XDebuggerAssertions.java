// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author eldar
 */
public class XDebuggerAssertions extends XDebuggerTestUtil {
  XDebuggerAssertions() {
  }

  public static <B extends XBreakpoint<?>> void assertBreakpointValidity(@NotNull Project project,
                                                                         @NotNull VirtualFile file,
                                                                         int line,
                                                                         boolean validity,
                                                                         boolean pending,
                                                                         String errorMessage,
                                                                         @NotNull Class<? extends XBreakpointType<B, ?>> breakpointType) {
    XLineBreakpointType type = (XLineBreakpointType)XDebuggerUtil.getInstance().findBreakpointType(breakpointType);
    XBreakpointManager manager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XLineBreakpointImpl breakpoint = ReadAction.compute(() -> (XLineBreakpointImpl)manager.findBreakpointAtLine(type, file, line));
    assertNotNull(breakpoint);
    Icon expectedIcon = validity
                        ? pending
                          ? AllIcons.Debugger.Db_invalid_breakpoint
                          : XDebuggerUtilImpl.getVerifiedIcon(breakpoint)
                        : AllIcons.Debugger.Db_invalid_breakpoint;
    assertEquals(expectedIcon, breakpoint.getIcon());
    assertEquals(errorMessage, breakpoint.getErrorMessage());
  }

  public static void assertPosition(XSourcePosition pos, @NotNull VirtualFile file, int line) throws IOException {
    assertNotNull("No current position", pos);
    assertEquals(new File(file.getPath()).getCanonicalPath(), new File(pos.getFile().getPath()).getCanonicalPath());
    if (line != -1) assertEquals(line, pos.getLine());
  }

  public static void assertCurrentPosition(@NotNull XDebugSession session, @NotNull VirtualFile file, int line) throws IOException {
    assertPosition(session.getCurrentPosition(), file, line);
  }

  public static void assertVariable(@NotNull Collection<? extends XValue> vars,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) {
    assertVariable(vars, name, type, value, hasChildren, null);
  }

  public static void assertVariable(@NotNull Collection<? extends XValue> vars,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren,
                                    @Nullable Icon icon) {
    assertVariable(findVar(vars, name), name, type, value, hasChildren, icon);
  }

  public static void assertVariable(@NotNull Pair<XValue, String> varAndErrorMessage,
                                    @Nullable String name,
                                    @Nullable String type) {
    assertVariable(varAndErrorMessage, name, type, null);
  }

  public static void assertVariable(@NotNull Pair<XValue, String> varAndErrorMessage,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value) {
    assertVariable(varAndErrorMessage, name, type, value, null);
  }

  public static void assertVariable(@NotNull Pair<XValue, String> varAndErrorMessage,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) {
    assertVariable(varAndErrorMessage, name, type, value, hasChildren, null);
  }

  public static void assertVariable(@NotNull Pair<XValue, String> varAndErrorMessage,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren,
                                    @Nullable Icon icon) {
    assertNull(varAndErrorMessage.second);
    assertVariable(varAndErrorMessage.first, name, type, value, hasChildren, icon);
  }

  public static void assertVariable(@NotNull XValue var,
                                    @Nullable String name) {
    assertVariable(var, name, null);
  }

  public static void assertVariable(@NotNull XValue var,
                                    @Nullable String name,
                                    @Nullable String type) {
    assertVariable(var, name, type, null);
  }

  public static void assertVariable(@NotNull XValue var,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value) {
    assertVariable(var, name, type, value, null);
  }

  public static void assertVariable(@NotNull XValue var,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) {
    assertVariable(var, name, type, value, hasChildren, null);
  }

  public static void assertVariable(@NotNull XValue var,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren,
                                    @Nullable Icon icon) {
    XTestValueNode node = computePresentation(var);
    assertVariable(node, name, type, value, hasChildren, icon);
  }

  public static void assertVariable(@NotNull XTestValueNode node,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) {
    assertVariable(node, name, type, value, hasChildren, null);
  }

  public static void assertVariable(@NotNull XTestValueNode node,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren,
                                    @Nullable Icon icon) {
    final String message = node.toString();
    if (name != null) assertEquals(message, name, node.myName);
    if (type != null) assertEquals(message, type, node.myType);
    if (value != null) assertEquals(message, value, node.myValue);
    if (hasChildren != null) assertEquals(message, hasChildren, node.myHasChildren);
    if (icon != null) assertEquals(message, icon, node.myIcon);
  }

  public static void assertVariableValue(@NotNull XValue var, @Nullable String name, @Nullable String value) {
    assertVariable(var, name, null, value, null, null);
  }

  public static void assertVariableValue(@NotNull Collection<? extends XValue> vars, @Nullable String name, @Nullable String value) {
    assertVariableValue(findVar(vars, name), name, value);
  }

  public static void assertVariableValueMatches(@NotNull Collection<? extends XValue> vars,
                                                @Nullable String name,
                                                @Nullable @RegExp String valuePattern) {
    assertVariableValueMatches(findVar(vars, name), name, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull Collection<? extends XValue> vars,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @RegExp String valuePattern) {
    assertVariableValueMatches(findVar(vars, name), name, type, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull Collection<? extends XValue> vars,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @RegExp String valuePattern,
                                                @Nullable Boolean hasChildren) {
    assertVariableValueMatches(findVar(vars, name), name, type, valuePattern, hasChildren);
  }

  public static void assertVariableValueMatches(@NotNull Collection<? extends XValue> vars,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @RegExp String valuePattern,
                                                @Nullable Boolean hasChildren,
                                                @Nullable Icon icon) {
    assertVariableValueMatches(findVar(vars, name), name, type, valuePattern, hasChildren, icon);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable @RegExp String valuePattern) {
    assertVariableValueMatches(var, name, null, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @RegExp String valuePattern) {
    assertVariableValueMatches(var, name, type, valuePattern, null);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @RegExp String valuePattern,
                                                @Nullable Boolean hasChildren) {
    assertVariableValueMatches(var, name, type, valuePattern, hasChildren, null);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @RegExp String valuePattern,
                                                @Nullable Boolean hasChildren,
                                                @Nullable Icon icon) {
    XTestValueNode node = computePresentation(var);
    assertVariable(node, name, type, null, hasChildren, icon);
    if (valuePattern != null) {
      assertTrue("Expected value: " + valuePattern + " Actual value: " + node.myValue, node.myValue.matches(valuePattern));
    }
  }

  public static void assertVariableTypeMatches(@NotNull Collection<? extends XValue> vars,
                                               @Nullable String name,
                                               @Nullable @RegExp String typePattern) {
    assertVariableTypeMatches(findVar(vars, name), name, typePattern);
  }

  public static void assertVariableTypeMatches(@NotNull XValue var,
                                               @Nullable String name,
                                               @Nullable @RegExp String typePattern) {
    assertVariableTypeMatches(var, name, typePattern, null);
  }

  public static void assertVariableTypeMatches(@NotNull XValue var,
                                               @Nullable String name,
                                               @Nullable @RegExp String typePattern,
                                               @Nullable String value) {
    assertVariableTypeMatches(var, name, typePattern, value, null);
  }

  public static void assertVariableTypeMatches(@NotNull XValue var,
                                               @Nullable String name,
                                               @Nullable @RegExp String typePattern,
                                               @Nullable String value,
                                               @Nullable Boolean hasChildren) {
    assertVariableTypeMatches(var, name, typePattern, value, hasChildren, null);
  }

  public static void assertVariableTypeMatches(@NotNull XValue var,
                                               @Nullable String name,
                                               @Nullable @RegExp String typePattern,
                                               @Nullable String value,
                                               @Nullable Boolean hasChildren,
                                               @Nullable Icon icon) {
    XTestValueNode node = computePresentation(var);
    assertVariable(node, name, null, value, hasChildren, icon);
    if (typePattern != null) {
      assertTrue("Expected type: " + typePattern + " Actual type: " + node.myType,
                 node.myType != null && node.myType.matches(typePattern));
    }
  }

  public static void assertVariableFullValue(@NotNull XValue var,
                                             @Nullable String value) throws Exception {
    XTestValueNode node = computePresentation(var);

    if (value == null) {
      assertNull("full value evaluator should be null", node.myFullValueEvaluator);
    }
    else {
      final String actualValue = computeFullValue(node);
      assertEquals(value, actualValue);
    }
  }

  public static void assertVariableFullValueMatches(@NotNull XValue var,
                                                    @RegExp @Nullable String valueRegexp) throws Exception {
    XTestValueNode node = computePresentation(var);

    if (valueRegexp == null) {
      assertNull("full value evaluator should be null", node.myFullValueEvaluator);
    }
    else {
      final String value = computeFullValue(node);
      assertTrue(value + " is not matched by " + valueRegexp, value.matches(valueRegexp));
    }
  }

  private static String computeFullValue(@NotNull XTestValueNode node) throws Exception {
    assertNotNull("full value evaluator should be not null", node.myFullValueEvaluator);
    CompletableFuture<String> result = new CompletableFuture<>();
    node.myFullValueEvaluator.startEvaluation(new XFullValueEvaluator.XFullValueEvaluationCallback() {
      @Override
      public void evaluated(@NotNull String fullValue) {
        result.complete(fullValue);
      }

      @Override
      public void evaluated(@NotNull String fullValue, @Nullable Font font) {
        result.complete(fullValue);
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        result.complete(errorMessage);
      }
    });

    return result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  public static void assertVariableFullValue(@NotNull Collection<? extends XValue> vars, @Nullable String name, @Nullable String value)
    throws Exception {
    assertVariableFullValue(findVar(vars, name), value);
  }

  public static void assertVariables(@NotNull List<? extends XValue> vars, String... names) {

    List<String> actualNames = new ArrayList<>();
    for (XValue each : vars) {
      actualNames.add(computePresentation(each).myName);
    }

    Collections.sort(actualNames);
    List<String> expectedNames = ContainerUtil.sorted(Arrays.asList(names));
    UsefulTestCase.assertOrderedEquals(actualNames, expectedNames);
  }

  public static void assertVariablesContain(@NotNull List<? extends XValue> vars, String... names) {
    List<String> expectedNames = new ArrayList<>(Arrays.asList(names));

    List<String> actualNames = new ArrayList<>();
    for (XValue each : vars) {
      actualNames.add(computePresentation(each).myName);
    }

    expectedNames.removeAll(actualNames);
    assertTrue("Missing variables:" + StringUtil.join(expectedNames, ", ")
               + "\nAll Variables: " + StringUtil.join(actualNames, ", "),
               expectedNames.isEmpty()
    );
  }

  public static void assertVariablesDontContain(@NotNull List<? extends XValue> vars, String... notExpected) {
    List<String> actualNames = new ArrayList<>();
    for (XValue each : vars) {
      actualNames.add(computePresentation(each).myName);
    }

    String allVariablesNames = StringUtil.join(actualNames, ", ");
    actualNames.retainAll(List.of(notExpected));

    assertTrue("Present variables: " + StringUtil.join(actualNames, ", ")
               + "\nAll Variables: " + allVariablesNames,
               actualNames.isEmpty()
    );
  }

  public static void assertSourcePosition(@NotNull XValue value, @NotNull VirtualFile file, int offset) {
    final XTestNavigatable n = new XTestNavigatable();
    ApplicationManager.getApplication().invokeAndWait(() -> value.computeSourcePosition(n));
    assertNotNull(n.getPosition());
    assertEquals(file, n.getPosition().getFile());
    assertEquals(offset, n.getPosition().getOffset());
  }

  public static void assertSourcePosition(@NotNull XStackFrame frame, @NotNull VirtualFile file, int line) {
    XSourcePosition position = frame.getSourcePosition();
    assertNotNull(position);
    assertEquals(file, position.getFile());
    assertEquals(line, position.getLine());
  }

  public static String assertVariableExpression(@NotNull XValue desc, String expectedExpression) {
    String expression = desc.getEvaluationExpression();
    assertEquals(expectedExpression, expression);
    return expression;
  }
}