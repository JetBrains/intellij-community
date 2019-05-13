/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class HighlightTestInfo implements Disposable {
  @NotNull protected final String[] filePaths;
  protected boolean checkWarnings;
  protected boolean checkInfos;
  protected boolean checkSymbolNames;
  protected boolean checkWeakWarnings;
  protected String projectRoot;
  private boolean tested;
  private final String myPlace;

  public HighlightTestInfo(@NotNull Disposable parentDisposable, @NonNls @NotNull String... filePaths) {
    this.filePaths = filePaths;
    // disposer here for catching the case of not calling test()
    Disposer.register(parentDisposable, this);
    myPlace = parentDisposable.toString();
  }

  public HighlightTestInfo checkWarnings() { checkWarnings = true; return this; }
  public HighlightTestInfo checkWeakWarnings() { checkWeakWarnings = true; return this; }
  public HighlightTestInfo checkInfos() { checkInfos = true; return this; }
  public HighlightTestInfo checkSymbolNames() { checkSymbolNames = true; return this; }
  public HighlightTestInfo projectRoot(@NonNls @NotNull String root) { projectRoot = root; return this; }

  public HighlightTestInfo test() {
    try {
      doTest();
      return this;
    }
    finally {
      tested = true;
      Disposer.dispose(this);
    }
  }

  @Override
  public void dispose() {
    assert tested : "You must call HighlightTestInfo.test() in " + myPlace;
  }

  protected abstract HighlightTestInfo doTest();
}
