// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class XLineBreakpointHighlightRangeMovedTest extends XBreakpointsTestCase {
  private final RecordingLineBreakpointType myType = new RecordingLineBreakpointType();
  private final List<XBreakpoint<?>> myChanged = new ArrayList<>();

  @Override
  protected void initApplication() throws Exception {
    super.initApplication();
    ExtensionPoint<XBreakpointType> point = XBreakpointType.EXTENSION_POINT_NAME.getPoint();
    point.registerExtension(myType, getTestRootDisposable());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject.getMessageBus().connect(getTestRootDisposable()).subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
      @Override
      public void breakpointChanged(@NotNull XBreakpoint<?> breakpoint) {
        myChanged.add(breakpoint);
      }
    });
  }

  public void testResetSourcePositionReportsTrackedRange() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.resetSourcePosition(1, new TextRange(3, 7));

    assertEquals(List.of(new TextRange(3, 7)), myType.myRanges);
    assertEquals(List.of(breakpoint), myChanged);
  }

  public void testSetLineReportsTrackedRangeBeforeLineUpdate() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.setLine(1, 5, new TextRange(9, 12));

    assertEquals(List.of(new TextRange(9, 12)), myType.myRanges);
    assertEquals(List.of(0), myType.myLinesAtReport);
    assertEquals(5, breakpoint.getLine());
    assertEquals(List.of(breakpoint), myChanged);
  }

  public void testStaleRequestNotifiesWhenRangeMoved() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.resetSourcePosition(2, new TextRange(3, 7));
    breakpoint.resetSourcePosition(1, new TextRange(4, 8));

    assertEquals(List.of(new TextRange(3, 7), new TextRange(4, 8)), myType.myRanges);
    assertEquals(List.of(breakpoint, breakpoint), myChanged);
  }

  public void testStaleRequestWithUnchangedPropertiesSkipsNotification() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();
    myType.myReportsChange = false;

    breakpoint.resetSourcePosition(2, new TextRange(3, 7));
    breakpoint.resetSourcePosition(1, new TextRange(4, 8));

    assertEquals(List.of(new TextRange(3, 7), new TextRange(4, 8)), myType.myRanges);
    assertEquals(List.of(breakpoint), myChanged);
  }

  public void testSetLineOnSameLineWithStaleRequestNotifiesWhenRangeMoved() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.setLine(2, 0, new TextRange(3, 7));
    breakpoint.setLine(1, 0, new TextRange(4, 8));

    assertEquals(0, breakpoint.getLine());
    assertEquals(List.of(breakpoint, breakpoint), myChanged);
  }

  public void testNullRangeSkipsHook() {
    XLineBreakpointImpl<?> breakpoint = addBreakpoint();

    breakpoint.resetSourcePosition(1, null);
    breakpoint.setLine(2, 5, null);

    assertEmpty(myType.myRanges);
    assertEquals(List.of(breakpoint, breakpoint), myChanged);
  }

  private XLineBreakpointImpl<?> addBreakpoint() {
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      myBreakpointManager.addLineBreakpoint(myType, "file://test", 0, new MyBreakpointProperties("range"));
    return (XLineBreakpointImpl<?>)breakpoint;
  }

  private static final class RecordingLineBreakpointType extends XLineBreakpointType<MyBreakpointProperties> {
    final List<TextRange> myRanges = new ArrayList<>();
    final List<Integer> myLinesAtReport = new ArrayList<>();
    boolean myReportsChange = true;

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
    public boolean highlightRangeMoved(@NotNull XLineBreakpoint<MyBreakpointProperties> breakpoint, @NotNull TextRange range) {
      myRanges.add(range);
      myLinesAtReport.add(breakpoint.getLine());
      return myReportsChange;
    }
  }
}
