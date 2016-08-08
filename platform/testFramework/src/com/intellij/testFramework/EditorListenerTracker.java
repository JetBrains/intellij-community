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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.junit.Assert;

import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class EditorListenerTracker {
  private final Map<Class, List> before;
  private final boolean myDefaultProjectInitialized;

  public EditorListenerTracker() {
    EncodingManager.getInstance(); //adds listeners
    EditorEventMulticasterImpl multicaster = (EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster();
    before = multicaster.getListeners();
    myDefaultProjectInitialized = ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized();
  }

  public void checkListenersLeak() throws AssertionError {
    try {
      // listeners may hang on default project
      if (myDefaultProjectInitialized != ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized()) return;

      EditorEventMulticasterImpl multicaster = (EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster();
      Map<Class, List> after = multicaster.getListeners();
      Map<Class, List> leaked = new LinkedHashMap<>();
      for (Map.Entry<Class, List> entry : after.entrySet()) {
        Class aClass = entry.getKey();
        List beforeList = before.get(aClass);
        List afterList = entry.getValue();
        if (beforeList != null) {
          afterList.removeAll(beforeList);
        }
        if (!afterList.isEmpty()) {
          leaked.put(aClass, afterList);
        }
      }

      for (Map.Entry<Class, List> entry : leaked.entrySet()) {
        Class aClass = entry.getKey();
        List list = entry.getValue();
        Assert.fail("Listeners leaked for " + aClass+":\n"+list);
      }
    }
    finally {
      before.clear();
    }
  }
}
