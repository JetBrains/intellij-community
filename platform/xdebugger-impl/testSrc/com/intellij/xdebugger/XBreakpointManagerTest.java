// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XBreakpointsFixture.MyBreakpointProperties;
import com.intellij.xdebugger.XBreakpointsFixture.MySimpleBreakpointType;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointAdditionalInfo;
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.breakpoints.BreakpointState;
import com.intellij.xdebugger.impl.breakpoints.ThreadDumpCapturePolicy;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import kotlin.Unit;
import kotlinx.coroutines.flow.MutableSharedFlow;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.xdebugger.XBreakpointsFixture.MY_LINE_BREAKPOINT_TYPE;
import static com.intellij.xdebugger.XBreakpointsFixture.MY_SIMPLE_BREAKPOINT_TYPE;
import static com.intellij.xdebugger.XBreakpointsFixtureKt.xBreakpointsFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class XBreakpointManagerTest {
  private final TestFixture<XBreakpointsFixture> breakpointsFixture = xBreakpointsFixture();

  @Test
  public void testAddRemove() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    Set<XBreakpoint<MyBreakpointProperties>> defaultBreakpoints = breakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE);
    assertThat(defaultBreakpoints).hasSize(1);
    XBreakpoint<MyBreakpointProperties> defaultBreakpoint = ContainerUtil.getOnlyItem(defaultBreakpoints);
    assertThat(fixture.getAllBreakpoints()).containsExactlyInAnyOrder(defaultBreakpoint);

    XLineBreakpoint<MyBreakpointProperties> lineBreakpoint =
      fixture.addLineBreakpoint("url", 239, new MyBreakpointProperties("123"));

    XBreakpoint<MyBreakpointProperties> breakpoint = fixture.addBreakpoint(new MyBreakpointProperties("abc"));

    assertThat(fixture.getAllBreakpoints()).containsExactlyInAnyOrder(breakpoint, lineBreakpoint, defaultBreakpoint);
    assertSame(lineBreakpoint, assertThat(breakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)).singleElement().actual());
    Assertions.<XBreakpoint<MyBreakpointProperties>>assertThat(breakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)).containsExactlyInAnyOrder(breakpoint, defaultBreakpoint);

    fixture.removeBreakpoint(lineBreakpoint);
    assertThat(fixture.getAllBreakpoints()).containsExactlyInAnyOrder(breakpoint, defaultBreakpoint);
    assertTrue(breakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE).isEmpty());
    Assertions.<XBreakpoint<MyBreakpointProperties>>assertThat(breakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)).containsExactlyInAnyOrder(breakpoint, defaultBreakpoint);

    fixture.removeBreakpoint(breakpoint);
    assertThat(fixture.getAllBreakpoints()).containsExactlyInAnyOrder(defaultBreakpoint);
    Assertions.<XBreakpoint<MyBreakpointProperties>>assertThat(breakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)).containsExactlyInAnyOrder(defaultBreakpoint);
  }

  @Test
  public void testSerialize() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      fixture.addLineBreakpoint("myurl", 239, new MyBreakpointProperties("z1"));
    breakpoint.setCondition("cond");
    breakpoint.setLogExpression("log");
    breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
    breakpoint.setLogMessage(true);
    breakpoint.setTemporary(true);
    ((XBreakpointBase<?, ?, ?>)breakpoint).setThreadDumpCapturePolicy(ThreadDumpCapturePolicy.OncePerSession.INSTANCE);
    fixture.addBreakpoint(new MyBreakpointProperties("z2"));

    reload();
    List<XBreakpoint<?>> breakpoints = fixture.getAllBreakpoints();
    assertEquals(3, breakpoints.size(), "Expected 3 breakpoints, actual: " + breakpoints);

    assertTrue(breakpointManager.isDefaultBreakpoint(breakpoints.getFirst()));
    assertEquals("default", assertInstanceOf(MyBreakpointProperties.class, breakpoints.get(0).getProperties()).myOption);
    assertTrue(breakpoints.get(0).isEnabled());

    XLineBreakpoint lineBreakpoint = assertInstanceOf(XLineBreakpoint.class, breakpoints.get(1));
    assertEquals(239, lineBreakpoint.getLine());
    assertEquals("myurl", lineBreakpoint.getFileUrl());
    assertEquals("z1", assertInstanceOf(MyBreakpointProperties.class, lineBreakpoint.getProperties()).myOption);
    assertEquals("cond", lineBreakpoint.getConditionExpression().getExpression());
    assertEquals("log", lineBreakpoint.getLogExpressionObject().getExpression());
    assertTrue(lineBreakpoint.isLogMessage());
    assertTrue(lineBreakpoint.isTemporary());
    assertSame(ThreadDumpCapturePolicy.OncePerSession.INSTANCE, ((XBreakpointBase<?, ?, ?>)lineBreakpoint).getThreadDumpCapturePolicy());
    assertEquals(SuspendPolicy.NONE, lineBreakpoint.getSuspendPolicy());
    assertNull(((XBreakpointBase<?, ?, ?>)breakpoints.get(2)).getThreadDumpCapturePolicy());

    assertEquals("z2", assertInstanceOf(MyBreakpointProperties.class, breakpoints.get(2).getProperties()).myOption);
    assertEquals(SuspendPolicy.ALL, breakpoints.get(2).getSuspendPolicy());
    assertFalse(breakpoints.get(2).isLogMessage());
  }

  @Test
  public void testCopyLineBreakpointCopiesStateAndDependency() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    XLineBreakpoint<MyBreakpointProperties> master =
      fixture.addLineBreakpoint("file://master", 1, new MyBreakpointProperties("master"));
    XLineBreakpoint<MyBreakpointProperties> source =
      fixture.addLineBreakpoint("file://source", 2, new MyBreakpointProperties("source"));
    source.setCondition("condition");
    source.setSuspendPolicy(SuspendPolicy.THREAD);
    breakpointManager.getDependentBreakpointManager().setMasterBreakpoint(source, master, true);

    XLineBreakpoint<MyBreakpointProperties> copy = breakpointManager.copyLineBreakpoint(source, "file://copy", 3);

    assertNotNull(copy);
    assertEquals("file://copy", copy.getFileUrl());
    assertEquals(3, copy.getLine());
    assertEquals("source", copy.getProperties().myOption);
    assertNotSame(source.getProperties(), copy.getProperties());
    assertEquals("condition", copy.getConditionExpression().getExpression());
    assertEquals(SuspendPolicy.THREAD, copy.getSuspendPolicy());
    assertSame(master, breakpointManager.getDependentBreakpointManager().getMasterBreakpoint(copy));
    assertTrue(breakpointManager.getDependentBreakpointManager().isLeaveEnabled(copy));
  }

  @Test
  public void testSameLineBreakpointsCanCoexistByPlacement() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    VirtualFile file = fixture.createFile("coexisting-breakpoints.txt");
    XLineBreakpoint<MyBreakpointProperties> onLine =
      breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, new MyBreakpointProperties("on-line"),
                                          new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.ON_LINE).build());
    XLineBreakpoint<MyBreakpointProperties> interLine =
      breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, new MyBreakpointProperties("inter-line"),
                                          new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.INTER_LINE).build());

    Assertions.<XLineBreakpoint<MyBreakpointProperties>>assertThat(breakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)).containsExactlyInAnyOrder(onLine, interLine);
    assertSame(onLine, assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0)).singleElement().actual());
    assertSame(onLine, breakpointManager.findBreakpointAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0));
    assertSame(onLine, assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0,
                                                                          XLineBreakpointVerticalPlacement.ON_LINE)).singleElement().actual());
    assertSame(interLine, assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0,
                                                                             XLineBreakpointVerticalPlacement.INTER_LINE)).singleElement().actual());
  }

  @Test
  public void testSerializePlacement() {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "myurl", 239, new MyBreakpointProperties("on-line"),
                                        new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.ON_LINE).build());
    breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "myurl", 239, new MyBreakpointProperties("inter-line"),
                                        new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.INTER_LINE).build());

    reload();

    List<XLineBreakpoint<MyBreakpointProperties>> lineBreakpoints = ContainerUtil.map(breakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE),
                                                                                      breakpoint -> (XLineBreakpoint<MyBreakpointProperties>)breakpoint);
    assertEquals(2, lineBreakpoints.size());
    assertTrue(ContainerUtil.exists(lineBreakpoints, breakpoint -> breakpoint.getPlacement() == XLineBreakpointVerticalPlacement.ON_LINE));
    assertTrue(ContainerUtil.exists(lineBreakpoints, breakpoint -> breakpoint.getPlacement() == XLineBreakpointVerticalPlacement.INTER_LINE));
  }

  @Test
  public void testRestoreRemovedBreakpointDoesNotRemoveOtherPlacement() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    VirtualFile file = fixture.createFile("breakpoint.txt");
    XLineBreakpoint<MyBreakpointProperties> onLine =
      breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, new MyBreakpointProperties("on-line"),
                                          new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.ON_LINE).build());
    XLineBreakpoint<MyBreakpointProperties> interLine =
      breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, new MyBreakpointProperties("inter-line"),
                                          new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.INTER_LINE).build());

    breakpointManager.rememberRemovedBreakpoint((XBreakpointBase<?, ?, ?>)interLine);
    fixture.removeBreakpoint(interLine);

    XLineBreakpoint<?> restored = assertInstanceOf(XLineBreakpoint.class, breakpointManager.restoreLastRemovedBreakpoint());
    assertEquals(XLineBreakpointVerticalPlacement.INTER_LINE, restored.getPlacement());
    assertSame(onLine, assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0)).singleElement().actual());
    assertSame(restored, assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0,
                                                                            XLineBreakpointVerticalPlacement.INTER_LINE)).singleElement().actual());
  }

  @Test
  public void testRestoreRemovedBreakpointRestoresDependency() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    VirtualFile file = fixture.createFile("dependent-breakpoints.txt");
    XLineBreakpoint<MyBreakpointProperties> master =
      fixture.addLineBreakpoint(file.getUrl(), 0, new MyBreakpointProperties("master"));
    XLineBreakpoint<MyBreakpointProperties> slave =
      fixture.addLineBreakpoint(file.getUrl(), 1, new MyBreakpointProperties("slave"));
    breakpointManager.getDependentBreakpointManager().setMasterBreakpoint(slave, master, true);

    breakpointManager.rememberRemovedBreakpoint((XBreakpointBase<?, ?, ?>)slave);
    fixture.removeBreakpoint(slave);
    XLineBreakpoint<?> restored = assertInstanceOf(XLineBreakpoint.class, breakpointManager.restoreLastRemovedBreakpoint());

    assertSame(master, breakpointManager.getDependentBreakpointManager().getMasterBreakpoint(restored));
    assertTrue(breakpointManager.getDependentBreakpointManager().isLeaveEnabled(restored));
  }

  @Test
  public void testChangingPlacementFromLogpointToBreakpointAndBackUpdatesBreakpointLookup(@TestDisposable Disposable disposable) {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    VirtualFile file = fixture.createFile("breakpoint.txt");
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, new MyBreakpointProperties("inter-line"),
                                          new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.INTER_LINE).build());

    StringBuilder out = new StringBuilder();
    breakpointManager.addBreakpointListener(MY_LINE_BREAKPOINT_TYPE, new XBreakpointListener<>() {
      @Override
      public void breakpointChanged(@NotNull XLineBreakpoint<MyBreakpointProperties> changedBreakpoint) {
        out.append("changed[").append(changedBreakpoint.getProperties().myOption).append("];");
      }
    }, disposable);

    assertLookupForPlacement(file, breakpoint, XLineBreakpointVerticalPlacement.INTER_LINE);

    XLineBreakpointImpl<?> breakpointImpl = assertInstanceOf(XLineBreakpointImpl.class, breakpoint);
    breakpointImpl.setPlacement(XLineBreakpointVerticalPlacement.ON_LINE);

    assertLookupForPlacement(file, breakpoint, XLineBreakpointVerticalPlacement.ON_LINE);

    breakpointImpl.setPlacement(XLineBreakpointVerticalPlacement.INTER_LINE);

    assertLookupForPlacement(file, breakpoint, XLineBreakpointVerticalPlacement.INTER_LINE);
    assertEquals("changed[inter-line];changed[inter-line];", out.toString());
  }

  @Test
  public void testChangingPlacementFromBreakpointToLogpointAndBackUpdatesBreakpointLookup(@TestDisposable Disposable disposable) {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    VirtualFile file = fixture.createFile("breakpoint.txt");
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, new MyBreakpointProperties("on-line"),
                                          new XLineBreakpointAdditionalInfo.Builder().setVerticalPlacement(XLineBreakpointVerticalPlacement.ON_LINE).build());

    StringBuilder out = new StringBuilder();
    breakpointManager.addBreakpointListener(MY_LINE_BREAKPOINT_TYPE, new XBreakpointListener<>() {
      @Override
      public void breakpointChanged(@NotNull XLineBreakpoint<MyBreakpointProperties> changedBreakpoint) {
        out.append("changed[").append(changedBreakpoint.getProperties().myOption).append("];");
      }
    }, disposable);

    assertLookupForPlacement(file, breakpoint, XLineBreakpointVerticalPlacement.ON_LINE);

    XLineBreakpointImpl<?> breakpointImpl = assertInstanceOf(XLineBreakpointImpl.class, breakpoint);
    breakpointImpl.setPlacement(XLineBreakpointVerticalPlacement.INTER_LINE);

    assertLookupForPlacement(file, breakpoint, XLineBreakpointVerticalPlacement.INTER_LINE);

    breakpointImpl.setPlacement(XLineBreakpointVerticalPlacement.ON_LINE);

    assertLookupForPlacement(file, breakpoint, XLineBreakpointVerticalPlacement.ON_LINE);
    assertEquals("changed[on-line];changed[on-line];", out.toString());
  }

  @Test
  public void testDoNotSaveUnmodifiedDefaultBreakpoint() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    reload();

    assertThat(getSingleBreakpoint().getProperties().myOption).isEqualTo("default");
    Element element = fixture.save();
    assertThat(element).isNull();
  }

  @Test
  public void testSaveChangedDefaultBreakpoint() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    reload();
    final XBreakpoint<MyBreakpointProperties> breakpoint = getSingleBreakpoint();
    breakpoint.setEnabled(false);

    assertFalse(fixture.save().getContent().isEmpty());
    reload();
    assertFalse(getSingleBreakpoint().isEnabled());
  }

  @Test
  public void testSaveDefaultBreakpointWithModifiedProperties() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    reload();
    getSingleBreakpoint().getProperties().myOption = "changed";

    assertFalse(fixture.save().getContent().isEmpty());
    reload();
    assertEquals("changed", getSingleBreakpoint().getProperties().myOption);
  }

  @Test
  public void testReloadDoesNotDuplicateDefaultBreakpointsInEventDrivenConsumer(@TestDisposable Disposable disposable) {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    List<XBreakpoint<MyBreakpointProperties>> visibleBreakpoints =
      new ArrayList<>(breakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE));
    List<String> listenerCalls = new ArrayList<>();
    breakpointManager.addBreakpointListener(MY_SIMPLE_BREAKPOINT_TYPE, new XBreakpointListener<>() {
      @Override
      public void breakpointAdded(@NotNull XBreakpoint<MyBreakpointProperties> breakpoint) {
        listenerCalls.add("breakpoint added " + breakpoint.getProperties().myOption);
        visibleBreakpoints.add(breakpoint);
      }

      @Override
      public void breakpointRemoved(@NotNull XBreakpoint<MyBreakpointProperties> breakpoint) {
        listenerCalls.add("breakpoint removed " + breakpoint.getProperties().myOption);
        visibleBreakpoints.remove(breakpoint);
      }

      @Override
      public void breakpointChanged(@NotNull XBreakpoint<MyBreakpointProperties> breakpoint) {
        listenerCalls.add("breakpoint changed " + breakpoint.getProperties().myOption);
      }
    }, disposable);

    reload();

    XBreakpoint<MyBreakpointProperties> restoredBreakpoint = assertThat(breakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)).singleElement().actual();
    assertThat(listenerCalls).containsExactly("breakpoint removed default", "breakpoint added default");
    assertThat(visibleBreakpoints).doesNotHaveDuplicates();
    assertThat(visibleBreakpoints).containsExactly(restoredBreakpoint);
  }

  @Test
  public void testLoadStateDeduplicatesDefaultBreakpointsWithSameState() {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    BreakpointManagerState state = new BreakpointManagerState();
    BreakpointState first = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), true, SuspendPolicy.NONE, 1);
    BreakpointState second = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), true, SuspendPolicy.NONE, 2);
    state.getDefaultBreakpoints().add(first);
    state.getDefaultBreakpoints().add(second);

    breakpointManager.loadState(state);

    XBreakpoint<MyBreakpointProperties> breakpoint = assertThat(breakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)).singleElement().actual();
    assertEquals(1, breakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE).size());
    assertTrue(breakpoint.isEnabled());
    assertEquals(SuspendPolicy.NONE, breakpoint.getSuspendPolicy());
  }

  @Test
  public void testLoadStateKeepsDefaultBreakpointsWithDifferentStates() {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    BreakpointManagerState state = new BreakpointManagerState();
    BreakpointState first = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), true, SuspendPolicy.NONE);
    BreakpointState second = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), false, SuspendPolicy.ALL);
    state.getDefaultBreakpoints().add(first);
    state.getDefaultBreakpoints().add(second);

    breakpointManager.loadState(state);

    Set<XBreakpoint<MyBreakpointProperties>> breakpoints = breakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE);
    assertEquals(2, breakpoints.size());
    assertEquals(2, breakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE).size());
    assertTrue(
      ContainerUtil.exists(breakpoints, breakpoint -> breakpoint.isEnabled() && breakpoint.getSuspendPolicy() == SuspendPolicy.NONE));
    assertTrue(
      ContainerUtil.exists(breakpoints, breakpoint -> !breakpoint.isEnabled() && breakpoint.getSuspendPolicy() == SuspendPolicy.ALL));
  }

  @Test
  public void testLoadStateCallsPropertiesOutsideManagerLock(@TestDisposable Disposable disposable) throws Exception {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    AtomicBoolean propertiesLoaded = new AtomicBoolean();
    XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties> type =
      new XBreakpointType<>("testLoadStateCallsPropertiesOutsideManagerLock", "BP") {
        @Override
        public String getDisplayText(XBreakpoint<MyBreakpointProperties> breakpoint) {
          return "";
        }

        @Override
        public MyBreakpointProperties createProperties() {
          assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
          assertBreakpointManagerIsAvailable();
          return new MyBreakpointProperties() {
            @Override
            public void loadState(@NotNull MyBreakpointProperties state) {
              assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
              assertBreakpointManagerIsAvailable();
              super.loadState(state);
              propertiesLoaded.set(true);
            }
          };
        }
      };
    XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(type, disposable);
    breakpointManager.addBreakpoint(type, new MyBreakpointProperties("loaded"));

    BreakpointManagerState state = new BreakpointManagerState();
    breakpointManager.saveState(state);
    ApplicationManager.getApplication().executeOnPooledThread(() -> breakpointManager.loadState(state)).get();

    assertTrue(propertiesLoaded.get());
  }

  private void assertBreakpointManagerIsAvailable() {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    var access = ApplicationManager.getApplication().executeOnPooledThread(breakpointManager::getAllBreakpoints);
    try {
      access.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    catch (Exception e) {
      throw new AssertionError("The breakpoint manager lock is held during a plug-in callback", e);
    }
    finally {
      access.cancel(true);
    }
  }

  @Test
  public void testExtensionAddedAddsDefaultBreakpointWhenStatesDiffer(@TestDisposable Disposable disposable) {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties> type =
      createExtensionType("from-extension", SuspendPolicy.NONE);

    breakpointManager.addDefaultBreakpoint(type, new MyBreakpointProperties("existing"));
    assertEquals(1, breakpointManager.getDefaultBreakpoints(type).size());

    XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(type, disposable);

    Set<XBreakpoint<MyBreakpointProperties>> breakpoints = breakpointManager.getDefaultBreakpoints(type);
    assertEquals(2, breakpoints.size());
    assertTrue(ContainerUtil.exists(breakpoints, breakpoint -> {
      MyBreakpointProperties properties = assertInstanceOf(MyBreakpointProperties.class, breakpoint.getProperties());
      return "existing".equals(properties.myOption);
    }));
    assertTrue(ContainerUtil.exists(breakpoints, breakpoint -> {
      MyBreakpointProperties properties = assertInstanceOf(MyBreakpointProperties.class, breakpoint.getProperties());
      return "from-extension".equals(properties.myOption) && breakpoint.getSuspendPolicy() == SuspendPolicy.NONE;
    }));
  }

  @Test
  public void testExtensionAddedDeduplicatesDefaultBreakpointWithSameState(@TestDisposable Disposable disposable) {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties> type =
      createExtensionType("from-extension", SuspendPolicy.NONE);

    XBreakpoint<MyBreakpointProperties> existing = breakpointManager.addDefaultBreakpoint(type, new MyBreakpointProperties("from-extension"));
    existing.setEnabled(true);
    existing.setSuspendPolicy(SuspendPolicy.NONE);
    assertEquals(1, breakpointManager.getDefaultBreakpoints(type).size());

    XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(type, disposable);

    Set<XBreakpoint<MyBreakpointProperties>> breakpoints = breakpointManager.getDefaultBreakpoints(type);
    assertThat(breakpoints).hasSize(1);
    XBreakpoint<MyBreakpointProperties> breakpoint = assertThat(breakpoints).singleElement().actual();
    assertEquals("from-extension", assertInstanceOf(MyBreakpointProperties.class, breakpoint.getProperties()).myOption);
    assertTrue(breakpoint.isEnabled());
    assertEquals(SuspendPolicy.NONE, breakpoint.getSuspendPolicy());
  }

  @Test
  public void testListener() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    final StringBuilder out = new StringBuilder();
    XBreakpointListener<XLineBreakpoint<MyBreakpointProperties>> listener = new XBreakpointListener<>() {
      @Override
      public void breakpointAdded(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("added[").append(breakpoint.getProperties().myOption).append("];");
      }

      @Override
      public void breakpointRemoved(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("removed[").append(breakpoint.getProperties().myOption).append("];");
      }

      @Override
      public void breakpointChanged(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("changed[").append(breakpoint.getProperties().myOption).append("];");
      }
    };
    breakpointManager.addBreakpointListener(MY_LINE_BREAKPOINT_TYPE, listener);

    XBreakpoint<MyBreakpointProperties> breakpoint = fixture.addLineBreakpoint("url", 239, new MyBreakpointProperties("abc"));
    fixture.addBreakpoint(new MyBreakpointProperties("321"));
    fixture.removeBreakpoint(breakpoint);
    assertEquals("added[abc];removed[abc];", out.toString());

    breakpointManager.removeBreakpointListener(MY_LINE_BREAKPOINT_TYPE, listener);
    out.setLength(0);
    fixture.addLineBreakpoint("url", 239, new MyBreakpointProperties("a"));
    assertEquals("", out.toString());
  }

  @Test
  public void testRemoveFile() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    VirtualFile file = fixture.createFile("breakpoint.txt");
    fixture.addLineBreakpoint(file.getUrl(), 0, null);
    assertThat(breakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)).hasSize(1);
    fixture.delete(file);
    assertThat(breakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)).isEmpty();
  }

  @Test
  public void testConditionConvert() throws IOException, JDOMException {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    String condition = "old-style condition";
    String logExpression = "old-style expression";
    String oldStyle =
    "<breakpoint-manager>" +
    "<breakpoints>" +
    "<line-breakpoint enabled=\"true\" type=\"" + MY_LINE_BREAKPOINT_TYPE.getId() + "\">" +
    "      <condition>" + condition + "</condition>" +
    "      <option name=\"temporary\" value=\"true\" />" +
    "      <url>url</url>" +
    "      <log-expression>" + logExpression + "</log-expression>" +
    "</line-breakpoint>" +
    "</breakpoints>" +
    "<option name=\"time\" value=\"1\" />" +
    "</breakpoint-manager>";
    fixture.load(JDOMUtil.load(oldStyle));
    XLineBreakpoint<MyBreakpointProperties> breakpoint = Assertions.<XLineBreakpoint<MyBreakpointProperties>>assertThat(breakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)).singleElement().actual();
    assertEquals(condition, breakpoint.getConditionExpression().getExpression());
    assertEquals(logExpression, breakpoint.getLogExpressionObject().getExpression());
    assertTrue(breakpoint.isTemporary());
  }

  @Test
  public void testPresentationUpdateEmitsBreakpointChange() {
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      addLineBreakpoint(myBreakpointManager, "url", 239, new MyBreakpointProperties("123"));
    @SuppressWarnings("unchecked")
    var changedFlow = (MutableSharedFlow<Unit>)((XBreakpointBase<?, ?, ?>)breakpoint).breakpointChangedFlow();
    changedFlow.resetReplayCache();

    // the tooltip in the breakpoint state contains the error message, so the state must be sent again
    myBreakpointManager.updateBreakpointPresentation(breakpoint, null, "error message");

    assertThat(changedFlow.getReplayCache()).isNotEmpty();
  }

  @Test
  public void testSessionMuteDisablesAndRestoresBreakpoints() throws ExecutionException {
    var calls = new ArrayList<String>();
    var session = startSession(calls);
    try {
      assertThat(calls).containsExactly("register default");
      calls.clear();

      session.setBreakpointMuted(true);
      assertThat(calls).containsExactly("unregister default true");
      session.setBreakpointMuted(true);
      assertThat(calls).containsExactly("unregister default true");
      session.setBreakpointMuted(false);
      assertThat(calls).containsExactly("unregister default true", "register default");
    }
    finally {
      session.stop();
    }
  }

  @Test
  public void testSessionEnablesDependentBreakpointAfterMasterHit() throws ExecutionException {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XBreakpointManagerImpl breakpointManager = fixture.getBreakpointManager();
    var master = fixture.addBreakpoint(new MyBreakpointProperties("master"));
    var slave = fixture.addBreakpoint(new MyBreakpointProperties("slave"));
    var dependencies = breakpointManager.getDependentBreakpointManager();
    dependencies.setMasterBreakpoint(slave, master, false);
    master.setSuspendPolicy(SuspendPolicy.NONE);
    slave.setSuspendPolicy(SuspendPolicy.NONE);
    var calls = new ArrayList<String>();
    var session = startSession(calls);
    try {
      assertThat(calls).containsExactlyInAnyOrder("register default", "register master");
      calls.clear();

      session.breakpointReached(master, null, new XSuspendContext() {});
      assertThat(calls).containsExactly("register slave");
      session.breakpointReached(slave, null, new XSuspendContext() {});
      assertThat(calls).containsExactly("register slave", "unregister slave false");
      dependencies.clearMasterBreakpoint(slave);
      assertThat(calls).containsExactly("register slave", "unregister slave false", "register slave");
    }
    finally {
      session.stop();
    }
  }

  @Test
  public void testSessionDisablesBreakpointsBeforeStepAndRestoresBeforeResume() throws ExecutionException {
    var calls = new ArrayList<String>();
    var session = startSession(calls);
    try {
      calls.clear();
      session.stepOver(true);
      assertThat(calls).containsExactly("unregister default true", "step over");
      session.setBreakpointMuted(true);
      session.setBreakpointMuted(false);
      assertThat(calls).containsExactly("unregister default true", "step over");

      session.positionReached(new XSuspendContext() {}, false);
      session.resume();
      assertThat(calls).containsExactly("unregister default true", "step over", "register default", "resume");
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
      session.stop();
    }
  }

  private XBreakpoint<MyBreakpointProperties> getSingleBreakpoint() {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    return Assertions.<XBreakpoint<MyBreakpointProperties>>assertThat(breakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)).singleElement().actual();
  }

  private static @NotNull BreakpointState createDefaultState(@NotNull String typeId,
                                                             boolean enabled,
                                                             @NotNull SuspendPolicy suspendPolicy) {
    BreakpointState state = new BreakpointState();
    state.setTypeId(typeId);
    state.setEnabled(enabled);
    state.setSuspendPolicy(suspendPolicy);
    return state;
  }

  private static @NotNull BreakpointState createDefaultState(@NotNull String typeId,
                                                             boolean enabled,
                                                             @NotNull SuspendPolicy suspendPolicy,
                                                             long timeStamp) {
    BreakpointState state = createDefaultState(typeId, enabled, suspendPolicy);
    state.setTimeStamp(timeStamp);
    return state;
  }

  private static @NotNull XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties> createExtensionType(
    @NotNull String defaultOption,
    @NotNull SuspendPolicy suspendPolicy
  ) {
    String typeId = "testExtensionType" + System.nanoTime();
    return new XBreakpointType<>(typeId, "239") {
      @Override
      public String getDisplayText(XBreakpoint<MyBreakpointProperties> breakpoint) {
        return "";
      }

      @Override
      public MyBreakpointProperties createProperties() {
        return new MyBreakpointProperties();
      }

      @Override
      public XBreakpoint<MyBreakpointProperties> createDefaultBreakpoint(@NotNull XBreakpointCreator<MyBreakpointProperties> creator) {
        XBreakpoint<MyBreakpointProperties> breakpoint = creator.createBreakpoint(new MyBreakpointProperties(defaultOption));
        breakpoint.setEnabled(true);
        breakpoint.setSuspendPolicy(suspendPolicy);
        return breakpoint;
      }
    };
  }

  private void reload() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    Element element = fixture.save();
    fixture.load(element);
  }

  private void assertLookupForPlacement(@NotNull VirtualFile file,
                                        @NotNull XLineBreakpoint<MyBreakpointProperties> breakpoint,
                                        @NotNull XLineBreakpointVerticalPlacement expectedPlacement) {
    XBreakpointManagerImpl breakpointManager = breakpointsFixture.get().getBreakpointManager();
    assertEquals(expectedPlacement, breakpoint.getPlacement());
    switch (expectedPlacement) {
      case ON_LINE -> {
        assertSame(breakpoint, breakpointManager.findBreakpointAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0));
        assertSame(breakpoint, assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0,
                                                                                  XLineBreakpointVerticalPlacement.ON_LINE)).singleElement().actual());
        assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0,
                                                           XLineBreakpointVerticalPlacement.INTER_LINE)).isEmpty();
      }
      case INTER_LINE -> {
        assertNull(breakpointManager.findBreakpointAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0));
        assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0,
                                                           XLineBreakpointVerticalPlacement.ON_LINE)).isEmpty();
        assertSame(breakpoint, assertThat(breakpointManager.findBreakpointsAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0,
                                                                                  XLineBreakpointVerticalPlacement.INTER_LINE)).singleElement().actual());
      }
    }
  }

  private XDebugSession startSession(List<String> calls) throws ExecutionException {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    var handler = new RecordingBreakpointHandler(calls);
    return XDebuggerManager.getInstance(fixture.getProject()).newSessionBuilder(new XDebugProcessStarter() {
      @Override
      public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
        return new XDebugProcess(session) {
          @Override
          public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
            return new XBreakpointHandler<?>[]{handler};
          }

          @Override
          public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
            return new XDebuggerEditorsProvider() {
              @Override
              public @NotNull FileType getFileType() {
                return PlainTextFileType.INSTANCE;
              }

              @Override
              public @NotNull Document createDocument(@NotNull Project project,
                                                      @NotNull XExpression expression,
                                                     @Nullable XSourcePosition sourcePosition,
                                                     @NotNull EvaluationMode mode) {
                return EditorFactory.getInstance().createDocument(expression.getExpression());
              }
            };
          }

          @Override
          public void stop() {
          }

          @Override
          public void startStepOver(XSuspendContext context) {
            calls.add("step over");
          }

          @Override
          public void resume(XSuspendContext context) {
            calls.add("resume");
          }
        };
      }
    }).sessionName("test").showTab(true).showToolWindowOnSuspendOnly(true).startSession().getSession();
  }

  private static final class RecordingBreakpointHandler extends XBreakpointHandler<XBreakpoint<MyBreakpointProperties>> {
    private final List<String> calls;

    private RecordingBreakpointHandler(List<String> calls) {
      super(MySimpleBreakpointType.class);
      this.calls = calls;
    }

    @Override
    public void registerBreakpoint(@NotNull XBreakpoint<MyBreakpointProperties> breakpoint) {
      calls.add("register " + breakpoint.getProperties().myOption);
    }

    @Override
    public void unregisterBreakpoint(@NotNull XBreakpoint<MyBreakpointProperties> breakpoint, boolean temporary) {
      calls.add("unregister " + breakpoint.getProperties().myOption + " " + temporary);
    }
  }
}
