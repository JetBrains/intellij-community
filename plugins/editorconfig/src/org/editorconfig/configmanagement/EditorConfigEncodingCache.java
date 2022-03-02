// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.ObjectUtils;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
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
  private final static String IGNORE_ATTR = "ignore";

  private final Map<String, CharsetData> myCharsetMap = new ConcurrentHashMap<>();

  public static EditorConfigEncodingCache getInstance() {
    return ApplicationManager.getApplication().getService(EditorConfigEncodingCache.class);
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
          if (charsetData.isIgnored()) {
            entryElement.setAttribute(IGNORE_ATTR, Boolean.toString(charsetData.isIgnored()));
          }
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
            CharsetData charsetData = new CharsetData(charset, useBom);
            myCharsetMap.put(url, charsetData);
            Attribute ignoreAttr = fileElement.getAttribute(IGNORE_ATTR);
            if (ignoreAttr != null) {
              try {
                charsetData.setIgnored(ignoreAttr.getBooleanValue());
              }
              catch (DataConversionException e) {
                // Ignore, do not set
              }
            }
          }
        }
      }
    }
  }

  public boolean getUseUtf8Bom(@Nullable Project project, @NotNull VirtualFile virtualFile) {
    return ObjectUtils.notNull(
      ObjectUtils.doIfNotNull(getCharsetData(project, virtualFile, true), CharsetData::isUseBom), false);
  }

  @Nullable
  CharsetData getCharsetData(@Nullable Project project, @NotNull VirtualFile virtualFile, boolean withCache) {
    if (!Utils.isApplicableTo(virtualFile) || Utils.isEditorConfigFile(virtualFile)) return null;
    if (withCache) {
      CharsetData cached = getCachedCharsetData(virtualFile);
      if (cached != null) return cached;
    }
    if (project != null) {
      return computeCharsetData(project, virtualFile);
    }
    return null;
  }

  private static @Nullable CharsetData computeCharsetData(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, virtualFile);
    final String charsetStr = Utils.configValueForKey(outPairs, ConfigEncodingManager.charsetKey);
    if (!charsetStr.isEmpty()) {
      final Charset charset = ConfigEncodingManager.toCharset(charsetStr);
      final boolean useBom = ConfigEncodingManager.UTF8_BOM_ENCODING.equals(charsetStr);
      if (charset != null) {
        return new CharsetData(charset, useBom);
      }
    }
    return null;
  }

  public void computeAndCacheEncoding(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    final String key = getKey(virtualFile);
    final CharsetData charsetData = getCharsetData(project, virtualFile, false);
    if (charsetData != null) {
      myCharsetMap.put(key, charsetData);
      virtualFile.setCharset(charsetData.charset);
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

  public boolean isIgnored(@NotNull VirtualFile virtualFile) {
    CharsetData charsetData = getCachedCharsetData(virtualFile);
    return charsetData != null && charsetData.isIgnored();
  }

  public void setIgnored(@NotNull VirtualFile virtualFile) {
    CharsetData charsetData = getCachedCharsetData(virtualFile);
    if (charsetData == null) {
      charsetData = new CharsetData(Charset.defaultCharset(), false);
      charsetData.setIgnored(true);
      myCharsetMap.put(getKey(virtualFile), charsetData);
    }
    else {
      charsetData.setIgnored(true);
    }
  }

  @NotNull
  private static String getKey(@NotNull VirtualFile virtualFile) {
    return virtualFile.getUrl();
  }

  public final void reset() {
    myCharsetMap.clear();
  }

  static class CharsetData {
    private final Charset charset;
    private final boolean useBom;
    private boolean isIgnored;

    CharsetData(Charset charset, boolean useBom) {
      this.charset = charset;
      this.useBom = useBom;
    }

    Charset getCharset() {
      return charset;
    }

    boolean isUseBom() {
      return useBom;
    }

    boolean isIgnored() {
      return isIgnored;
    }

    void setIgnored(boolean isIgnored) {
      this.isIgnored = isIgnored;
    }
  }

  public static class VfsListener extends BulkVirtualFileListenerAdapter {

    public VfsListener() {
      super(new VirtualFileListener() {
        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
          VirtualFile file = event.getFile();
          Project project = ProjectLocator.getInstance().guessProjectForFile(file);
          if (project != null && ConfigEncodingManager.isEnabledFor(project, file)) {
            getInstance().computeAndCacheEncoding(project, event.getFile());
          }
        }
      });
    }

  }
}
