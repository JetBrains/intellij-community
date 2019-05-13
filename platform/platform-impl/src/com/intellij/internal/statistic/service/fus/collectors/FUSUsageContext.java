// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

//  FUSUsageContext represents additional data for submitted groups(UsageCollector)or usages(metrics)
//  example: my usage collector collects actions invocations.
// "my.foo.action"  can be invoked from main.menu/context.menu/my.dialog/"all-actions-run"
// if I write  MyTrigger.trigger("my.foo.action") I'll see how many times this action was invoked.
// for instance, "my.foo.action"=239
// if I write  MyTrigger.trigger("my.foo.action", SystemInfo.OS_NAME, "main.menu"(or context.menu/my.dialog/etc.))
// I'll get the same counts for action, "my.foo.action"=239,
// but  I'll know for instance: this action on MacOS was invoked 230 times and on Windows 9 times,
// or from main.menu it was invoked 3 times, from my.dialog => 235 times and from context.menu => 1  times.
// submitted data format is:
// "id": "my.collector",
//          "metrics": [
//            {
//              "id": "my.foo.action",
//              "value": 125,
//              "context": { "data_1": "Mac OS X", "data_2": "main.menu"}
//            }
//            {
//              "id": "my.foo.action",
//              "value": 1,
//              "context": { "data_1": "Mac OS X", "data_2": "context.menu"}
//            }
public class FUSUsageContext {
  public static final FUSUsageContext OS_CONTEXT = create(getOSNameContextData());

  private static final byte MAX_DATA_SIZE = 5; // restricted by server
  private final Map<String, String> data;

  private FUSUsageContext(@NotNull String... data) {
    this.data = ContainerUtil.newLinkedHashMap(data.length);
    for (int i = 1; i < data.length + 1; i++) {
      String contextData = data[i - 1];
      if (StringUtil.isEmptyOrSpaces(contextData)) continue;
      this.data.put(getContextDataKey(i), contextData);
    }
  }

  @NotNull
  public Map<String, String> getData() {
    return Collections.unmodifiableMap(data);
  }

  public static String getOSNameContextData() {
    if (SystemInfo.isWindows) return "Windows";
    if (SystemInfo.isMac) return "Mac";
    if (SystemInfo.isLinux) return "Linux";
    return "Other";
  }

  public static FUSUsageContext create(@NotNull String... data) {
    assert data.length <= MAX_DATA_SIZE;
    return new FUSUsageContext(data);
  }

  @NotNull
  private static String getContextDataKey(int i) {
    return "data_" + i;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FUSUsageContext)) return false;
    return Objects.equals(data, ((FUSUsageContext)o).data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }
}
