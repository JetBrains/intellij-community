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
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.attach.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.xdebugger.impl.actions.AttachToProcessAction.*;

public class AttachToProcessActionTest extends PlatformTestCase {

  @NotNull
  private AttachToProcessItem fixtureCreateAttachToProcessItem(@NotNull XAttachPresentationGroup<ProcessInfo> group, boolean firstInGroup, @NotNull ProcessInfo info, @NotNull List<XLocalAttachDebugger> debuggers, @NotNull UserDataHolder dataHolder) {
    List<XAttachDebugger> attachDebuggers = ContainerUtil.newArrayList(debuggers);
    return new AttachToProcessItem(group, firstInGroup, LocalAttachHost.INSTANCE, info, attachDebuggers, getProject(), dataHolder);
  }

  @NotNull
  private RecentItem fixtureCreateHistoryItem(@NotNull ProcessInfo info, @NotNull XAttachPresentationGroup group, @NotNull String debuggerName) {
    return RecentItem.createRecentItem(LocalAttachHost.INSTANCE, info, group, debuggerName);
  }

  private List<AttachToProcessItem> fixtureCollectAttachItems(ProcessInfo[] infos, @NotNull XAttachDebuggerProvider... providers) {
    List<ProcessInfo> infoList = ContainerUtil.newArrayList(infos);
    return doCollectAttachProcessItems(getProject(), LocalAttachHost.INSTANCE, infoList, DumbProgressIndicator.INSTANCE, Arrays.asList(providers));
  }

  public void testCollectingAttachItems_Empty() {
    // no providers
    assertItems("");

    // no provided debuggers
    assertItems("", new TestDebuggerProvider(), new TestDebuggerProvider());
  }

  public void testCollectingAttachItems_OneDebugger() {
    assertItems("--------\n" +
                "1 exec1: dbg\n" +
                "2 exec2: dbg\n",
                new TestDebuggerProvider("dbg"));
  }

  public void testCollectingAttachItems_DebuggerPerProcess() {
    // from one provider
    assertItems("--------\n" +
                "1 exec1: dbg1\n" +
                "2 exec2: dbg2\n",
                new TestDebuggerProvider(1, XDefaultLocalAttachGroup.INSTANCE, "dbg1"),
                new TestDebuggerProvider(2, XDefaultLocalAttachGroup.INSTANCE, "dbg2"));
  }

  public void testCollectingAttachItems_SeveralDebuggers() {
    // from one provider
    assertItems("--------\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "    dbg3\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "    dbg3\n",
                new TestDebuggerProvider("dbg1", "dbg2", "dbg3"));

    // from several providers
    assertItems("--------\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "    dbg3\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "    dbg3\n",
                new TestDebuggerProvider("dbg1"),
                new TestDebuggerProvider("dbg2", "dbg3"));

    // keep order
    assertItems("--------\n" +
                "1 exec1: dbg3\n" +
                "    dbg3\n" +
                "    dbg2\n" +
                "    dbg1\n" +
                "2 exec2: dbg3\n" +
                "    dbg3\n" +
                "    dbg2\n" +
                "    dbg1\n",
                new TestDebuggerProvider("dbg3"),
                new TestDebuggerProvider("dbg2", "dbg1"));

    // several debuggers with same display name
    assertItems("--------\n" +
                "1 exec1: dbg\n" +
                "    dbg\n" +
                "    dbg\n" +
                "    dbg\n" +
                "2 exec2: dbg\n" +
                "    dbg\n" +
                "    dbg\n" +
                "    dbg\n",
                new TestDebuggerProvider("dbg", "dbg"),
                new TestDebuggerProvider("dbg"));
  }

  public void testCollectingAttachItems_Groups() {
    // one group
    assertItems("----group----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n",
                new TestDebuggerProvider(new TestAttachGroup("group", 0), "dbg1", "dbg2"));

    // merging same group
    TestAttachGroup group = new TestAttachGroup("group", 0);
    assertItems("----group----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n",
                new TestDebuggerProvider(group, "dbg1"),
                new TestDebuggerProvider(group, "dbg2"));

    assertItems("--------\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "--------\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n",
                new TestDebuggerProvider(XDefaultLocalAttachGroup.INSTANCE, "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("", 1), "dbg1", "dbg2"));
  }

  public void testCollectingAttachItems_Groups_SortingGroups() {
    assertItems("----group1----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "----group2----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n",
                new TestDebuggerProvider(new TestAttachGroup("group1", 1), "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group2", 2), "dbg1", "dbg2"));
    assertItems("----group2----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "----group1----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n",
                new TestDebuggerProvider(new TestAttachGroup("group1", 2), "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group2", 1), "dbg1", "dbg2"));

    // sorting with default group
    assertItems("----group2----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "--------\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "----group1----\n" +
                "1 exec1: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n" +
                "2 exec2: dbg1\n" +
                "    dbg1\n" +
                "    dbg2\n",
                new TestDebuggerProvider(XDefaultLocalAttachGroup.INSTANCE, "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group1", 1), "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group2", -1), "dbg1", "dbg2"));
  }

  public void testCollectingAttachItems_Groups_SortingItems() {
    assertItems("----group----\n" +
                "1 exec1: dbg1\n" +
                "2 exec2: dbg1\n",
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @Override
                  public int compare(@NotNull ProcessInfo a, @NotNull ProcessInfo b) {
                    return a.getPid() - b.getPid();
                  }
                }, "dbg1"));
    assertItems("----group----\n" +
                "2 exec2: dbg1\n" +
                "1 exec1: dbg1\n",
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @Override
                  public int compare(@NotNull ProcessInfo a, @NotNull ProcessInfo b) {
                    return b.getPid() - a.getPid();
                  }
                }, "dbg1"));
  }

  public void testCollectingAttachItems_Groups_CustomItemTitles() {
    assertItems("----group----\n" +
                "1 custom: dbg1\n" +
                "2 custom: dbg1\n",
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @NotNull
                  @Override
                  public String getProcessDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
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

    List<XLocalAttachDebugger> debuggers = createDebuggers("gdb");
    UserDataHolderBase dataHolder = new UserDataHolderBase();
    AttachToProcessItem item1 = fixtureCreateAttachToProcessItem(XDefaultLocalAttachGroup.INSTANCE, true, info1, debuggers, dataHolder);
    AttachToProcessItem item2 = fixtureCreateAttachToProcessItem(XDefaultLocalAttachGroup.INSTANCE, true, info2, debuggers, dataHolder);
    AttachToProcessItem item3 = fixtureCreateAttachToProcessItem(XDefaultLocalAttachGroup.INSTANCE, true, info3, debuggers, dataHolder);
    AttachToProcessItem item4 = fixtureCreateAttachToProcessItem(XDefaultLocalAttachGroup.INSTANCE, true, info4, debuggers, dataHolder);
    AttachToProcessItem item5 = fixtureCreateAttachToProcessItem(XDefaultLocalAttachGroup.INSTANCE, true, info5, debuggers, dataHolder);

    RecentItem recentItem1 = fixtureCreateHistoryItem(info1, XDefaultLocalAttachGroup.INSTANCE, "gdb");
    RecentItem recentItem2 = fixtureCreateHistoryItem(info2, XDefaultLocalAttachGroup.INSTANCE, "gdb");
    RecentItem recentItem3 = fixtureCreateHistoryItem(info3, XDefaultLocalAttachGroup.INSTANCE, "gdb");
    RecentItem recentItem4 = fixtureCreateHistoryItem(info4, XDefaultLocalAttachGroup.INSTANCE, "gdb");
    RecentItem recentItem5 = fixtureCreateHistoryItem(info5, XDefaultLocalAttachGroup.INSTANCE, "gdb");

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
    List<XLocalAttachDebugger> debuggers1 = createDebuggers("gdb1", "lldb1");
    List<XLocalAttachDebugger> debuggers2 = createDebuggers("gdb2", "lldb2");

    List<AttachToProcessItem> originalItems = fixtureCollectAttachItems(new ProcessInfo[]{
                                                          new ProcessInfo(1, "command line 1", "exec1", "args1"),
                                                          new ProcessInfo(2, "command line 2", "exec2", "args2")},
                                                        new TestDebuggerProvider(1, group1, debuggers1),
                                                        new TestDebuggerProvider(2, group2, debuggers2));

    // one item in history
    addToRecent(getProject(), originalItems.get(0));
    assertItems("----Recent----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // several items in history
    addToRecent(getProject(), originalItems.get(1));
    assertItems("----Recent----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // put most recent item on top
    addToRecent(getProject(), originalItems.get(0));
    assertItems("----Recent----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // put debugger used in history item on top
    addToRecent(getProject(), originalItems.get(0).getSubItems().get(1));
    addToRecent(getProject(), originalItems.get(1).getSubItems().get(1));
    assertItems("----Recent----\n" +
                "20 exec20: lldb2\n" +
                "    gdb2\n" +
                "    lldb2\n" +
                "10 exec10: lldb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // filter unavailable history items
    assertItems("----Recent----\n" +
                "20 exec20: lldb2\n" +
                "    gdb2\n" +
                "    lldb2\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 10", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));
    assertItems("----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 10", "exec10", "args10"),
                  new ProcessInfo(20, "command line 20", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));
    // history items available again:
    assertItems("----Recent----\n" +
                "20 exec20: lldb2\n" +
                "    gdb2\n" +
                "    lldb2\n" +
                "10 exec10: lldb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));

    // filter items from history by suitable group
    assertItems("----Recent----\n" +
                "10 exec10: lldb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group1, debuggers2));
    assertItems("----Recent----\n" +
                "20 exec20: lldb2\n" +
                "    gdb2\n" +
                "    lldb2\n" +
                "----group2----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group2, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers2));
    // filter by group equality, not by name
    assertItems("----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, new TestAttachGroup(group1.getGroupName(), group1.getOrder()), debuggers1),
                new TestDebuggerProvider(20, new TestAttachGroup(group2.getGroupName(), group2.getOrder()), debuggers2));

    // filter items from history by available debugger
    assertItems("----Recent----\n" +
                "10 exec10: lldb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, debuggers1),
                new TestDebuggerProvider(20, group2, debuggers1));

    // filter debuggers by name, not by equality
    assertItems("----Recent----\n" +
                "20 exec20: lldb2\n" +
                "    gdb2\n" +
                "    lldb2\n" +
                "10 exec10: lldb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group1----\n" +
                "10 exec10: gdb1\n" +
                "    gdb1\n" +
                "    lldb1\n" +
                "----group2----\n" +
                "20 exec20: gdb2\n" +
                "    gdb2\n" +
                "    lldb2\n",
                new ProcessInfo[]{
                  new ProcessInfo(10, "command line 1", "exec10", "args10"),
                  new ProcessInfo(20, "command line 2", "exec20", "args20")
                },
                new TestDebuggerProvider(10, group1, createDebuggers("gdb1", "lldb1")),
                new TestDebuggerProvider(20, group2, createDebuggers("gdb2", "lldb2")));
  }

  private void assertItems(String expected, @NotNull XLocalAttachDebuggerProvider... providers) {
    ProcessInfo[] infos = {
      new ProcessInfo(1, "command line 1", "exec1", "args1"),
      new ProcessInfo(2, "command line 2", "exec2", "args2"),
    };
    assertItems(expected, infos, providers);
  }

  private void assertItems(String expected, ProcessInfo[] infos, @NotNull XLocalAttachDebuggerProvider... providers) {
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
  private static List<XLocalAttachDebugger> createDebuggers(String... names) {
    return ContainerUtil.map(names, s -> new XLocalAttachDebugger() {
      @NotNull
      @Override
      public String getDebuggerDisplayName() {
        return s;
      }

      @Override
      public void attachDebugSession(@NotNull Project project, @NotNull ProcessInfo processInfo) {
      }
    });
  }

  private static class TestAttachGroup extends XDefaultLocalAttachGroup {
    @Nullable String myName;
    @Nullable Integer myOrder;

    public TestAttachGroup(@Nullable String name, @Nullable Integer order) {
      myName = name;
      myOrder = order;
    }

    @Override
    public int getOrder() {
      return myOrder != null ? myOrder : super.getOrder();
    }

    @NotNull
    @Override
    public String getGroupName() {
      return myName != null ? myName : super.getGroupName();
    }
  }

  private static class TestDebuggerProvider implements XLocalAttachDebuggerProvider {
    @Nullable private final Integer myFilterPID;
    @NotNull private final XLocalAttachGroup myGroup;
    @NotNull private final List<XLocalAttachDebugger> myDebuggers;

    public TestDebuggerProvider(@Nullable Integer filterPID,
                                @NotNull XLocalAttachGroup group,
                                @NotNull List<XLocalAttachDebugger> debuggers) {
      myFilterPID = filterPID;
      myGroup = group;
      myDebuggers = debuggers;
    }

    public TestDebuggerProvider(@Nullable Integer filterPID, @NotNull XLocalAttachGroup group, String... names) {
      this(filterPID, group, createDebuggers(names));
    }

    public TestDebuggerProvider(@NotNull XLocalAttachGroup group, String... names) {
      this(null, group, names);
    }

    public TestDebuggerProvider(String... names) {
      this(XDefaultLocalAttachGroup.INSTANCE, names);
    }

    @NotNull
    @Override
    public XLocalAttachGroup getAttachGroup() {
      return myGroup;
    }

    @NotNull
    @Override
    public List<XLocalAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                            @NotNull ProcessInfo processInfo,
                                                            @NotNull UserDataHolder contextHolder) {
      if (myFilterPID != null && processInfo.getPid() != myFilterPID) return Collections.emptyList();
      return myDebuggers;
    }
  }
}
