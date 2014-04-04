package org.jetbrains.debugger.sourcemap;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import com.intellij.util.UrlImpl;
import com.intellij.util.Urls;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.io.URLUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.LocalFileFinder;

import java.util.List;

public class SourceResolver {
  private final List<String> rawSources;

  final Url[] canonicalizedSources;
  final ObjectIntHashMap<Url> canonicalizedSourcesMap;

  private TObjectIntHashMap<String> absoluteLocalPathToSourceIndex;
  // absoluteLocalPathToSourceIndex contains canonical paths too, but this map contains only used (specified in the source map) path
  private String[] sourceIndexToAbsoluteLocalPath;

  public SourceResolver(@NotNull List<String> sources, boolean trimFileScheme, @Nullable Url baseFileUrl) {
    rawSources = sources;
    canonicalizedSources = new Url[sources.size()];
    canonicalizedSourcesMap = new ObjectIntHashMap<Url>(canonicalizedSources.length);
    for (int i = 0; i < sources.size(); i++) {
      Url url = canonicalizeUrl(sources.get(i), baseFileUrl, trimFileScheme, i);
      canonicalizedSources[i] = url;
      canonicalizedSourcesMap.put(url, i);
    }
  }

  public static boolean isAbsolute(@NotNull String path) {
    return !path.isEmpty() && (path.charAt(0) == '/' || (SystemInfo.isWindows && (path.length() > 2 && path.charAt(1) == ':')));
  }

  // see canonicalizeUri kotlin impl and https://trac.webkit.org/browser/trunk/Source/WebCore/inspector/front-end/ParsedURL.js completeURL
  private Url canonicalizeUrl(@NotNull String url, @Nullable Url baseUrl, boolean trimFileScheme, int sourceIndex) {
    if (trimFileScheme && url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
      return Urls.newLocalFileUrl(FileUtil.toCanonicalPath(VfsUtilCore.toIdeaUrl(url, true).substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length()), '/'));
    }
    else if (baseUrl == null || url.contains(URLUtil.SCHEME_SEPARATOR) || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:")) {
      return Urls.parseEncoded(url);
    }

    String path = url;
    if (url.charAt(0) != '/') {
      String basePath = baseUrl.getPath();
      int lastSlashIndex = basePath.lastIndexOf('/');
      StringBuilder pathBuilder = new StringBuilder();
      if (lastSlashIndex == -1) {
        pathBuilder.append(basePath).append('/');
      }
      else {
        pathBuilder.append(basePath, 0, lastSlashIndex + 1);
      }
      path = pathBuilder.append(url).toString();
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
          absoluteLocalPathToSourceIndex = new TObjectIntHashMap<String>(rawSources.size());
          sourceIndexToAbsoluteLocalPath = new String[rawSources.size()];
        }
        absoluteLocalPathToSourceIndex.put(path, sourceIndex);
        sourceIndexToAbsoluteLocalPath[sourceIndex] = path;
        String canonicalPath = file.getCanonicalPath();
        if (canonicalPath != null && !canonicalPath.equals(path)) {
          absoluteLocalPathToSourceIndex.put(canonicalPath, sourceIndex);
        }
      }
    }
    return new UrlImpl(baseUrl.getScheme(), baseUrl.getAuthority(), path, null);
  }

  @Nullable
  public Url getSource(@NotNull MappingEntry entry) {
    int index = entry.getSource();
    return index < 0 ? null : canonicalizedSources[index];
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
    int resolve(@NotNull ObjectIntHashMap<Url> map);
  }

  @Nullable
  public MappingList findMappings(@NotNull SourceMap sourceMap, @NotNull Resolver resolver) {
    int index = resolver.resolve(canonicalizedSourcesMap);
    return index < 0 ? null : sourceMap.sourceIndexToMappings[index];
  }

  @Nullable
  public MappingList findMappings(@NotNull List<Url> sourceUrls, @NotNull SourceMap sourceMap, @Nullable VirtualFile sourceFile) {
    for (Url sourceUrl : sourceUrls) {
      int index = canonicalizedSourcesMap.get(sourceUrl.trimParameters());
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
  private MappingList findByFile(@NotNull SourceMap sourceMap, @NotNull VirtualFile sourceFile) {
    MappingList mappings = null;
    if (absoluteLocalPathToSourceIndex != null && sourceFile.isInLocalFileSystem()) {
      mappings = sourceMap.sourceIndexToMappings[absoluteLocalPathToSourceIndex.get(sourceFile.getPath())];
      if (mappings == null) {
        String sourceFileCanonicalPath = sourceFile.getCanonicalPath();
        if (sourceFileCanonicalPath != null) {
          mappings = sourceMap.sourceIndexToMappings[absoluteLocalPathToSourceIndex.get(sourceFileCanonicalPath)];
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