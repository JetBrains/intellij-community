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

package com.intellij.history.core.tree;

import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

public class EntryTest extends LocalHistoryTestCase {
  @Test
  public void testPathEquality() {
    Entry e = new MyEntry() {
      @Override
      public String getPath() {
        return "path";
      }
    };
    assertTrue(e.pathEquals("path"));
    assertFalse(e.pathEquals("bla-bla-bla"));

    Paths.setCaseSensitive(true);
    assertFalse(e.pathEquals("PATH"));

    Paths.setCaseSensitive(false);
    assertTrue(e.pathEquals("PATH"));
  }

  private class MyEntry extends Entry {
    public MyEntry() {
      super((String)null);
    }

    @Override
    public long getTimestamp() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Entry copy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void collectDifferencesWith(@NotNull Entry e, @NotNull List<Difference> result) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void collectCreatedDifferences(@NotNull List<Difference> result) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void collectDeletedDifferences(@NotNull List<Difference> result) {
      throw new UnsupportedOperationException();
    }
  }
}
