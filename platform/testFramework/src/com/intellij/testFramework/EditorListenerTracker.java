// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

@TestOnly
public final class EditorListenerTracker {
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
        afterList.removeIf(listener -> {
          //noinspection CastConflictsWithInstanceof
          if (listener instanceof PsiDocumentManager && ((PsiDocumentManagerBase)listener).isDefaultProject()) {
            return true;
          }

          // app level listener
          String name = listener.getClass().getName();
          return name.startsWith("com.intellij.copyright.CopyrightManagerDocumentListener$") ||
                 name.startsWith("com.jetbrains.liveEdit.highlighting.ElementHighlighterCaretListener");
        });
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
