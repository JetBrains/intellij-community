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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;


@State(
  name = "Encoding",
  storages = {
      @Storage(id = "Encoding", file = "$APP_CONFIG$/encoding.xml")
  }
)
public class EncodingManagerImpl extends EncodingManager implements PersistentStateComponent<Element>, Disposable {
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private String myDefaultEncoding = "";
  private Charset myCachedCharset = null;

  private final Alarm updateEncodingFromContent = new Alarm(Alarm.ThreadToUse.OWN_THREAD, this);
  private static final Key<Charset> CACHED_CHARSET_FROM_CONTENT = Key.create("CACHED_CHARSET_FROM_CONTENT");
  private final Queue<Document> myChangedDocuments = new ConcurrentLinkedQueue<Document>();
  private final Runnable myEncodingUpdateRunnable = new Runnable() {
    public void run() {
      Document document = myChangedDocuments.poll();
      if (document == null) return;
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile == null) return;
      Project project = guessProject(virtualFile);
      if (project != null && project.isDisposed()) return;
      Charset charset = LoadTextUtil.charsetFromContentOrNull(project, virtualFile, document.getText());
      document.putUserData(CACHED_CHARSET_FROM_CONTENT, charset);
    }
  };

  public void dispose() {
    updateEncodingFromContent.cancelAllRequests();
  }

  public void updateEncodingFromContent(Document document) {
    myChangedDocuments.offer(document);
    updateEncodingFromContent.cancelAllRequests();
    updateEncodingFromContent.addRequest(myEncodingUpdateRunnable, 400);
  }

  public Charset getCachedCharsetFromContent(@NotNull Document document) {
    return document.getUserData(CACHED_CHARSET_FROM_CONTENT);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EncodingManager";
  }

  public void initComponent() {
    final DocumentAdapter myDocumentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        updateEncodingFromContent(e.getDocument());
      }
    };

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener, this);
  }

  public void disposeComponent() {
  }

  public Element getState() {
    Element result = new Element("x");
    result.setAttribute("default_encoding", myDefaultEncoding);
    return result;
  }

  public void loadState(final Element state) {
    myCachedCharset = null;
    myDefaultEncoding = state.getAttributeValue("default_encoding");
  }

  @NotNull
  public Collection<Charset> getFavorites() {
    Set<Charset> result = new THashSet<Charset>();
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      result.addAll(EncodingProjectManager.getInstance(project).getFavorites());
    }
    return result;
  }

  @Nullable
  public Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults) {
    Project project = guessProject(virtualFile);
    if (project == null) return null;
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(project);
    if (encodingManager == null) return null; //tests
    return encodingManager.getEncoding(virtualFile, useParentDefaults);
  }

  @Nullable
  private static Project guessProject(final VirtualFile virtualFile) {
    return ProjectLocator.getInstance().guessProjectForFile(virtualFile);
  }

  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
    Project project = guessProject(virtualFileOrDir);
    EncodingProjectManager.getInstance(project).setEncoding(virtualFileOrDir, charset);
  }

  public boolean isUseUTFGuessing(final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    return project != null && EncodingProjectManager.getInstance(project).isUseUTFGuessing(virtualFile);
  }

  public void setUseUTFGuessing(final VirtualFile virtualFile, final boolean useUTFGuessing) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setUseUTFGuessing(virtualFile, useUTFGuessing);
  }

  public boolean isNative2AsciiForPropertiesFiles(final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    return project != null && EncodingProjectManager.getInstance(project).isNative2AsciiForPropertiesFiles(virtualFile);
  }

  public void setNative2AsciiForPropertiesFiles(final VirtualFile virtualFile, final boolean native2Ascii) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setNative2AsciiForPropertiesFiles(virtualFile, native2Ascii);
  }

  public Charset getDefaultCharset() {
    Charset result = myCachedCharset;
    if (result == null) {
      result = cacheCharset();
      myCachedCharset = result;
    }

    return result;
  }

  public String getDefaultCharsetName() {
    return myDefaultEncoding;
  }

  public void setDefaultCharsetName(final String name) {
    myDefaultEncoding = name;
    myCachedCharset = null;
  }

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

  public Charset getDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    if (project == null) return null;
    return EncodingProjectManager.getInstance(project).getDefaultCharsetForPropertiesFiles(virtualFile);
  }

  public void setDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile, final Charset charset) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setDefaultCharsetForPropertiesFiles(virtualFile, charset);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }
  void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    myPropertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }
}
