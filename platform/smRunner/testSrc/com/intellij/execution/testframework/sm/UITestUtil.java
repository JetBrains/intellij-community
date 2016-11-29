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
package com.intellij.execution.testframework.sm;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Roman Chernyatchik
 */
public class UITestUtil {
  public static class ListOfFragments extends ArrayList<Pair<String, SimpleTextAttributes>> {
    public void add(@NotNull @Nls final String fragment, @NotNull final SimpleTextAttributes attributes) {
      add(new Pair<>(fragment, attributes));
    }
  }

  public static class FragmentsContainer {
    private final UITestUtil.ListOfFragments myFragments;

    public FragmentsContainer() {
      myFragments = new ListOfFragments();
    }

    public void append(@NotNull @Nls final String fragment,
                       @NotNull final SimpleTextAttributes attributes) {
      myFragments.add(fragment, attributes);
    }

    public UITestUtil.ListOfFragments getFragments() {
      return myFragments;
    }

    public String getTextAt(final int index) {
      return myFragments.get(index).first;
    }

    public SimpleTextAttributes getAttribsAt(final int index) {
      return myFragments.get(index).second;
    }

    public void clear() {
      myFragments.clear();
    }
  }
}
