/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 17, 2007
 * Time: 3:20:51 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.xmlb.annotations.Attribute;
import gnu.trove.Equality;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.Collection;

@State(name = "Encoding", storages = @Storage("encoding.xml"))
public class EncodingManagerImpl extends EncodingManager implements PersistentStateComponent<EncodingManagerImpl.State>, Disposable {
  private static final Equality<Reference<Document>> REFERENCE_EQUALITY = new Equality<Reference<Document>>() {
    @Override
    public boolean equals(Reference<Document> o1, Reference<Document> o2) {
      Object v1 = o1 == null ? REFERENCE_EQUALITY : o1.get();
      Object v2 = o2 == null ? REFERENCE_EQUALITY : o2.get();
      return v1 == v2;
    }
  };
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  static class State {
    @NotNull
    private Charset myDefaultEncoding = CharsetToolkit.UTF8_CHARSET;

    @Attribute("default_encoding")
    @NotNull
    public String getDefaultCharsetName() {
      return myDefaultEncoding == ChooseFileEncodingAction.NO_ENCODING ? "" : myDefaultEncoding.name();
    }

    public void setDefaultCharsetName(@NotNull String name) {
      myDefaultEncoding = name.isEmpty()
                          ? ChooseFileEncodingAction.NO_ENCODING
                          : ObjectUtils.notNull(CharsetToolkit.forName(name), CharsetToolkit.getDefaultSystemCharset());
    }
  }

  private State myState = new State();

  private final Alarm updateEncodingFromContent = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private static final Key<Charset> CACHED_CHARSET_FROM_CONTENT = Key.create("CACHED_CHARSET_FROM_CONTENT");

  private final BoundedTaskExecutor changedDocumentExecutor =
    new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, JobSchedulerImpl.CORES_COUNT, this);

  public EncodingManagerImpl(@NotNull EditorFactory editorFactory) {
    editorFactory.getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        queueUpdateEncodingFromContent(e.getDocument());
      }
    }, this);
    editorFactory.addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        queueUpdateEncodingFromContent(event.getEditor().getDocument());
      }
    }, this);
  }

  @NonNls public static final String PROP_CACHED_ENCODING_CHANGED = "cachedEncoding";

  private static final Key<String> DETECTING_ENCODING_KEY = Key.create("DETECTING_ENCODING_KEY");
  private void handleDocument(@NotNull final Document document) {
    if (document.getUserData(DETECTING_ENCODING_KEY) == null) return;
    try {
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile == null) return;
      Project project = guessProject(virtualFile);
      if (project != null && project.isDisposed()) return;
      Charset charset = LoadTextUtil.charsetFromContentOrNull(project, virtualFile, document.getImmutableCharSequence());
      Charset oldCached = getCachedCharsetFromContent(document);
      if (!Comparing.equal(charset, oldCached)) {
        setCachedCharsetFromContent(charset, oldCached, document);
      }
    }
    finally {
      document.putUserData(DETECTING_ENCODING_KEY, null);
    }
  }

  private void setCachedCharsetFromContent(Charset charset, Charset oldCached, @NotNull Document document) {
    document.putUserData(CACHED_CHARSET_FROM_CONTENT, charset);
    firePropertyChange(document, PROP_CACHED_ENCODING_CHANGED, oldCached, charset);
  }

  @Nullable("returns null if charset set cannot be determined from content")
  Charset computeCharsetFromContent(@NotNull final VirtualFile virtualFile) {
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) {
      return null;
    }
    Charset cached = EncodingManager.getInstance().getCachedCharsetFromContent(document);
    if (cached != null) {
      return cached;
    }

    final Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
    return ApplicationManager.getApplication().runReadAction(new Computable<Charset>() {
      @Override
      public Charset compute() {
        Charset charsetFromContent = LoadTextUtil.charsetFromContentOrNull(project, virtualFile, document.getImmutableCharSequence());
        if (charsetFromContent != null) {
          setCachedCharsetFromContent(charsetFromContent, null, document);
        }
        return charsetFromContent;
      }
    });
  }

  @Override
  public void dispose() {
    updateEncodingFromContent.cancelAllRequests();
    clearDocumentQueue();
  }

  void queueUpdateEncodingFromContent(@NotNull Document document) {
    document.putUserData(DETECTING_ENCODING_KEY, "");
    changedDocumentExecutor.execute(new DocumentEncodingDetectRequest(document));
  }

  private static class DocumentEncodingDetectRequest implements Runnable {
    private final Reference<Document> ref;

    private DocumentEncodingDetectRequest(@NotNull Document document) {
      ref = new WeakReference<Document>(document);
    }

    @Override
    public void run() {
      Document document = ref.get();
      if (document == null) return; // document gced, don't bother
      ((EncodingManagerImpl)getInstance()).handleDocument(document);
    }
  }

  @Override
  @Nullable
  public Charset getCachedCharsetFromContent(@NotNull Document document) {
    return document.getUserData(CACHED_CHARSET_FROM_CONTENT);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  @Override
  @NotNull
  public Collection<Charset> getFavorites() {
    Collection<Charset> result = new THashSet<Charset>();
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      result.addAll(EncodingProjectManager.getInstance(project).getFavorites());
    }
    result.addAll(EncodingProjectManagerImpl.widelyKnownCharsets());
    return result;
  }

  @Override
  @Nullable
  public Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults) {
    Project project = guessProject(virtualFile);
    if (project == null) return null;
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(project);
    if (encodingManager == null) return null; //tests
    return encodingManager.getEncoding(virtualFile, useParentDefaults);
  }

  public void clearDocumentQueue() {
    changedDocumentExecutor.clearAndCancelAll();
  }

  @Nullable
  private static Project guessProject(final VirtualFile virtualFile) {
    return ProjectLocator.getInstance().guessProjectForFile(virtualFile);
  }

  @Override
  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
    Project project = guessProject(virtualFileOrDir);
    EncodingProjectManager.getInstance(project).setEncoding(virtualFileOrDir, charset);
  }

  @Override
  public boolean isUseUTFGuessing(final VirtualFile virtualFile) {
    return true;
  }

  @Override
  public boolean isNative2Ascii(@NotNull final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    return project != null && EncodingProjectManager.getInstance(project).isNative2Ascii(virtualFile);
  }

  @Override
  public boolean isNative2AsciiForPropertiesFiles() {
    Project project = guessProject(null);
    return project != null && EncodingProjectManager.getInstance(project).isNative2AsciiForPropertiesFiles();
  }

  @Override
  public void setNative2AsciiForPropertiesFiles(final VirtualFile virtualFile, final boolean native2Ascii) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setNative2AsciiForPropertiesFiles(virtualFile, native2Ascii);
  }

  @Override
  @NotNull
  public Charset getDefaultCharset() {
    return myState.myDefaultEncoding == ChooseFileEncodingAction.NO_ENCODING ? CharsetToolkit.getDefaultSystemCharset() : myState.myDefaultEncoding;
  }

  @Override
  @NotNull
  public String getDefaultCharsetName() {
    return myState.getDefaultCharsetName();
  }

  @Override
  public void setDefaultCharsetName(@NotNull String name) {
    myState.setDefaultCharsetName(name);
  }

  @Override
  @Nullable
  public Charset getDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    if (project == null) return null;
    return EncodingProjectManager.getInstance(project).getDefaultCharsetForPropertiesFiles(virtualFile);
  }

  @Override
  public void setDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile, final Charset charset) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setDefaultCharsetForPropertiesFiles(virtualFile, charset);
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener, @NotNull Disposable parentDisposable) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removePropertyChangeListener(listener);
      }
    });
  }

  private void removePropertyChangeListener(@NotNull PropertyChangeListener listener){
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  void firePropertyChange(@Nullable Document document, @NotNull String propertyName, final Object oldValue, final Object newValue) {
    Object source = document == null ? this : document;
    myPropertyChangeSupport.firePropertyChange(new PropertyChangeEvent(source, propertyName, oldValue, newValue));
  }
}
