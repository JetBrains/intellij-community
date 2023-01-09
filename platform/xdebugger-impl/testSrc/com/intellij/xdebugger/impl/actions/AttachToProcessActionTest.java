// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.attach.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.xdebugger.impl.actions.AttachToProcessAction.*;

public class AttachToProcessActionTest extends HeavyPlatformTestCase {

  @NotNull
  private AttachToProcessItem fixtureCreateAttachToProcessItem(@NotNull XAttachPresentationGroup<ProcessInfo> group,
                                                               boolean firstInGroup,
                                                               @NotNull ProcessInfo info,
                                                               @NotNull List<XAttachDebugger> debuggers,
                                                               @NotNull UserDataHolder dataHolder) {
    List<XAttachDebugger> attachDebuggers = new ArrayList<>(debuggers);
    return new AttachToProcessItem(group, firstInGroup, LocalAttachHost.INSTANCE, info, attachDebuggers, getProject(), dataHolder);
  }

  @NotNull
  private static RecentItem fixtureCreateHistoryItem(@NotNull ProcessInfo info,
                                                     @NotNull XAttachPresentationGroup group,
                                                     @NotNull String debuggerName) {
    return RecentItem.createRecentItem(LocalAttachHost.INSTANCE, info, group, debuggerName);
  }

  private List<AttachToProcessItem> fixtureCollectAttachItems(ProcessInfo[] infos, XAttachDebuggerProvider @NotNull ... providers) {
    List<ProcessInfo> infoList = List.of(infos);
    return doCollectAttachProcessItems(getProject(), LocalAttachHost.INSTANCE, infoList, DumbProgressIndicator.INSTANCE, Arrays.asList(providers));
  }

  public void testCollectingAttachItems_Empty() {
    // no providers
    assertItems("");

    // no provided debuggers
    assertItems("", new TestDebuggerProvider(), new TestDebuggerProvider());
  }

  public void testCollectingAttachItems_OneDebugger() {
    assertItems("""
                  --------
                  1 exec1: dbg
                  2 exec2: dbg
                  """,
                new TestDebuggerProvider("dbg"));
  }

  public void testCollectingAttachItems_DebuggerPerProcess() {
    // from one provider
    assertItems("""
                  --------
                  1 exec1: dbg1
                  2 exec2: dbg2
                  """,
                new TestDebuggerProvider(1, TEST_GROUP, "dbg1"),
                new TestDebuggerProvider(2, TEST_GROUP, "dbg2"));
  }

  public void testCollectingAttachItems_SeveralDebuggers() {
    // from one provider
    assertItems("""
                  --------
                  1 exec1: dbg1
                      dbg1
                      dbg2
                      dbg3
                  2 exec2: dbg1
                      dbg1
                      dbg2
                      dbg3
                  """,
                new TestDebuggerProvider("dbg1", "dbg2", "dbg3"));

    // from several providers
    assertItems("""
                  --------
                  1 exec1: dbg1
                      dbg1
                      dbg2
                      dbg3
                  2 exec2: dbg1
                      dbg1
                      dbg2
                      dbg3
                  """,
                new TestDebuggerProvider("dbg1"),
                new TestDebuggerProvider("dbg2", "dbg3"));

    // keep order
    assertItems("""
                  --------
                  1 exec1: dbg3
                      dbg3
                      dbg2
                      dbg1
                  2 exec2: dbg3
                      dbg3
                      dbg2
                      dbg1
                  """,
                new TestDebuggerProvider("dbg3"),
                new TestDebuggerProvider("dbg2", "dbg1"));

    // several debuggers with same display name
    assertItems("""
                  --------
                  1 exec1: dbg
                      dbg
                      dbg
                      dbg
                  2 exec2: dbg
                      dbg
                      dbg
                      dbg
                  """,
                new TestDebuggerProvider("dbg", "dbg"),
                new TestDebuggerProvider("dbg"));
  }

  public void testCollectingAttachItems_Groups() {
    // one group
    assertItems("""
                  ----group----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  """,
                new TestDebuggerProvider(new TestAttachGroup("group", 0), "dbg1", "dbg2"));

    // merging same group
    TestAttachGroup group = new TestAttachGroup("group", 0);
    assertItems("""
                  ----group----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  """,
                new TestDebuggerProvider(group, "dbg1"),
                new TestDebuggerProvider(group, "dbg2"));

    assertItems("""
                  --------
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  --------
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  """,
                new TestDebuggerProvider(TEST_GROUP, "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("", 1), "dbg1", "dbg2"));
  }

  public void testCollectingAttachItems_Groups_SortingGroups() {
    assertItems("""
                  ----group1----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  ----group2----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  """,
                new TestDebuggerProvider(new TestAttachGroup("group1", 1), "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group2", 2), "dbg1", "dbg2"));
    assertItems("""
                  ----group2----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  ----group1----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  """,
                new TestDebuggerProvider(new TestAttachGroup("group1", 2), "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group2", 1), "dbg1", "dbg2"));

    // sorting with default group
    assertItems("""
                  ----group2----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  --------
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  ----group1----
                  1 exec1: dbg1
                      dbg1
                      dbg2
                  2 exec2: dbg1
                      dbg1
                      dbg2
                  """,
                new TestDebuggerProvider(TEST_GROUP, "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group1", 1), "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group2", -1), "dbg1", "dbg2"));
  }

  public void testCollectingAttachItems_Groups_SortingItems() {
    assertItems("""
                  ----group----
                  1 exec1: dbg1
                  2 exec2: dbg1
                  """,
                new TestDebuggerProvider(new TestAttachGroup("group", 0), "dbg1"));
    assertItems("""
                  ----group----
                  2 exec2: dbg1
                  1 exec1: dbg1
                  """,
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @Override
                  public int compare(@NotNull ProcessInfo a, @NotNull ProcessInfo b) {
                    return b.getPid() - a.getPid();
                  }
                }, "dbg1"));
  }

  public void testCollectingAttachItems_Groups_CustomItemTitles() {
    assertItems("""
                  ----group----
                  1 custom: dbg1
                  2 custom: dbg1
                  """,
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @Nls
                  @Override
                  public @NotNull String getItemDisplayText(@NotNull Project project,
                                                            @NotNull ProcessInfo info,
                                                            @NotNull UserDataHolder dataHolder) {
                    return "custom";
                  }
                }, "dbg1"));
  }

  public void testHistory() {
    ProcessInfo info1 = new ProcessInfo(1, "command line 1", "exec1", "args1");
    ProcessInfo info2 = new ProcessInfo(1, "command line 2", "exec1", "args1");
    ProcessInfo info3 = new ProcessInfo(1, "command line 3", "exec1", "args1");
    ProcessInfo info4 = new ProcessInfo(1, "command line 4", "exec1", "args1");
    ProcessInfo info5 = new ProcessInfo(1, "command line 5", "exec1", "args1");

    List<XAttachDebugger> debuggers = createDebuggers("gdb");
    UserDataHolderBase dataHolder = new UserDataHolderBase();
    AttachToProcessItem item1 = fixtureCreateAttachToProcessItem(TEST_GROUP, true, info1, debuggers, dataHolder);
    AttachToProcessItem item2 = fixtureCreateAttachToProcessItem(TEST_GROUP, true, info2, debuggers, dataHolder);
    AttachToProcessItem item3 = fixtureCreateAttachToProcessItem(TEST_GROUP, true, info3, debuggers, dataHolder);
    AttachToProcessItem item4 = fixtureCreateAttachToProcessItem(TEST_GROUP, true, info4, debuggers, dataHolder);
    AttachToProcessItem item5 = fixtureCreateAttachToProcessItem(TEST_GROUP, true, info5, debuggers, dataHolder);

    RecentItem recentItem1 = fixtureCreateHistoryItem(info1, TEST_GROUP, "gdb");
    RecentItem recentItem2 = fixtureCreateHistoryItem(info2, TEST_GROUP, "gdb");
    RecentItem recentItem3 = fixtureCreateHistoryItem(info3, TEST_GROUP, "gdb");
    RecentItem recentItem4 = fixtureCreateHistoryItem(info4, TEST_GROUP, "gdb");
    RecentItem recentItem5 = fixtureCreateHistoryItem(info5, TEST_GROUP, "gdb");

    // empty
    assertEmpty(getRecentItems(LocalAttachHost.INSTANCE, getProject()));

    // adding some items
    addToRecent(getProject(), item1);
    addToRecent(getProject(), item2);

    assertOrderedEquals(getRecentItems(LocalAttachHost.INSTANCE, getProject()), recentItem1, recentItem2);

    addToRecent(getProject(), item3);
    addToRecent(getProject(), item4);

    assertOrderedEquals(getRecentItems(LocalAttachHost.INSTANCE, getProject()), recentItem1, recentItem2, recentItem3, recentItem4);

    // limiting size to 4 items
    addToRecent(getProject(), item5);
    assertOrderedEquals(getRecentItems(LocalAttachHost.INSTANCE, getProject()), recentItem2, recentItem3, recentItem4, recentItem5);

    // popping up recent items
    addToRecent(getProject(), item3);
    addToRecent(getProject(), item2);
    assertOrderedEquals(getRecentItems(LocalAttachHost.INSTANCE, getProject()), recentItem4, recentItem5, recentItem3, recentItem2);
  }

  public void testHistory_UpdatingPreviousItems() {
    TestAttachGroup group1 = new TestAttachGroup("group1", 1);
    TestAttachGroup group2 = new TestAttachGroup("group2", 2);

    ProcessInfo info1 = new ProcessInfo(1, "same command line", "exec1", "args1");
    ProcessInfo info2 = new ProcessInfo(2, "same command line", "exec2", "args2");

    UserDataHolderBase dataHolder = new UserDataHolderBase();
    AttachToProcessItem item1 = fixtureCreateAttachToProcessItem(group1, true, info1, createDebuggers("gdb1"), dataHolder);
    AttachToProcessItem item2 = fixtureCreateAttachToProcessItem(group2, true, info2, createDebuggers("gdb2"), dataHolder);

    RecentItem recentItem1 = fixtureCreateHistoryItem(info1, group1, "gdb1");
    RecentItem recentItem2 = fixtureCreateHistoryItem(info2, group2, "gdb2");

    addToRecent(getProject(), item1);
    assertOrderedEquals(getRecentItems(LocalAttachHost.INSTANCE, getProject()), recentItem1);
    addToRecent(getProject(), item2);
    assertOrderedEquals(getRecentItems(LocalAttachHost.INSTANCE, getProject()), recentItem2);
  }

  public void testHistoryGroup() {
    TestAttachGroup group1 = new TestAttachGroup("group1", 1);
    TestAttachGroup group2 = new TestAttachGroup("group2", 2);
    List<XAttachDebugger> debuggers1 = createDebuggers("gdb1", "lldb1");
    List<XAttachDebugger> debuggers2 = createDebuggers("gdb2", "lldb2");

    List<AttachToProcessItem> originalItems = fixtureCollectAttachItems(new ProcessInfo[]{
                                                          new ProcessInfo(1, "command line 1", "exec1", "args1"),
                                                          new ProcessInfo(2, "command line 2", "exec2", "args2")},
                                                        new TestDebuggerProvider(1, group1, debuggers1),
                                                        new TestDebuggerProvider(2, group2, debuggers2));

    // one item in history
    addToRecent(getProject(), originalItems.get(0));
    assertItems("""
                  ----Recent----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // several items in history
    addToRecent(getProject(), originalItems.get(1));
    assertItems("""
                  ----Recent----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // put most recent item on top
    addToRecent(getProject(), originalItems.get(0));
    assertItems("""
                  ----Recent----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // put debugger used in history item on top
    addToRecent(getProject(), originalItems.get(0).getSubItems().get(1));
    addToRecent(getProject(), originalItems.get(1).getSubItems().get(1));
    assertItems("""
                  ----Recent----
                  20 exec20: lldb2
                      gdb2
                      lldb2
                  10 exec10: lldb1
                      gdb1
                      lldb1
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // filter unavailable history items
    assertItems("""
                  ----Recent----
                  20 exec20: lldb2
                      gdb2
                      lldb2
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 10", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));
    assertItems("""
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 10", "exec10", "args10"),
                  new ProcessInfo(20, "command line 20", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));
    // history items available again:
    assertItems("""
                  ----Recent----
                  20 exec20: lldb2
                      gdb2
                      lldb2
                  10 exec10: lldb1
                      gdb1
                      lldb1
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // filter items from history by suitable group
    assertItems("""
                  ----Recent----
                  10 exec10: lldb1
                      gdb1
                      lldb1
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group1, debuggers2));
    assertItems("""
                  ----Recent----
                  20 exec20: lldb2
                      gdb2
                      lldb2
                  ----group2----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group2, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));
    // filter by group equality, not by name
    assertItems("""
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, new TestAttachGroup(group1.getGroupName(), group1.getOrder()), debuggers1),
                new TestDebuggerProvider(20, new TestAttachGroup(group2.getGroupName(), group2.getOrder()), debuggers2));

    // filter items from history by available debugger
    assertItems("""
                  ----Recent----
                  10 exec10: lldb1
                      gdb1
                      lldb1
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb1
                      gdb1
                      lldb1
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers1));

    // filter debuggers by name, not by equality
    assertItems("""
                  ----Recent----
                  20 exec20: lldb2
                      gdb2
                      lldb2
                  10 exec10: lldb1
                      gdb1
                      lldb1
                  ----group1----
                  10 exec10: gdb1
                      gdb1
                      lldb1
                  ----group2----
                  20 exec20: gdb2
                      gdb2
                      lldb2
                  """,
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, createDebuggers("gdb1", "lldb1")),
                new TestDebuggerProvider(20, group2, createDebuggers("gdb2", "lldb2")));
  }

  private void assertItems(String expected, XAttachDebuggerProvider @NotNull ... providers) {
    ProcessInfo[] infos = {
      new ProcessInfo(1, "command line 1", "exec1", "args1"),
      new ProcessInfo(2, "command line 2", "exec2", "args2"),
    };
    assertItems(expected, infos, providers);
  }

  private void assertItems(String expected, ProcessInfo[] infos, XAttachDebuggerProvider @NotNull ... providers) {
    assertEquals(expected, printItems(fixtureCollectAttachItems(infos, providers)));
  }

  private String printItems(List<AttachToProcessItem> items) {
    StringBuilder builder = new StringBuilder();

    for (AttachToProcessItem each : items) {
      String title = each.getSeparatorTitle();
      if (title != null) builder.append("----").append(title).append("----\n");
      builder.append(each.getText(getProject())).append(": ").append(each.getSelectedDebugger().getDebuggerDisplayName()).append("\n");
      for (AttachToProcessItem eachSubItem : each.getSubItems()) {
        builder.append("    ").append(eachSubItem.getSelectedDebugger().getDebuggerDisplayName()).append("\n");
      }
    }

    return builder.toString();
  }

  @NotNull
  private static List<XAttachDebugger> createDebuggers(String... names) {
    return ContainerUtil.map(names, s -> new XAttachDebugger() {
      @NotNull
      @Override
      public String getDebuggerDisplayName() {
        return s;
      }

      @Override
      public void attachDebugSession(@NotNull Project project, @NotNull XAttachHost attachHost, @NotNull ProcessInfo processInfo)
        throws ExecutionException {

      }
    });
  }

  private static final XAttachProcessPresentationGroup TEST_GROUP = new TestAttachGroup("", 0);

  private static class TestAttachGroup implements XAttachProcessPresentationGroup {
    @Nullable String myName;
    @Nullable Integer myOrder;

    TestAttachGroup(@Nullable String name, @Nullable Integer order) {
      myName = name;
      myOrder = order;
    }

    @Override
    public int getOrder() {
      return myOrder != null ? myOrder : 0;
    }

    @NotNull
    @Override
    public String getGroupName() {
      return myName != null ? myName : "";
    }

    @Override
    public @NotNull Icon getItemIcon(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      return AllIcons.RunConfigurations.Application;
    }

    @Nls
    @Override
    public @NotNull String getItemDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      return info.getExecutableDisplayName();
    }
  }

  private static class TestDebuggerProvider implements XAttachDebuggerProvider {
    @Nullable private final Integer myFilterPID;
    @NotNull private final XAttachProcessPresentationGroup myGroup;
    @NotNull private final List<XAttachDebugger> myDebuggers;

    TestDebuggerProvider(@Nullable Integer filterPID,
                         @NotNull XAttachProcessPresentationGroup group,
                         @NotNull List<XAttachDebugger> debuggers) {
      myFilterPID = filterPID;
      myGroup = group;
      myDebuggers = debuggers;
    }

    TestDebuggerProvider(@Nullable Integer filterPID, @NotNull XAttachProcessPresentationGroup group, String... names) {
      this(filterPID, group, createDebuggers(names));
    }

    TestDebuggerProvider(@NotNull XAttachProcessPresentationGroup group, String... names) {
      this(null, group, names);
    }

    TestDebuggerProvider(String... names) {
      this(TEST_GROUP, names);
    }

    @Override
    public @NotNull XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
      return myGroup;
    }

    @Override
    public boolean isAttachHostApplicable(@NotNull XAttachHost attachHost) {
      return attachHost instanceof LocalAttachHost;
    }

    @Override
    public @NotNull List<XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                                @NotNull XAttachHost attachHost,
                                                                @NotNull ProcessInfo processInfo,
                                                                @NotNull UserDataHolder contextHolder) {
      if (myFilterPID != null && processInfo.getPid() != myFilterPID) return Collections.emptyList();
      return myDebuggers;
    }
  }
}
