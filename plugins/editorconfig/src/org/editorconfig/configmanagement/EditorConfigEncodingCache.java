// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ObjectUtils;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@State(name = "editorConfigEncodings", storages = @Storage(StoragePathMacros.CACHE_FILE))
public class EditorConfigEncodingCache implements PersistentStateComponent<Element> {
  private final static String ENTRY_ELEMENT = "file";
  private final static String URL_ATTR = "url";
  private final static String CHARSET_ATTR = "charset";

  private final Map<String, CharsetData> myCharsetMap = new ConcurrentHashMap<>();

  public static EditorConfigEncodingCache getInstance() {
    return ServiceManager.getService(EditorConfigEncodingCache.class);
  }

  @Override
  public @Nullable Element getState() {
    final Element root = new Element("encodings");
    for (String url : myCharsetMap.keySet()) {
      final CharsetData charsetData = myCharsetMap.get(url);
      if (charsetData != null) {
        String charsetStr = ConfigEncodingManager.toString(charsetData.charset, charsetData.useBom);
        if (charsetStr != null) {
          final Element entryElement = new Element(ENTRY_ELEMENT);
          final Attribute urlAttr = new Attribute(URL_ATTR, url);
          final Attribute charsetAttr = new Attribute(CHARSET_ATTR, charsetStr);
          entryElement.setAttribute(urlAttr);
          entryElement.setAttribute(charsetAttr);
          root.addContent(entryElement);
        }
      }
    }
    return root;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myCharsetMap.clear();
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    for (Element fileElement : state.getChildren(ENTRY_ELEMENT)) {
      final Attribute urlAttr = fileElement.getAttribute(URL_ATTR);
      final Attribute charsetAttr =  fileElement.getAttribute(CHARSET_ATTR);
      if (urlAttr != null && charsetAttr != null) {
        final String url = urlAttr.getValue();
        String charsetStr = charsetAttr.getValue();
        final Charset charset = ConfigEncodingManager.toCharset(charsetStr);
        final boolean useBom = ConfigEncodingManager.UTF8_BOM_ENCODING.equals(charsetStr);
        if (charset != null) {
          VirtualFile vf = vfManager.findFileByUrl(url);
          if (vf != null) {
            myCharsetMap.put(url, new CharsetData(charset, useBom));
          }
        }
      }
    }
  }

  public boolean getUseUtf8Bom(@Nullable Project project, @NotNull VirtualFile virtualFile) {
    return ObjectUtils.notNull(
      ObjectUtils.doIfNotNull(getCharsetData(project, virtualFile), CharsetData::isUseBom), false);
  }

  @Nullable
  public Charset getEncoding(@Nullable Project project, @NotNull VirtualFile virtualFile) {
    return ObjectUtils.doIfNotNull(getCharsetData(project, virtualFile), CharsetData::getCharset);
  }

  @Nullable
  private CharsetData getCharsetData(@Nullable Project project, @NotNull VirtualFile virtualFile) {
    if (!Utils.isApplicableTo(virtualFile) || Utils.isEditorConfigFile(virtualFile)) return null;
    CharsetData cached = getCachedCharsetData(virtualFile);
    if (cached != null) return cached;
    if (project != null) {
      final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, virtualFile);
      final String charsetStr = Utils.configValueForKey(outPairs, ConfigEncodingManager.charsetKey);
      if (!charsetStr.isEmpty()) {
        final Charset charset = ConfigEncodingManager.toCharset(charsetStr);
        final boolean useBom = ConfigEncodingManager.UTF8_BOM_ENCODING.equals(charsetStr);
        if (charset != null) {
          return new CharsetData(charset, useBom);
        }
      }
    }
    return null;
  }

  private void cacheEncoding(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    final String key = getKey(virtualFile);
    if (!myCharsetMap.containsKey(key)) {
      final CharsetData charsetData = getCharsetData(project, virtualFile);
      if (charsetData != null) {
        myCharsetMap.put(key, charsetData);
      }
    }
  }

  @Nullable
  public Charset getCachedEncoding(@NotNull VirtualFile virtualFile) {
    return ObjectUtils.doIfNotNull(getCachedCharsetData(virtualFile), CharsetData::getCharset);
  }

  @Nullable
  private CharsetData getCachedCharsetData(@NotNull VirtualFile virtualFile) {
    return myCharsetMap.get(getKey(virtualFile));
  }

  @NotNull
  private static String getKey(@NotNull VirtualFile virtualFile) {
    return virtualFile.getUrl();
  }

  public final void reset() {
    myCharsetMap.clear();
  }

  private static class CharsetData {
    private final Charset charset;
    private final boolean useBom;

    CharsetData(Charset charset, boolean useBom) {
      this.charset = charset;
      this.useBom = useBom;
    }

    Charset getCharset() {
      return charset;
    }

    private boolean isUseBom() {
      return useBom;
    }
  }

  public static class FileEditorListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      getInstance().cacheEncoding(source.getProject(), file);
    }
  }
}
