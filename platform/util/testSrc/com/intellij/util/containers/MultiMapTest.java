// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class MultiMapTest {
  @Test
  public void testConcurrentMostlySingularMultiMapTestReplace() {
    ConcurrentMostlySingularMultiMap<String, String> map = new ConcurrentMostlySingularMultiMap<>();
    List<String> xxxList = Collections.singletonList("xxx");
    List<String> empty = Collections.emptyList();
    String KEY = "key";
    boolean replaced = map.replace(KEY, empty, xxxList);
    assertThat(replaced).isTrue();
    assertThat(map.size()).isEqualTo(1);
    assertThat(map.get(KEY)).isEqualTo(xxxList);

    List<String> yyyList = Collections.singletonList("yyy");
    replaced = map.replace(KEY, xxxList, yyyList);
    assertThat(replaced).isTrue();
    assertThat(map.size()).isEqualTo(1);
    assertThat(map.get(KEY)).isEqualTo(yyyList);

    replaced = map.replace(KEY, yyyList, empty);
    assertThat(replaced).isTrue();
    assertThat(map.isEmpty()).isTrue();
    assertThat(map.get(KEY)).isEqualTo(empty);
  }

  @Test
  public void hashMap() {
    MultiMap<String, String> map = new MultiMap<>();
    doTest(map);
  }

  @Test
  public void linked() {
    MultiMap<String, String> map = MultiMap.createLinked();
    doTest(map);
  }

  private static void doTest(MultiMap<String, String> map) {
    map.putValue("foo", "bar");
    map.putValue("foo", "bar2");
    assertThat(map.freezeValues().size()).isEqualTo(1);
    assertThatThrownBy(() -> {
      map.freezeValues().remove("foo");
      map.get("foo").remove("bar");
    }).isInstanceOf(UnsupportedOperationException.class);
  }
}
