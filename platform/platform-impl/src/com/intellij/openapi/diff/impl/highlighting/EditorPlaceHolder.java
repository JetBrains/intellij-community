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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.ContentChangeListener;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.DiffVersionComponent;
import com.intellij.openapi.diff.impl.util.ContentDocumentListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;

class EditorPlaceHolder extends DiffMarkup implements DiffVersionComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.highlighting.EditorWrapper");
  private EditorEx myEditor;
  private DiffContent myContent;
  private final FragmentSide mySide;
  private ContentChangeListener myListener = null;

  public EditorPlaceHolder(FragmentSide side, Project project) {
    super(project);
    mySide = side;
    resetHighlighters();
  }

  public void addListener(ContentChangeListener listener) {
    LOG.assertTrue(myListener == null);
    myListener = listener;
  }

  protected void doDispose() {
    LOG.assertTrue(!isDisposed());
    super.doDispose();
    fireContentChanged();
  }

  private void fireContentChanged() {
    myListener.onContentChangedIn(this);
  }

  public void setContent(final DiffContent content) {
    disposeEditor();
    myContent = content;
    if (myContent != null) {
      Document document = myContent.getDocument();
      final EditorFactory editorFactory = EditorFactory.getInstance();
      myEditor = DiffUtil.createEditor(document, getProject(), false);
      addDisposable(new Disposable() {
        public void dispose() {
          editorFactory.releaseEditor(myEditor);
          myEditor = null;
        }
      });
      ContentDocumentListener.install(myContent, this);
    }
    fireContentChanged();
  }

  public EditorEx getEditor() { return myEditor; }

  public FragmentSide getSide() { return mySide; }

  public DiffContent getContent() {
    return myContent;
  }

  public void removeContent() {
    setContent(null);
  }
}
