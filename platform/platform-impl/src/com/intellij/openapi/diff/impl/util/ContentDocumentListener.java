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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.DiffVersionComponent;

public class ContentDocumentListener implements DiffContent.Listener {
  private final DiffVersionComponent myDiffComponent;

  private ContentDocumentListener(DiffVersionComponent diffComponent) {
    myDiffComponent = diffComponent;
  }

  public static void install(final DiffContent content, DiffVersionComponent component) {
    final ContentDocumentListener listener = new ContentDocumentListener(component);
    content.onAssigned(true);
    content.addListener(listener);
    Disposable disposable = new Disposable() {
      public void dispose() {
        content.onAssigned(false);
      }
    };
    component.addDisposable(disposable);
  }

  public void contentInvalid() {
    myDiffComponent.removeContent();
  }
}
