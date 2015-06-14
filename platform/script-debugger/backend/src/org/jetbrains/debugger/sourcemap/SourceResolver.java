/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.debugger.sourcemap;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Url;
import com.intellij.util.UrlImpl;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.LocalFileFinder;

import java.util.List;

public class SourceResolver {
  private final List<String> rawSources;
  @Nullable private final List<String> sourceContents;

  final Url[] canonicalizedSources;
  private final ObjectIntHashMap<Url> canonicalizedSourcesMap;

  private TObjectIntHashMap<String> absoluteLocalPathToSourceIndex;
  // absoluteLocalPathToSourceIndex contains canonical paths too, but this map contains only used (specified in the source map) path
  private String[] sourceIndexToAbsoluteLocalPath;

  public SourceResolver(@NotNull List<String> sourceUrls, boolean trimFileScheme, @Nullable Url baseFileUrl, @Nullable List<String> sourceContents) {
    this(sourceUrls, trimFileScheme, baseFileUrl, true, sourceContents);
  }

  public SourceResolver(@NotNull List<String> sourceUrls, boolean trimFileScheme, @Nullable Url baseFileUrl, boolean baseUrlIsFile, @Nullable List<String> sourceContents) {
    rawSources = sourceUrls;
    this.sourceContents = sourceContents;
    canonicalizedSources = new Url[sourceUrls.size()];
    canonicalizedSourcesMap = SystemInfo.isFileSystemCaseSensitive
                              ? new ObjectIntHashMap<Url>(canonicalizedSources.length)
                              : new ObjectIntHashMap<Url>(canonicalizedSources.length, Urls.getCaseInsensitiveUrlHashingStrategy());
    for (int i = 0; i < sourceUrls.size(); i++) {
      String rawSource = sourceUrls.get(i);
      Url url = canonicalizeUrl(rawSource, baseFileUrl, trimFileScheme, i, baseUrlIsFile);
      canonicalizedSources[i] = url;
      canonicalizedSourcesMap.put(url, i);
    }
  }

  public static boolean isAbsolute(@NotNull String path) {
    return !path.isEmpty() && (path.charAt(0) == '/' || (SystemInfo.isWindows && (path.length() > 2 && path.charAt(1) == ':')));
  }

  // see canonicalizeUri kotlin impl and https://trac.webkit.org/browser/trunk/Source/WebCore/inspector/front-end/ParsedURL.js completeURL
  protected Url canonicalizeUrl(@NotNull String url, @Nullable Url baseUrl, boolean trimFileScheme, int sourceIndex, boolean baseUrlIsFile) {
    if (trimFileScheme && url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
      return Urls.newLocalFileUrl(FileUtil.toCanonicalPath(VfsUtilCore.toIdeaUrl(url, true).substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length()), '/'));
    }
    else if (baseUrl == null || url.contains(URLUtil.SCHEME_SEPARATOR) || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:")) {
      return Urls.parseEncoded(url);
    }

    String path = url;
    if (url.charAt(0) != '/') {
      String basePath = baseUrl.getPath();
      if (baseUrlIsFile) {
        int lastSlashIndex = basePath.lastIndexOf('/');
        StringBuilder pathBuilder = new StringBuilder();
        if (lastSlashIndex == -1) {
          pathBuilder.append('/');
        }
        else {
          pathBuilder.append(basePath, 0, lastSlashIndex + 1);
        }
        path = pathBuilder.append(url).toString();
      }
      else {
        path = basePath + '/' + url;
      }
    }
    path = FileUtil.toCanonicalPath(path, '/');

    if (baseUrl.getScheme() == null && baseUrl.isInLocalFileSystem()) {
      return Urls.newLocalFileUrl(path);
    }

    // browserify produces absolute path in the local filesystem
    if (isAbsolute(path)) {
      VirtualFile file = LocalFileFinder.findFile(path);
      if (file != null) {
        if (absoluteLocalPathToSourceIndex == null) {
          // must be linked, on iterate original path must be first
          absoluteLocalPathToSourceIndex = createStringIntMap(rawSources.size());
          sourceIndexToAbsoluteLocalPath = new String[rawSources.size()];
        }
        absoluteLocalPathToSourceIndex.put(path, sourceIndex);
        sourceIndexToAbsoluteLocalPath[sourceIndex] = path;
        String canonicalPath = file.getCanonicalPath();
        if (canonicalPath != null && !canonicalPath.equals(path)) {
          absoluteLocalPathToSourceIndex.put(canonicalPath, sourceIndex);
        }
        return Urls.newLocalFileUrl(path);
      }
    }
    return new UrlImpl(baseUrl.getScheme(), baseUrl.getAuthority(), path, null);
  }

  @NotNull
  private static ObjectIntHashMap<String> createStringIntMap(int initialCapacity) {
    if (initialCapacity == -1) {
      initialCapacity = 4;
    }
    return SystemInfo.isFileSystemCaseSensitive
           ? new ObjectIntHashMap<String>(initialCapacity)
           : new ObjectIntHashMap<String>(initialCapacity, CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  @Nullable
  public Url getSource(@NotNull MappingEntry entry) {
    int index = entry.getSource();
    return index < 0 ? null : canonicalizedSources[index];
  }

  @Nullable
  public String getSourceContent(@NotNull MappingEntry entry) {
    if (ContainerUtil.isEmpty(sourceContents)) {
      return null;
    }

    int index = entry.getSource();
    return index < 0 || index >= sourceContents.size() ? null : sourceContents.get(index);
  }

  @Nullable
  public String getSourceContent(int sourceIndex) {
    if (ContainerUtil.isEmpty(sourceContents)) {
      return null;
    }
    return sourceIndex < 0 || sourceIndex >= sourceContents.size() ? null : sourceContents.get(sourceIndex);
  }

  public int getSourceIndex(@NotNull Url url) {
    return ArrayUtil.indexOf(canonicalizedSources, url);
  }

  @Nullable
  public String getRawSource(@NotNull MappingEntry entry) {
    int index = entry.getSource();
    return index < 0 ? null : rawSources.get(index);
  }

  @Nullable
  public String getLocalFilePath(@NotNull MappingEntry entry) {
    final int index = entry.getSource();
    return index < 0 || sourceIndexToAbsoluteLocalPath == null ? null : sourceIndexToAbsoluteLocalPath[index];
  }

  public interface Resolver {
    int resolve(@Nullable VirtualFile sourceFile, @NotNull ObjectIntHashMap<Url> map);
  }

  @Nullable
  public MappingList findMappings(@Nullable VirtualFile sourceFile, @NotNull SourceMap sourceMap, @NotNull Resolver resolver) {
    int index = resolver.resolve(sourceFile, canonicalizedSourcesMap);
    return index < 0 ? null : sourceMap.sourceIndexToMappings[index];
  }

  @Nullable
  public MappingList findMappings(@NotNull List<Url> sourceUrls, @NotNull SourceMap sourceMap, @Nullable VirtualFile sourceFile) {
    for (Url sourceUrl : sourceUrls) {
      int index = canonicalizedSourcesMap.get(sourceUrl);
      if (index != -1) {
        return sourceMap.sourceIndexToMappings[index];
      }
    }

    if (sourceFile != null) {
      MappingList mappings = findByFile(sourceMap, sourceFile);
      if (mappings != null) {
        return mappings;
      }
    }

    return null;
  }

  @Nullable
  private static MappingList getMappingsBySource(@NotNull SourceMap sourceMap, int index) {
    return index == -1 ? null : sourceMap.sourceIndexToMappings[index];
  }

  @Nullable
  private MappingList findByFile(@NotNull SourceMap sourceMap, @NotNull VirtualFile sourceFile) {
    MappingList mappings = null;
    if (absoluteLocalPathToSourceIndex != null && sourceFile.isInLocalFileSystem()) {
      mappings = getMappingsBySource(sourceMap, absoluteLocalPathToSourceIndex.get(sourceFile.getPath()));
      if (mappings == null) {
        String sourceFileCanonicalPath = sourceFile.getCanonicalPath();
        if (sourceFileCanonicalPath != null) {
          mappings = getMappingsBySource(sourceMap, absoluteLocalPathToSourceIndex.get(sourceFileCanonicalPath));
        }
      }
    }

    if (mappings == null) {
      int index = canonicalizedSourcesMap.get(Urls.newFromVirtualFile(sourceFile).trimParameters());
      if (index != -1) {
        return sourceMap.sourceIndexToMappings[index];
      }

      for (int i = 0; i < canonicalizedSources.length; i++) {
        Url url = canonicalizedSources[i];
        if (Urls.equalsIgnoreParameters(url, sourceFile)) {
          return sourceMap.sourceIndexToMappings[i];
        }

        VirtualFile canonicalFile = sourceFile.getCanonicalFile();
        if (canonicalFile != null && !canonicalFile.equals(sourceFile) && Urls.equalsIgnoreParameters(url, canonicalFile)) {
          return sourceMap.sourceIndexToMappings[i];
        }
      }
    }
    return mappings;
  }
}