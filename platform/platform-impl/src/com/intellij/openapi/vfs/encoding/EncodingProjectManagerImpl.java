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

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.nio.charset.Charset;
import java.util.*;

@State(
  name = "Encoding",
  storages = {
    @Storage( file = "$PROJECT_FILE$"),
    @Storage( file = "$PROJECT_CONFIG_DIR$/encodings.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class EncodingProjectManagerImpl extends EncodingProjectManager {
  private final Project myProject;
  private final GeneralSettings myGeneralSettings;
  private final EditorSettingsExternalizable myEditorSettings;
  private boolean myUseUTFGuessing = true;
  private boolean myNative2AsciiForPropertiesFiles;
  private Charset myDefaultCharsetForPropertiesFiles;

  public EncodingProjectManagerImpl(Project project, GeneralSettings generalSettings, EditorSettingsExternalizable editorSettings, PsiDocumentManager documentManager) {
    myProject = project;
    myGeneralSettings = generalSettings;
    myEditorSettings = editorSettings;
    documentManager.addListener(new PsiDocumentManager.Listener() {
      @Override
      public void documentCreated(@NotNull Document document, PsiFile psiFile) {
        ((EncodingManagerImpl)EncodingManager.getInstance()).queueUpdateEncodingFromContent(document);
      }

      @Override
      public void fileCreated(@NotNull PsiFile file, @NotNull Document document) {
      }
    });
  }

  //null key means project
  private final Map<VirtualFile, Charset> myMapping = new HashMap<VirtualFile, Charset>();

  @Override
  public Element getState() {
    Element element = new Element("x");
    List<VirtualFile> files = new ArrayList<VirtualFile>(myMapping.keySet());
    ContainerUtil.quickSort(files, new Comparator<VirtualFile>() {
      @Override
      public int compare(final VirtualFile o1, final VirtualFile o2) {
        if (o1 == null || o2 == null) return o1 == null ? o2 == null ? 0 : 1 : -1;
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    for (VirtualFile file : files) {
      Charset charset = myMapping.get(file);
      Element child = new Element("file");
      element.addContent(child);
      child.setAttribute("url", file == null ? "PROJECT" : file.getUrl());
      child.setAttribute("charset", charset.name());
    }
    element.setAttribute("useUTFGuessing", Boolean.toString(myUseUTFGuessing));
    element.setAttribute("native2AsciiForPropertiesFiles", Boolean.toString(myNative2AsciiForPropertiesFiles));
    if (myDefaultCharsetForPropertiesFiles != null) {
      element.setAttribute("defaultCharsetForPropertiesFiles", myDefaultCharsetForPropertiesFiles.name());
    }
    return element;
  }

  @Override
  public void loadState(Element element) {
    List<Element> files = element.getChildren("file");
    for (Element fileElement : files) {
      String url = fileElement.getAttributeValue("url");
      String charsetName = fileElement.getAttributeValue("charset");
      Charset charset = CharsetToolkit.forName(charsetName);
      if (charset == null) continue;
      VirtualFile file = url.equals("PROJECT") ? null : VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null || url.equals("PROJECT")) {
        myMapping.put(file, charset);
      }
    }
    myUseUTFGuessing = Boolean.parseBoolean(element.getAttributeValue("useUTFGuessing"));
    myNative2AsciiForPropertiesFiles = Boolean.parseBoolean(element.getAttributeValue("native2AsciiForPropertiesFiles"));
    myDefaultCharsetForPropertiesFiles = CharsetToolkit.forName(element.getAttributeValue("defaultCharsetForPropertiesFiles"));

    boolean migrated = myGeneralSettings.migrateCharsetSettingsTo(this);
    migrated |= myEditorSettings.migrateCharsetSettingsTo(this);
    if (migrated) {
      // load up default project only if some settings have been migrated
      EncodingProjectManager defaultManager = getInstance(ProjectManager.getInstance().getDefaultProject());
      if (defaultManager != null) {
        myGeneralSettings.migrateCharsetSettingsTo(defaultManager);
        myEditorSettings.migrateCharsetSettingsTo(defaultManager);
      }
    }
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "EncodingProjectManager";
  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  @Override
  public void projectOpened() {

  }

  @Override
  public void projectClosed() {

  }

  @Override
  @Nullable
  public Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults) {
    VirtualFile parent = virtualFile;
    while (true) {
      Charset charset = myMapping.get(parent);
      if (charset != null || !useParentDefaults) return charset;
      if (parent == null) break;
      parent = parent.getParent();
    }
    return null;
  }

  @Override
  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
    if (charset == null) {
      myMapping.remove(virtualFileOrDir);
    }
    else {
      myMapping.put(virtualFileOrDir, charset);
    }
    setAndSaveOrReload(virtualFileOrDir, charset);
  }

  private static void setAndSaveOrReload(final VirtualFile virtualFileOrDir, final Charset charset) {
    if (virtualFileOrDir == null || virtualFileOrDir.isDirectory()) {
      return;
    }
    virtualFileOrDir.setCharset(charset);
    LoadTextUtil.setCharsetWasDetectedFromBytes(virtualFileOrDir, false);
    saveOrReload(virtualFileOrDir);
  }

  private static void saveOrReload(final VirtualFile virtualFile) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    if (documentManager.isFileModified(virtualFile)) {
      Document document = documentManager.getDocument(virtualFile);
      if (document != null) {
        documentManager.saveDocument(document);
      }
    }
    else {
      ((VirtualFileListener)documentManager).contentsChanged(new VirtualFileEvent(null, virtualFile, virtualFile.getName(), virtualFile.getParent()));
    }
  }

  @Override
  @NotNull
  public Collection<Charset> getFavorites() {
    Set<Charset> result = new THashSet<Charset>();
    result.addAll(myMapping.values());
    result.add(CharsetToolkit.UTF8_CHARSET);
    result.add(CharsetToolkit.getDefaultSystemCharset());
    return result;
  }

  @Override
  public Map<VirtualFile, Charset> getAllMappings() {
    return myMapping;
  }

  @Override
  public void setMapping(final Map<VirtualFile, Charset> result) {
    Map<VirtualFile, Charset> map = new HashMap<VirtualFile, Charset>(result);
    //todo return it back as soon as FileIndex get to the platform
    //ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    //for (VirtualFile file : result.keySet()) {
    //  if (file != null && !fileIndex.isInContent(file)) {
    //    map.remove(file);
    //  }
    //}
    myMapping.clear();
    myMapping.putAll(map);
    for (VirtualFile virtualFile : map.keySet()) {
      Charset charset = map.get(virtualFile);
      assert charset != null;
      setAndSaveOrReload(virtualFile, charset);
    }
    if (!myProject.isDefault()) {
      for (VirtualFile open : FileEditorManager.getInstance(myProject).getOpenFiles()) {
        if (!map.containsKey(open)) {
          saveOrReload(open);
        }
      }
    }
  }

  //retrieves encoding for the Project node
  @Override
  @Nullable
  public Charset getDefaultCharset() {
    Charset charset = getEncoding(null, false);
    return charset == null ? EncodingManager.getInstance().getDefaultCharset() : charset;
  }

  @Override
  public boolean isUseUTFGuessing(final VirtualFile virtualFile) {
    return myUseUTFGuessing;
  }

  @Override
  public void setUseUTFGuessing(final VirtualFile virtualFile, final boolean useUTFGuessing) {
    myUseUTFGuessing = useUTFGuessing;
  }

  @Override
  public boolean isNative2Ascii(@NotNull final VirtualFile virtualFile) {
    return virtualFile.getFileType() == StdFileTypes.PROPERTIES && myNative2AsciiForPropertiesFiles;
  }

  @Override
  public boolean isNative2AsciiForPropertiesFiles() {
    return myNative2AsciiForPropertiesFiles;
  }

  @Override
  public void setNative2AsciiForPropertiesFiles(final VirtualFile virtualFile, final boolean native2Ascii) {
    if (myNative2AsciiForPropertiesFiles != native2Ascii) {
      myNative2AsciiForPropertiesFiles = native2Ascii;
      ((EncodingManagerImpl)EncodingManager.getInstance()).firePropertyChange(PROP_NATIVE2ASCII_SWITCH, !native2Ascii, native2Ascii);
    }
  }

  @Override
  @Nullable
  public Charset getDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile) {
    return myDefaultCharsetForPropertiesFiles;
  }

  @Override
  public void setDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile, @Nullable Charset charset) {
    Charset old = myDefaultCharsetForPropertiesFiles;
    if (!Comparing.equal(old, charset)) {
      myDefaultCharsetForPropertiesFiles = charset;
      ((EncodingManagerImpl)EncodingManager.getInstance()).firePropertyChange(PROP_PROPERTIES_FILES_ENCODING, old, charset);
    }
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener){
    EncodingManager.getInstance().addPropertyChangeListener(listener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable) {
    EncodingManager.getInstance().addPropertyChangeListener(listener,parentDisposable);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener){
    EncodingManager.getInstance().removePropertyChangeListener(listener);
  }

  @Override
  @Nullable
  public Charset getCachedCharsetFromContent(@NotNull Document document) {
    return EncodingManager.getInstance().getCachedCharsetFromContent(document);
  }
}
