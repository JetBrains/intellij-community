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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 17, 2007
 * Time: 3:20:51 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.TransferToPooledThreadQueue;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import gnu.trove.Equality;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Set;


@State(
  name = "Encoding",
  storages = {
      @Storage( file = "$APP_CONFIG$/encoding.xml")
  }
)
public class EncodingManagerImpl extends EncodingManager implements PersistentStateComponent<Element>, Disposable {
  public static final Equality<Reference<Document>> REFERENCE_EQUALITY = new Equality<Reference<Document>>() {
    @Override
    public boolean equals(Reference<Document> o1, Reference<Document> o2) {
      return o1.get() == o2.get();
    }
  };
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private String myDefaultEncoding = CharsetToolkit.UTF8;
  private Charset myCachedCharset = null;

  private final Alarm updateEncodingFromContent = new Alarm(Alarm.ThreadToUse.OWN_THREAD, this);
  private static final Key<Charset> CACHED_CHARSET_FROM_CONTENT = Key.create("CACHED_CHARSET_FROM_CONTENT");

  private final TransferToPooledThreadQueue<Reference<Document>> myChangedDocuments = new TransferToPooledThreadQueue<Reference<Document>>(
    "Loading documents", new Processor<Reference<Document>>() {
    @Override
    public boolean process(Reference<Document> ref) {
      Document document = ref.get();
      if (document == null) return true; // document gced, don't bother
      handleDocument(document);
      return true;
    }
  }, ApplicationManager.getApplication().getDisposed(), -1); // drain the whole queue, do not reschedule

  public EncodingManagerImpl(EditorFactory editorFactory) {
    editorFactory.getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        queueUpdateEncodingFromContent(e.getDocument());
      }
    }, this);
    editorFactory.addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorCreated(EditorFactoryEvent event) {
        queueUpdateEncodingFromContent(event.getEditor().getDocument());
      }
    }, this);
  }

  @NonNls public static final String PROP_CACHED_ENCODING_CHANGED = "cachedEncoding";

  private void handleDocument(final Document document) {
    ApplicationManager.getApplication().runReadAction(new Runnable(){
      @Override
      public void run() {
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (virtualFile == null) return;
        Project project = guessProject(virtualFile);
        if (project != null && project.isDisposed()) return;
        Charset charset = LoadTextUtil.charsetFromContentOrNull(project, virtualFile, document.getText());
        Charset oldCached = getCachedCharsetFromContent(document);
        if (!Comparing.equal(charset, oldCached)) {
          document.putUserData(CACHED_CHARSET_FROM_CONTENT, charset);
          firePropertyChange(PROP_CACHED_ENCODING_CHANGED, oldCached, charset);
        }
      }
    });
  }

  @Override
  public void dispose() {
    updateEncodingFromContent.cancelAllRequests();
    clearDocumentQueue();
  }

  public void queueUpdateEncodingFromContent(@NotNull Document document) {
    myChangedDocuments.offerIfAbsent(new WeakReference<Document>(document), REFERENCE_EQUALITY);
  }

  @Override
  @Nullable
  public Charset getCachedCharsetFromContent(@NotNull Document document) {
    return document.getUserData(CACHED_CHARSET_FROM_CONTENT);
  }

  @Override
  public Element getState() {
    Element result = new Element("x");
    result.setAttribute("default_encoding", myDefaultEncoding);
    return result;
  }

  @Override
  public void loadState(final Element state) {
    myCachedCharset = null;
    myDefaultEncoding = state.getAttributeValue("default_encoding");
  }

  @Override
  @NotNull
  public Collection<Charset> getFavorites() {
    Set<Charset> result = new THashSet<Charset>();
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      result.addAll(EncodingProjectManager.getInstance(project).getFavorites());
    }
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
    myChangedDocuments.stop();
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
    Project project = guessProject(virtualFile);
    return project != null && EncodingProjectManager.getInstance(project).isUseUTFGuessing(virtualFile);
  }

  @Override
  public void setUseUTFGuessing(final VirtualFile virtualFile, final boolean useUTFGuessing) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setUseUTFGuessing(virtualFile, useUTFGuessing);
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
  @Nullable
  public Charset getDefaultCharset() {
    Charset result = myCachedCharset;
    if (result == null) {
      result = cacheCharset();
      myCachedCharset = result;
    }

    return result;
  }

  @Override
  @Nullable
  public String getDefaultCharsetName() {
    return myDefaultEncoding;
  }

  @Override
  public void setDefaultCharsetName(final String name) {
    myDefaultEncoding = name;
    myCachedCharset = null;
  }

  @Nullable
  private Charset cacheCharset() {
    Charset result = CharsetToolkit.getDefaultSystemCharset();
    if (!StringUtil.isEmpty(myDefaultEncoding)) {
      try {
        result = Charset.forName(myDefaultEncoding);
      }
      catch (Exception e) {
        // Do nothing
      }
    }

    return result;
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
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener){
    myPropertyChangeSupport.addPropertyChangeListener(listener);
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

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener){
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }
  void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    myPropertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }
}
