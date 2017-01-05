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
package com.intellij.execution;

import com.intellij.util.PathsList;
import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class PathListBuilderTest {
  @Test
  public void testOrder() {
    PathsList builder = new PathsList();
    builder.add("a");
    builder.addFirst("2");
    builder.addTail("A");
    builder.addFirst("1");
    builder.add("b");
    builder.addTail("B");
    assertThat(builder.getPathList()).containsExactly("1", "2", "a", "b", "A", "B");
  }

  @Test
  public void testDuplications() {
    PathsList builder = new PathsList();
    builder.add("b");
    builder.add("b");
    builder.addFirst("a");
    builder.addFirst("a");
    builder.addTail("c");
    builder.addTail("c");
    assertThat(builder.getPathList()).containsExactly("a", "b", "c");
  }

  @Test
  public void testComplexDuplications() {
    PathsList builder = new PathsList();
    builder.add("a" + File.pathSeparatorChar + "b");
    builder.add("c" + File.pathSeparatorChar + "b");
    assertThat(builder.getPathList()).containsExactly("a", "b", "c");
  }

  @Test
  public void testAddTwice() {
    PathsList builder = new PathsList();
    builder.add("a" + File.pathSeparatorChar + "a");
    builder.add("b");
    assertThat(builder.getPathList()).containsExactly("a", "b");
  }

  @Test
  public void testAddFirstTwice() {
    PathsList builder = new PathsList();
    builder.addFirst("b" + File.pathSeparatorChar + "b");
    builder.addFirst("a");
    assertThat(builder.getPathList()).containsExactly("a", "b");
  }

  @Test
  public void testAsString() {
    PathsList builder = new PathsList();
    builder.add("a" + File.pathSeparatorChar + "b" + File.pathSeparatorChar);
    builder.add("c" + File.pathSeparatorChar);
    assertThat(builder.getPathsString()).isEqualTo("a" + File.pathSeparatorChar + "b" + File.pathSeparatorChar + "c");
  }
}