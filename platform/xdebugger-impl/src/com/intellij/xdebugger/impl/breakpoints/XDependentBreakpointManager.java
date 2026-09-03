// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
@ApiStatus.Internal
public final class XDependentBreakpointManager {
  private final Map<XBreakpoint<?>,  XDependentBreakpointInfo> mySlave2Info = new HashMap<>();
  private final MultiMap<XBreakpointBase, XDependentBreakpointInfo> myMaster2Info = new MultiMap<>();
  private final XBreakpointManagerImpl myBreakpointManager;
  private final XDependentBreakpointListener myEventPublisher;

  public XDependentBreakpointManager(@NotNull XBreakpointManagerImpl breakpointManager) {
    myBreakpointManager = breakpointManager;
    myEventPublisher = breakpointManager.getProject().getMessageBus().syncPublisher(XDependentBreakpointListener.TOPIC);
  }

  @NotNull List<XBreakpoint<?>> onBreakpointRemoved(final @NotNull XBreakpoint<?> breakpoint) {
    assert myBreakpointManager.isStateLockHeldByCurrentThread();
    XDependentBreakpointInfo info = mySlave2Info.remove(breakpoint);
    if (info != null) {
      myMaster2Info.remove(info.myMasterBreakpoint, info);
    }

    Collection<XDependentBreakpointInfo> infos = myMaster2Info.remove((XBreakpointBase)breakpoint);
    if (infos == null) {
      return Collections.emptyList();
    }

    List<XBreakpoint<?>> result = new SmartList<>();
    for (XDependentBreakpointInfo breakpointInfo : infos) {
      XDependentBreakpointInfo removed = mySlave2Info.remove(breakpointInfo.mySlaveBreakpoint);
      if (removed != null) {
        result.add(breakpointInfo.mySlaveBreakpoint);
      }
    }
    return result;
  }

  void fireDependenciesCleared(@NotNull Collection<? extends XBreakpoint<?>> breakpoints) {
    assert myBreakpointManager.isStateLockHeldByCurrentThread();
    breakpoints.forEach(myEventPublisher::dependencyCleared);
  }

  public void loadState() {
    assert myBreakpointManager.isStateLockHeldByCurrentThread();
    Map<String, XBreakpointBase<?,?,?>> id2Breakpoint = new HashMap<>();
    XBreakpointBase<?, ?, ?>[] allBreakpoints = myBreakpointManager.getAllBreakpoints();
    for (XBreakpointBase<?,?,?> breakpoint : allBreakpoints) {
      XBreakpointDependencyState state = breakpoint.getDependencyState();
      if (state != null) {
        String id = state.getId();
        if (id != null) {
          id2Breakpoint.put(id, breakpoint);
        }
      }
    }
    mySlave2Info.clear();
    myMaster2Info.clear();
    for (XBreakpointBase<?, ?, ?> breakpoint : allBreakpoints) {
      XBreakpointDependencyState state = breakpoint.getDependencyState();
      if (state != null) {
        String masterId = state.getMasterBreakpointId();
        if (masterId != null) {
          XBreakpointBase<?, ?, ?> master = id2Breakpoint.get(masterId);
          if (master != null) {
            addDependency(master, breakpoint, state.isLeaveEnabled());
          }
        }
      }
    }
  }

  public void saveState() {
    assert myBreakpointManager.isStateLockHeldByCurrentThread();
    Map<XBreakpointBase<?, ?, ?>, String> breakpointToId = new HashMap<>();
    int id = 0;
    for (XBreakpointBase breakpoint : myMaster2Info.keySet()) {
      breakpointToId.put(breakpoint, String.valueOf(id++));
    }

    for (XDependentBreakpointInfo info : mySlave2Info.values()) {
      XBreakpointDependencyState state = new XBreakpointDependencyState(breakpointToId.get(info.mySlaveBreakpoint),
                                                                        breakpointToId.get(info.myMasterBreakpoint),
                                                                        info.myLeaveEnabled);
      info.mySlaveBreakpoint.setDependencyState(state);
    }

    for (Map.Entry<XBreakpointBase<?, ?, ?>, String> entry : breakpointToId.entrySet()) {
      if (!mySlave2Info.containsKey(entry.getKey())) {
        entry.getKey().setDependencyState(new XBreakpointDependencyState(entry.getValue()));
      }
    }
  }

  public void setMasterBreakpoint(@NotNull XBreakpoint<?> slave, @NotNull XBreakpoint<?> master, boolean leaveEnabled) {
    withStateLock(() -> {
      XDependentBreakpointInfo info = mySlave2Info.get(slave);
      if (info == null) {
        addDependency((XBreakpointBase<?, ?, ?>)master, (XBreakpointBase<?, ?, ?>)slave, leaveEnabled);
      }
      else if (info.myMasterBreakpoint == master) {
        info.myLeaveEnabled = leaveEnabled;
      }
      else {
        myMaster2Info.remove(info.myMasterBreakpoint, info);
        info.myMasterBreakpoint = (XBreakpointBase)master;
        info.myLeaveEnabled = leaveEnabled;
        myMaster2Info.putValue((XBreakpointBase)master, info);
      }
    });
    myEventPublisher.dependencySet(slave, master);
  }

  void copyMasterBreakpoint(@NotNull XBreakpoint<?> source, @NotNull XBreakpoint<?> copy) {
    assert myBreakpointManager.isStateLockHeldByCurrentThread();
    XDependentBreakpointInfo info = mySlave2Info.get(source);
    if (info == null) {
      return;
    }
    addDependency(info.myMasterBreakpoint, (XBreakpointBase<?, ?, ?>)copy, info.myLeaveEnabled);
    myEventPublisher.dependencySet(copy, info.myMasterBreakpoint);
  }

  public void clearMasterBreakpoint(@NotNull XBreakpoint<?> slave) {
    boolean dependencyCleared = withStateLock(() -> {
      XDependentBreakpointInfo info = mySlave2Info.remove(slave);
      if (info == null) {
        return false;
      }
      myMaster2Info.remove(info.myMasterBreakpoint, info);
      return true;
    });
    if (dependencyCleared) {
      myEventPublisher.dependencyCleared(slave);
    }
  }

  private void addDependency(@NotNull XBreakpointBase<?, ?, ?> master, final XBreakpointBase<?, ?, ?> slave, final boolean leaveEnabled) {
    assert myBreakpointManager.isStateLockHeldByCurrentThread();
    XDependentBreakpointInfo info = new XDependentBreakpointInfo(master, slave, leaveEnabled);
    mySlave2Info.put(slave, info);
    myMaster2Info.putValue(master, info);
  }

  public @Nullable XBreakpoint<?> getMasterBreakpoint(@NotNull XBreakpoint<?> slave) {
    return withStateLock(() -> {
      XDependentBreakpointInfo info = mySlave2Info.get(slave);
      return info != null ? info.myMasterBreakpoint : null;
    });
  }

  public boolean isLeaveEnabled(@NotNull XBreakpoint<?> slave) {
    return withStateLock(() -> {
      XDependentBreakpointInfo info = mySlave2Info.get(slave);
      return info != null && info.myLeaveEnabled;
    });
  }

  public List<XBreakpoint<?>> getSlaveBreakpoints(final XBreakpoint<?> breakpoint) {
    return withStateLock(() -> {
      Collection<XDependentBreakpointInfo> slaveInfos = myMaster2Info.get((XBreakpointBase)breakpoint);
      if (slaveInfos.isEmpty()) {
        return Collections.emptyList();
      }
      List<XBreakpoint<?>> breakpoints = new SmartList<>();
      for (XDependentBreakpointInfo slaveInfo : slaveInfos) {
        breakpoints.add(slaveInfo.mySlaveBreakpoint);
      }
      return breakpoints;
    });
  }

  public boolean isMasterOrSlave(final XBreakpoint<?> breakpoint) {
    return withStateLock(() -> myMaster2Info.containsKey((XBreakpointBase)breakpoint) || mySlave2Info.containsKey(breakpoint));
  }

  public Set<XBreakpoint<?>> getAllSlaveBreakpoints() {
    return withStateLock(() -> new HashSet<>(mySlave2Info.keySet()));
  }

  private <T> T withStateLock(@NotNull Supplier<T> action) {
    return myBreakpointManager.withStateLock(action);
  }

  private void withStateLock(@NotNull Runnable action) {
    myBreakpointManager.withStateLock(action);
  }

  private static final class XDependentBreakpointInfo {
    private XBreakpointBase myMasterBreakpoint;
    private final XBreakpointBase mySlaveBreakpoint;
    private boolean myLeaveEnabled;

    private XDependentBreakpointInfo(final @NotNull XBreakpointBase masterBreakpoint, final XBreakpointBase slaveBreakpoint, final boolean leaveEnabled) {
      myMasterBreakpoint = masterBreakpoint;
      myLeaveEnabled = leaveEnabled;
      mySlaveBreakpoint = slaveBreakpoint;
    }
  }

  class DependenciesData {
    private final Collection<XDependentBreakpointInfo> myDependencies = new SmartList<>();
    private final XBreakpointBase myBreakpoint;

    DependenciesData(XBreakpointBase breakpoint) {
      assert myBreakpointManager.isStateLockHeldByCurrentThread();
      myBreakpoint = breakpoint;
      ContainerUtil.addIfNotNull(myDependencies, mySlave2Info.get(breakpoint));
      myDependencies.addAll(myMaster2Info.get(breakpoint));
    }

    void restore(XBreakpointBase breakpoint) {
      assert myBreakpointManager.isStateLockHeldByCurrentThread();
      myDependencies.forEach(d -> addDependency(replaceSelf(d.myMasterBreakpoint, breakpoint),
                                                replaceSelf(d.mySlaveBreakpoint, breakpoint),
                                                d.myLeaveEnabled));
    }

    private XBreakpointBase replaceSelf(XBreakpointBase breakpoint, XBreakpointBase self) {
      return breakpoint == myBreakpoint ? self : breakpoint;
    }
  }
}
