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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.EditorPlace;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.util.containers.HashSet;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;

public class FontSizeSynchronizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.util.FontSizeSynchronizer");
  private final Collection<Editor> myEditors = new HashSet<>();
  private final MyFontSizeListener myFontSizeListener = new MyFontSizeListener();
  private int myLastFontSize = -1;

  public void synchronize(EditorEx editor) {
    LOG.assertTrue(!myEditors.contains(editor));
    editor.addPropertyChangeListener(myFontSizeListener);
    myEditors.add(editor);
    if (myLastFontSize != -1) myFontSizeListener.updateEditor(editor);
  }

  public void stopSynchronize(EditorEx editor) {
    LOG.assertTrue(myEditors.contains(editor));
    editor.removePropertyChangeListener(myFontSizeListener);
    myEditors.remove(editor);
  }

  public static void attachTo(ArrayList<EditorPlace> editorPlaces) {
    final FontSizeSynchronizer synchronizer = new FontSizeSynchronizer();
    for (EditorPlace editorPlace : editorPlaces) {
      editorPlace.addListener(new EditorPlace.EditorListener() {
        public void onEditorCreated(EditorPlace place) {
          synchronizer.synchronize((EditorEx)place.getEditor());
        }

        public void onEditorReleased(Editor releasedEditor) {
          synchronizer.stopSynchronize((EditorEx)releasedEditor);
        }
      });
      EditorEx editor = (EditorEx)editorPlace.getEditor();
      if (editor != null) synchronizer.synchronize(editor);
    }

  }

  private class MyFontSizeListener implements PropertyChangeListener {
    private boolean myDuringUpdate = false;
    public void propertyChange(PropertyChangeEvent evt) {
      if (myDuringUpdate) return;
      if (!EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) return;
      if (evt.getOldValue().equals(evt.getNewValue())) return;
      myLastFontSize = ((Integer)evt.getNewValue()).intValue();
      for (Editor editor : myEditors) {
        if (editor == null || editor == evt.getSource()) continue;
        updateEditor((EditorEx)editor);
      }
    }

    public void updateEditor(EditorEx editor) {
      try {
        myDuringUpdate = true;
        editor.setFontSize(myLastFontSize);
      } finally {
        myDuringUpdate = false;
      }
    }
  }
}
