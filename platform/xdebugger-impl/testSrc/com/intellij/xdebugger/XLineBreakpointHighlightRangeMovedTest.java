// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XBreakpointsFixture.MyBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.xdebugger.XBreakpointsFixtureKt.xBreakpointsFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class XLineBreakpointHighlightRangeMovedTest {
  private final TestFixture<XBreakpointsFixture> breakpointsFixture = xBreakpointsFixture();
  private final RecordingLineBreakpointType myType = new RecordingLineBreakpointType();
  private final List<XBreakpoint<?>> myChanged = new ArrayList<>();

  @BeforeEach
  void setUp(@TestDisposable Disposable disposable) {
    ExtensionPoint<XBreakpointType> point = XBreakpointType.EXTENSION_POINT_NAME.getPoint();
    point.registerExtension(myType, disposable);
    breakpointsFixture.get().getProject().getMessageBus().connect(disposable).subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
      @Override
      public void breakpointChanged(@NotNull XBreakpoint<?> breakpoint) {
        myChanged.add(breakpoint);
      }
    });
  }

  @Test
  public void testResetSourcePositionReportsTrackedRange() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.resetSourcePosition(1, new TextRange(3, 7));

    assertEquals(List.of(new TextRange(3, 7)), myType.myRanges);
    assertEquals(List.of(breakpoint), myChanged);
  }

  @Test
  public void testSetLineReportsTrackedRangeBeforeLineUpdate() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.setLine(1, 5, new TextRange(9, 12));

    assertEquals(List.of(new TextRange(9, 12)), myType.myRanges);
    assertEquals(List.of(0), myType.myLinesAtReport);
    assertEquals(5, breakpoint.getLine());
    assertEquals(List.of(breakpoint), myChanged);
  }

  @Test
  public void testNullRangeSkipsHook() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.resetSourcePosition(1, null);
    breakpoint.setLine(2, 5, null);

    assertThat(myType.myRanges).isEmpty();
    assertEquals(List.of(breakpoint, breakpoint), myChanged);
  }

  private XLineBreakpointImpl<?> addBreakpoint() {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      breakpointManager.addLineBreakpoint(myType, "file://test", 0, new MyBreakpointProperties("range"));
    return (XLineBreakpointImpl<?>)breakpoint;
  }

  private static final class RecordingLineBreakpointType extends XLineBreakpointType<MyBreakpointProperties> {
    final List<TextRange> myRanges = new ArrayList<>();
    final List<Integer> myLinesAtReport = new ArrayList<>();

    RecordingLineBreakpointType() {
      super("testLineHighlightRange", "range");
    }

    @Override
    public MyBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
      return null;
    }

    @Override
    public MyBreakpointProperties createProperties() {
      return new MyBreakpointProperties();
    }

    @Override
    public void highlightRangeMoved(@NotNull XLineBreakpoint<MyBreakpointProperties> breakpoint, @NotNull TextRange range) {
      myRanges.add(range);
      myLinesAtReport.add(breakpoint.getLine());
    }
  }
}
