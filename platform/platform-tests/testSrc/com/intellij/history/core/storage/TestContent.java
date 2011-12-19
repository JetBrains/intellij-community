/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core.storage;

import com.intellij.history.core.Content;

import java.util.Arrays;

public class TestContent extends Content {
  private final byte[] myData;

  public TestContent(byte[] bytes) {
    myData = bytes;
  }

  @Override
  public byte[] getBytes() {
    return myData;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void release() {
  }

  @Override
  public boolean equals(Object o) {
    return Arrays.equals(myData, ((Content)o).getBytes());
  }

  @Override
  public int hashCode() {
    return myData.hashCode();
  }
}
