// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.reference.SoftReference.dereference;

public final class ContentRevisionCache {
  private final Object myLock;
  private final SLRUMap<Key, SoftReference<byte[]>> myCache;
  private final Map<Key, byte[]> myConstantCache = new HashMap<>();

  public ContentRevisionCache() {
    myLock = new Object();
    myCache = new SLRUMap<>(100, 50);
  }

  private void put(@NotNull FilePath path,
                   @NotNull VcsRevisionNumber number,
                   @NotNull VcsKey vcsKey,
                   @NotNull UniqueType type,
                   byte @Nullable [] bytes) {
    if (bytes == null) return;
    synchronized (myLock) {
      myCache.put(new Key(path, number, vcsKey, type), new SoftReference<>(bytes));
    }
  }

  @Contract("!null, _, _ -> !null")
  public static @Nullable String getAsString(byte @Nullable [] bytes, @NotNull FilePath file, @Nullable Charset charset) {
    if (bytes == null) return null;
    if (charset == null) {
      return bytesToString(file, bytes);
    }
    else {
      return CharsetToolkit.bytesToString(bytes, charset);
    }
  }

  public static @NotNull String getOrLoadAsString(@NotNull Project project,
                                                  @NotNull FilePath file,
                                                  @NotNull VcsRevisionNumber number,
                                                  @NotNull VcsKey key,
                                                  @NotNull UniqueType type,
                                                  @NotNull Throwable2Computable<byte[], ? extends VcsException, ? extends IOException> loader,
                                                  @Nullable Charset charset)
    throws VcsException, IOException {
    final byte[] bytes = getOrLoadAsBytes(project, file, number, key, type, loader);
    return getAsString(bytes, file, charset);
  }


  public static @NotNull String getOrLoadAsString(@NotNull Project project,
                                                  @NotNull FilePath path,
                                                  @NotNull VcsRevisionNumber number,
                                                  @NotNull VcsKey vcsKey,
                                                  @NotNull UniqueType type,
                                                  @NotNull Throwable2Computable<byte[], ? extends VcsException, ? extends IOException> loader)
    throws VcsException, IOException {
    return getOrLoadAsString(project, path, number, vcsKey, type, loader, null);
  }

  private static @NotNull String bytesToString(FilePath path, byte @NotNull [] bytes) {
    Charset charset = null;
    if (path.getVirtualFile() != null) {
      charset = path.getVirtualFile().getCharset();
    }

    if (charset != null) {
      int bomLength = CharsetToolkit.getBOMLength(bytes, charset);
      final CharBuffer charBuffer = charset.decode(ByteBuffer.wrap(bytes, bomLength, bytes.length - bomLength));
      return charBuffer.toString();
    }

    return CharsetToolkit.bytesToString(bytes, EncodingRegistry.getInstance().getDefaultCharset());
  }

  public byte @Nullable [] getBytes(FilePath path, VcsRevisionNumber number, @NotNull VcsKey vcsKey, @NotNull UniqueType type) {
    synchronized (myLock) {
      final SoftReference<byte[]> reference = myCache.get(new Key(path, number, vcsKey, type));
      return dereference(reference);
    }
  }

  public static byte @NotNull [] loadAsBytes(@NotNull FilePath path,
                                             Throwable2Computable<byte @NotNull [], ? extends VcsException, ? extends IOException> loader)
    throws VcsException, IOException {
    checkLocalFileSize(path);
    return loader.compute();
  }

  public static byte @NotNull [] getOrLoadAsBytes(@NotNull Project project,
                                                  @NotNull FilePath path,
                                                  @NotNull VcsRevisionNumber number,
                                                  @NotNull VcsKey vcsKey,
                                                  @NotNull UniqueType type,
                                                  @NotNull Throwable2Computable<byte @NotNull [], ? extends VcsException, ? extends IOException> loader)
    throws VcsException, IOException {
    ContentRevisionCache cache = ProjectLevelVcsManager.getInstance(project).getContentRevisionCache();
    byte[] bytes = cache.getBytes(path, number, vcsKey, type);
    if (bytes != null) {
      return bytes;
    }
    bytes = cache.getFromConstantCache(path, number, vcsKey, type);
    if (bytes != null) {
      return bytes;
    }

    checkLocalFileSize(path);
    bytes = loader.compute();
    cache.put(path, number, vcsKey, type, bytes);
    return bytes;
  }

  private static void checkLocalFileSize(@NotNull FilePath path) throws VcsException {
    File ioFile = path.getIOFile();
    if (ioFile.exists()) {
      checkContentsSize(ioFile.getPath(), ioFile.length());
    }
  }

  public static void checkContentsSize(final String path, final long size) throws VcsException {
    if (size > VcsUtil.getMaxVcsLoadedFileSize()) {
      throw new VcsException(VcsBundle.message("file.content.too.big.to.load.increase.property.suggestion", path,
                                               StringUtil.formatFileSize(VcsUtil.getMaxVcsLoadedFileSize()),
                                               VcsUtil.MAX_VCS_LOADED_SIZE_KB));
    }
  }

  public void putIntoConstantCache(@NotNull FilePath path,
                                   @NotNull VcsRevisionNumber revisionNumber,
                                   @NotNull VcsKey vcsKey,
                                   byte[] content) {
    synchronized (myConstantCache) {
      myConstantCache.put(new Key(path, revisionNumber, vcsKey, UniqueType.REPOSITORY_CONTENT), content);
    }
  }

  public byte[] getFromConstantCache(@NotNull FilePath path,
                                     @NotNull VcsRevisionNumber revisionNumber,
                                     @NotNull VcsKey vcsKey,
                                     @NotNull UniqueType type) {
    synchronized (myConstantCache) {
      return myConstantCache.get(new Key(path, revisionNumber, vcsKey, type));
    }
  }

  public void clearConstantCache() {
    myConstantCache.clear();
  }

  public static Pair<VcsRevisionNumber, byte[]> getOrLoadCurrentAsBytes(@NotNull Project project,
                                                                        @NotNull FilePath path,
                                                                        @NotNull VcsKey vcsKey,
                                                                        @NotNull CurrentRevisionProvider loader)
    throws VcsException, IOException {
    ContentRevisionCache cache = ProjectLevelVcsManager.getInstance(project).getContentRevisionCache();

    while (true) {
      VcsRevisionNumber currentRevision = loader.getCurrentRevision();

      final byte[] cachedCurrent = cache.getBytes(path, currentRevision, vcsKey, UniqueType.REPOSITORY_CONTENT);
      if (cachedCurrent != null) {
        return Pair.create(currentRevision, cachedCurrent);
      }

      checkLocalFileSize(path);
      Pair<VcsRevisionNumber, byte[]> loaded = loader.get();

      if (loaded.getFirst().equals(currentRevision)) {
        cache.put(path, currentRevision, vcsKey, UniqueType.REPOSITORY_CONTENT, loaded.getSecond());
        return loaded;
      }
    }
  }

  private static class CurrentKey {
    protected final FilePath myPath;
    protected final VcsKey myVcsKey;

    private CurrentKey(FilePath path, VcsKey vcsKey) {
      myPath = path;
      myVcsKey = vcsKey;
    }

    public FilePath getPath() {
      return myPath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CurrentKey that = (CurrentKey)o;

      if (myPath != null ? !myPath.equals(that.myPath) : that.myPath != null) return false;
      if (myVcsKey != null ? !myVcsKey.equals(that.myVcsKey) : that.myVcsKey != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPath != null ? myPath.hashCode() : 0;
      result = 31 * result + (myVcsKey != null ? myVcsKey.hashCode() : 0);
      return result;
    }
  }

  private static final class Key extends CurrentKey {
    private final VcsRevisionNumber myNumber;
    private final UniqueType myType;

    private Key(FilePath path, VcsRevisionNumber number, VcsKey vcsKey, UniqueType type) {
      super(path, vcsKey);
      myNumber = number;
      myType = type;
    }

    public VcsRevisionNumber getNumber() {
      return myNumber;
    }

    public UniqueType getType() {
      return myType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      Key key = (Key)o;

      if (myNumber != null ? !myNumber.equals(key.myNumber) : key.myNumber != null) return false;
      if (!myPath.equals(key.myPath)) return false;
      if (myType != key.myType) return false;
      if (!myVcsKey.equals(key.myVcsKey)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myPath.hashCode();
      result = 31 * result + (myNumber != null ? myNumber.hashCode() : 0);
      result = 31 * result + myVcsKey.hashCode();
      result = 31 * result + myType.hashCode();
      return result;
    }
  }

  public enum UniqueType {
    REPOSITORY_CONTENT,
    REMOTE_CONTENT
  }

  public void clearAll() {
    synchronized (myLock) {
      myCache.clear();
      myConstantCache.clear();
    }
  }
}
