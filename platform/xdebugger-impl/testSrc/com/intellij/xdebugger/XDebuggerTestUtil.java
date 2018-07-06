package com.intellij.xdebugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

public class XDebuggerTestUtil extends XDebuggerTestHelper {
  public static <B extends XBreakpoint<?>> void assertBreakpointValidity(Project project,
                                                                         VirtualFile file,
                                                                         int line,
                                                                         boolean validity,
                                                                         String errorMessage,
                                                                         Class<? extends XBreakpointType<B, ?>> breakpointType) {
    XLineBreakpointType type = (XLineBreakpointType)XDebuggerUtil.getInstance().findBreakpointType(breakpointType);
    XBreakpointManager manager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XLineBreakpointImpl breakpoint = ReadAction.compute(() -> (XLineBreakpointImpl)manager.findBreakpointAtLine(type, file, line));
    assertNotNull(breakpoint);
    assertEquals(validity ? XDebuggerUtilImpl.getVerifiedIcon(breakpoint) : AllIcons.Debugger.Db_invalid_breakpoint, breakpoint.getIcon());
    assertEquals(errorMessage, breakpoint.getErrorMessage());
  }
  public static void assertPosition(XSourcePosition pos, VirtualFile file, int line) throws IOException {
    assertNotNull("No current position", pos);
    assertEquals(new File(file.getPath()).getCanonicalPath(), new File(pos.getFile().getPath()).getCanonicalPath());
    if (line != -1) assertEquals(line, pos.getLine());
  }

  public static void assertCurrentPosition(XDebugSession session, VirtualFile file, int line) throws IOException {
    assertPosition(session.getCurrentPosition(), file, line);
  }

  public static void assertVariable(XValue var,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) {
    assertVariable(var, name, type, value, hasChildren, XDebuggerTestHelper::waitFor);
  }

  public static void assertVariable(XValue var,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren,
                                    BiFunction<Semaphore, Long, Boolean> waitFunction) {
    XTestValueNode node = XDebuggerTestHelper.computePresentation(var, waitFunction);

    if (name != null) assertEquals(name, node.myName);
    if (type != null) assertEquals(type, node.myType);
    if (value != null) assertEquals(value, node.myValue);
    if (hasChildren != null) assertEquals(hasChildren, node.myHasChildren);
  }

  public static void assertVariableValue(XValue var, @Nullable String name, @Nullable String value) {
    assertVariable(var, name, null, value, null);
  }

  public static void assertVariableValue(Collection<XValue> vars, @Nullable String name, @Nullable String value) {
    assertVariableValue(XDebuggerTestHelper.findVar(vars, name), name, value);
  }

  public static void assertVariableValueMatches(@NotNull Collection<XValue> vars,
                                                @Nullable String name,
                                                @Nullable @Language("RegExp") String valuePattern) {
    assertVariableValueMatches(XDebuggerTestHelper.findVar(vars, name), name, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull Collection<XValue> vars,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern) {
    assertVariableValueMatches(XDebuggerTestHelper.findVar(vars, name), name, type, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull Collection<XValue> vars,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern,
                                                @Nullable Boolean hasChildren) {
    assertVariableValueMatches(XDebuggerTestHelper.findVar(vars, name), name, type, valuePattern, hasChildren);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable @Language("RegExp") String valuePattern) {
    assertVariableValueMatches(var, name, null, valuePattern);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern) {
    assertVariableValueMatches(var, name, type, valuePattern, null);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern,
                                                @Nullable Boolean hasChildren) {
    assertVariableValueMatches(var, name, type, valuePattern, hasChildren, XDebuggerTestHelper::waitFor);
  }

  public static void assertVariableValueMatches(@NotNull XValue var,
                                                @Nullable String name,
                                                @Nullable String type,
                                                @Nullable @Language("RegExp") String valuePattern,
                                                @Nullable Boolean hasChildren,
                                                BiFunction<Semaphore, Long, Boolean> waitFunction) {
    XTestValueNode node = XDebuggerTestHelper.computePresentation(var, waitFunction);
    if (name != null) assertEquals(name, node.myName);
    if (type != null) assertEquals(type, node.myType);
    if (valuePattern != null) {
      assertTrue("Expected value: " + valuePattern + " Actual value: " + node.myValue, node.myValue.matches(valuePattern));
    }
    if (hasChildren != null) assertEquals(hasChildren, node.myHasChildren);
  }

  public static void assertVariableTypeMatches(@NotNull Collection<XValue> vars,
                                               @Nullable String name,
                                               @Nullable @Language("RegExp") String typePattern) {
    assertVariableTypeMatches(XDebuggerTestHelper.findVar(vars, name), name, typePattern);
  }

  public static void assertVariableTypeMatches(@NotNull XValue var,
                                               @Nullable String name,
                                               @Nullable @Language("RegExp") String typePattern) {
    assertVariableTypeMatches(var, name, typePattern, XDebuggerTestHelper::waitFor);
  }

  public static void assertVariableTypeMatches(@NotNull XValue var,
                                               @Nullable String name,
                                               @Nullable @Language("RegExp") String typePattern,
                                               @NotNull BiFunction<Semaphore, Long, Boolean> waitFunction) {
    XTestValueNode node = XDebuggerTestHelper.computePresentation(var, waitFunction);
    if (name != null) {
      assertEquals(name, node.myName);
    }
    if (typePattern != null) {
      assertTrue("Expected type: " + typePattern + " Actual type: " + node.myType, node.myType.matches(typePattern));
    }
  }

  public static void assertVariableFullValue(@NotNull XValue var,
                                             @Nullable String value) throws Exception {
    assertVariableFullValue(var, value, XDebuggerTestHelper::waitFor);
  }

  public static void assertVariableFullValue(@NotNull XValue var,
                                             @Nullable String value,
                                             @NotNull BiFunction<Semaphore, Long, Boolean> waitFunction) throws Exception {
    XTestValueNode node = XDebuggerTestHelper.computePresentation(var, waitFunction);

    if (value == null) {
      assertNull("full value evaluator should be null", node.myFullValueEvaluator);
    }
    else {
      final FutureResult<String> result = new FutureResult<>();
      node.myFullValueEvaluator.startEvaluation(new XFullValueEvaluator.XFullValueEvaluationCallback() {
        @Override
        public void evaluated(@NotNull String fullValue) {
          result.set(fullValue);
        }

        @Override
        public void evaluated(@NotNull String fullValue, @Nullable Font font) {
          result.set(fullValue);
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          result.set(errorMessage);
        }
      });

      assertEquals(value, result.get(XDebuggerTestHelper.TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }
  }

  public static void assertVariableFullValue(Collection<XValue> vars, @Nullable String name, @Nullable String value)
    throws Exception {
    assertVariableFullValue(XDebuggerTestHelper.findVar(vars, name), value);
  }

  public static void assertVariables(java.util.List<XValue> vars, String... names) {
    java.util.List<String> expectedNames = new ArrayList<>(Arrays.asList(names));

    java.util.List<String> actualNames = new ArrayList<>();
    for (XValue each : vars) {
      actualNames.add(XDebuggerTestHelper.computePresentation(each).myName);
    }

    Collections.sort(actualNames);
    Collections.sort(expectedNames);
    UsefulTestCase.assertOrderedEquals(actualNames, expectedNames);
  }

  public static void assertVariablesContain(java.util.List<XValue> vars, String... names) {
    java.util.List<String> expectedNames = new ArrayList<>(Arrays.asList(names));

    List<String> actualNames = new ArrayList<>();
    for (XValue each : vars) {
      actualNames.add(XDebuggerTestHelper.computePresentation(each).myName);
    }

    expectedNames.removeAll(actualNames);
    assertTrue("Missing variables:" + StringUtil.join(expectedNames, ", ")
        + "\nAll Variables: " + StringUtil.join(actualNames, ", "),
      expectedNames.isEmpty()
    );
  }

  public static void assertVariable(Collection<XValue> vars,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) {
    assertVariable(XDebuggerTestHelper.findVar(vars, name), name, type, value, hasChildren);
  }

  public static void assertSourcePosition(final XValue value, VirtualFile file, int offset) {
    final XTestNavigatable n = new XTestNavigatable();
    ApplicationManager.getApplication().runReadAction(() -> value.computeSourcePosition(n));
    assertNotNull(n.myPosition);
    assertEquals(file, n.myPosition.getFile());
    assertEquals(offset, n.myPosition.getOffset());
  }

  public static void assertSourcePosition(final XStackFrame frame, VirtualFile file, int line) {
    XSourcePosition position = frame.getSourcePosition();
    assertNotNull(position);
    assertEquals(file, position.getFile());
    assertEquals(line, position.getLine());
  }

  public static class XTestNavigatable implements XNavigatable {
    private XSourcePosition myPosition;

    @Override
    public void setSourcePosition(@Nullable XSourcePosition sourcePosition) {
      myPosition = sourcePosition;
    }

    public XSourcePosition getPosition() {
      return myPosition;
    }
  }

  public static void assertVariable(Pair<XValue, String> varAndErrorMessage,
                                    @Nullable String name,
                                    @Nullable String type,
                                    @Nullable String value,
                                    @Nullable Boolean hasChildren) {
    assertNull(varAndErrorMessage.second);
    assertVariable(varAndErrorMessage.first, name, type, value, hasChildren);
  }

  public static String assertVariableExpression(XValue desc, String expectedExpression) {
    String expression = desc.getEvaluationExpression();
    assertEquals(expectedExpression, expression);
    return expression;
  }
}
