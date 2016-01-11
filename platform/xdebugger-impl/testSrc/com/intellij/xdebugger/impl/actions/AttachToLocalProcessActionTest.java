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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.attach.XDefaultLocalAttachGroup;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.xdebugger.impl.actions.AttachToLocalProcessAction.*;

public class AttachToLocalProcessActionTest extends PlatformTestCase {
  public void testCollectingAttachItems_Empty() throws Exception {
    // no providers
    assertItems("");

    // no provided debuggers
    assertItems("", new TestDebuggerProvider(), new TestDebuggerProvider());
  }

  public void testCollectingAttachItems_OneDebugger() throws Exception {
    Project project = ProjectManager.getInstance().getDefaultProject();

    assertItems("--------\n" +
                "1 exec1: dbg\n" +
                "2 exec2: dbg\n",
                new TestDebuggerProvider("dbg"));
  }

  public void testCollectingAttachItems_DebuggerPerProcess() throws Exception {
    // from one provider
    assertItems("--------\n" +
                "1 exec1: dbg1\n" +
                "2 exec2: dbg2\n",
                new TestDebuggerProvider("dbg1") {
                  @NotNull
                  @Override
                  public List<XLocalAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                                          @NotNull ProcessInfo processInfo,
                                                                          @NotNull UserDataHolder contextHolder) {
                    if (processInfo.getPid() != 1) return Collections.emptyList();
                    return super.getAvailableDebuggers(project, processInfo, contextHolder);
                  }
                },
                new TestDebuggerProvider("dbg2") {
                  @NotNull
                  @Override
                  public List<XLocalAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                                          @NotNull ProcessInfo processInfo,
                                                                          @NotNull UserDataHolder contextHolder) {
                    if (processInfo.getPid() != 2) return Collections.emptyList();
                    return super.getAvailableDebuggers(project, processInfo, contextHolder);
                  }
                });
  }

  public void testCollectingAttachItems_SeveralDebuggers() throws Exception {
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

  public void testCollectingAttachItems_Groups() throws Exception {
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
                new TestDebuggerProvider(XLocalAttachGroup.DEFAULT, "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("", 1), "dbg1", "dbg2"));
  }

  public void testCollectingAttachItems_Groups_SortingGroups() throws Exception {
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
                new TestDebuggerProvider(XLocalAttachGroup.DEFAULT, "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group1", 1), "dbg1", "dbg2"),
                new TestDebuggerProvider(new TestAttachGroup("group2", -1), "dbg1", "dbg2"));
  }

  public void testCollectingAttachItems_Groups_SortingItems() throws Exception {
    assertItems("----group----\n" +
                "1 exec1: dbg1\n" +
                "2 exec2: dbg1\n",
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @Override
                  public int compare(@NotNull Project project, @NotNull ProcessInfo a, @NotNull ProcessInfo b) {
                    return a.getPid() - b.getPid();
                  }
                }, "dbg1"));
    assertItems("----group----\n" +
                "2 exec2: dbg1\n" +
                "1 exec1: dbg1\n",
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @Override
                  public int compare(@NotNull Project project, @NotNull ProcessInfo a, @NotNull ProcessInfo b) {
                    return b.getPid() - a.getPid();
                  }
                }, "dbg1"));
  }
  
  public void testCollectingAttachItems_Groups_CustomItemTitles() throws Exception {
    assertItems("----group----\n" +
                "1 custom: dbg1\n" +
                "2 custom: dbg1\n",
                new TestDebuggerProvider(new TestAttachGroup("group", 0) {
                  @NotNull
                  @Override
                  public String getProcessDisplayText(@NotNull Project project, @NotNull ProcessInfo info) {
                    return "custom";
                  }
                }, "dbg1"));
  }

  public void testHistory() throws Exception {
    ProcessInfo info1 = new ProcessInfo(1, "command line 1", "exec1", "args1", null, null);
    ProcessInfo info2 = new ProcessInfo(1, "command line 2", "exec1", "args1", null, null);
    ProcessInfo info3 = new ProcessInfo(1, "command line 3", "exec1", "args1", null, null);
    ProcessInfo info4 = new ProcessInfo(1, "command line 4", "exec1", "args1", null, null);
    ProcessInfo info5 = new ProcessInfo(1, "command line 5", "exec1", "args1", null, null);

    List<XLocalAttachDebugger> debuggers = createDebuggers("gdb");
    AttachItem item1 = new AttachItem(XLocalAttachGroup.DEFAULT, true, info1, debuggers);
    AttachItem item2 = new AttachItem(XLocalAttachGroup.DEFAULT, true, info2, debuggers);
    AttachItem item3 = new AttachItem(XLocalAttachGroup.DEFAULT, true, info3, debuggers);
    AttachItem item4 = new AttachItem(XLocalAttachGroup.DEFAULT, true, info4, debuggers);
    AttachItem item5 = new AttachItem(XLocalAttachGroup.DEFAULT, true, info5, debuggers);

    HistoryItem historyItem1 = new HistoryItem(info1, XLocalAttachGroup.DEFAULT, debuggers.get(0));
    HistoryItem historyItem2 = new HistoryItem(info2, XLocalAttachGroup.DEFAULT, debuggers.get(0));
    HistoryItem historyItem3 = new HistoryItem(info3, XLocalAttachGroup.DEFAULT, debuggers.get(0));
    HistoryItem historyItem4 = new HistoryItem(info4, XLocalAttachGroup.DEFAULT, debuggers.get(0));
    HistoryItem historyItem5 = new HistoryItem(info5, XLocalAttachGroup.DEFAULT, debuggers.get(0));
    
    // empty
    assertEmpty(getHistory(getProject()));

    // adding some items
    addToHistory(getProject(), item1);
    addToHistory(getProject(), item2);

    assertOrderedEquals(getHistory(getProject()), historyItem1, historyItem2);

    addToHistory(getProject(), item3);
    addToHistory(getProject(), item4);

    assertOrderedEquals(getHistory(getProject()), historyItem1, historyItem2, historyItem3, historyItem4);

    // limiting size to 4 items
    addToHistory(getProject(), item5);
    assertOrderedEquals(getHistory(getProject()), historyItem2, historyItem3, historyItem4, historyItem5);
    
    // popping up recent items
    addToHistory(getProject(), item3);
    addToHistory(getProject(), item2);
    assertOrderedEquals(getHistory(getProject()), historyItem4, historyItem5, historyItem3, historyItem2);
  }
  
  public void testHistory_UpdatingPreviousItems() throws Exception {
    TestAttachGroup group1 = new TestAttachGroup("group1", 1);
    TestAttachGroup group2 = new TestAttachGroup("group2", 2);

    List<XLocalAttachDebugger> debuggers1 = createDebuggers("gdb1");
    List<XLocalAttachDebugger> debuggers2 = createDebuggers("gdb2");

    ProcessInfo info1 = new ProcessInfo(1, "same command line", "exec1", "args1", null, null);
    ProcessInfo info2 = new ProcessInfo(2, "same command line", "exec2", "args2", null, null);
    
    AttachItem item1 = new AttachItem(group1, true, info1, debuggers1);
    AttachItem item2 = new AttachItem(group2, true, info2, debuggers2);

    HistoryItem historyItem1 = new HistoryItem(info1, group1, debuggers1.get(0));
    HistoryItem historyItem2 = new HistoryItem(info2, group2, debuggers2.get(0));

    addToHistory(getProject(), item1);
    assertOrderedEquals(getHistory(getProject()), historyItem1);
    addToHistory(getProject(), item2);
    assertOrderedEquals(getHistory(getProject()), historyItem2);
  }
                                                                                                                                     
  private static void assertItems(String expected, @NotNull XLocalAttachDebuggerProvider... providers) {
    Project project = ProjectManager.getInstance().getDefaultProject();
    assertEquals(expected, printItems(project, collectAttachItems(project, getProcessList(), providers)));
  }

  private static String printItems(Project project, List<AttachItem> items) {
    StringBuilder builder = new StringBuilder();

    for (AttachItem each : items) {
      String title = each.getSeparatorTitle();
      if (title != null) builder.append("----").append(title).append("----\n");
      builder.append(each.getText(project)).append(": ").append(each.getSelectedDebugger().getDebuggerDisplayName()).append("\n");
      for (AttachItem eachSubItem : each.getSubItems()) {
        builder.append("    ").append(eachSubItem.getSelectedDebugger().getDebuggerDisplayName()).append("\n");
      }
    }

    return builder.toString();
  }

  private static ProcessInfo[] getProcessList() {
    return new ProcessInfo[]{
      new ProcessInfo(1, "command line 1", "exec1", "args1", null, null),
      new ProcessInfo(2, "command line 2", "exec2", "args2", null, null),
    };
  }

  @NotNull
  private static List<XLocalAttachDebugger> createDebuggers(String... names) {
    return ContainerUtil.map(names, new Function<String, XLocalAttachDebugger>() {
      @Override
      public XLocalAttachDebugger fun(final String s) {
        return new XLocalAttachDebugger() {
          @NotNull
          @Override
          public String getDebuggerDisplayName() {
            return s;
          }

          @NotNull
          @Override
          public XDebugSession attachDebugSession(@NotNull Project project, @NotNull ProcessInfo processInfo) throws ExecutionException {
            return null;
          }
        };
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
    XLocalAttachGroup myGroup;
    String[] myNames;

    public TestDebuggerProvider(XLocalAttachGroup group, String... names) {
      myGroup = group;
      myNames = names;
    }

    public TestDebuggerProvider(String... names) {
      this(XLocalAttachGroup.DEFAULT, names);
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
      return createDebuggers(myNames);
    }
  }
}