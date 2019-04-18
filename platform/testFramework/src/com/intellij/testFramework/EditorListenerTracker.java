/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
@TestOnly
public class EditorListenerTracker {
  private final Map<Class<? extends EventListener>, List<? extends EventListener>> before;

  public EditorListenerTracker() {
    EncodingManager.getInstance(); //adds listeners
    before = ((EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster()).getListeners();
  }

  public void checkListenersLeak() throws AssertionError {
    try {
      EditorEventMulticasterImpl multicaster = (EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster();
      Map<Class<? extends EventListener>, List<? extends EventListener>> after = multicaster.getListeners();
      Map<Class<? extends EventListener>, List<? extends EventListener>> leaked = new LinkedHashMap<>();
      for (Map.Entry<Class<? extends EventListener>, List<? extends EventListener>> entry : after.entrySet()) {
        Class<? extends EventListener> aClass = entry.getKey();
        List<? extends EventListener> beforeList = before.get(aClass);
        List<EventListener> afterList = new ArrayList<>(entry.getValue());
        if (beforeList != null) {
          afterList.removeAll(beforeList);
        }
        // listeners may hang on default project which comes and goes unpredictably, so just ignore them
        afterList.removeIf(listener -> listener instanceof PsiDocumentManager && ((PsiDocumentManagerBase)listener).isDefaultProject());
        if (!afterList.isEmpty()) {
          leaked.put(aClass, afterList);
        }
      }

      for (Map.Entry<Class<? extends EventListener>, List<? extends EventListener>> entry : leaked.entrySet()) {
        Class<? extends EventListener> aClass = entry.getKey();
        List<? extends EventListener> list = entry.getValue();
        Assert.fail("Listeners leaked for " + aClass+":\n"+list);
      }
    }
    finally {
      before.clear();
    }
  }
}
