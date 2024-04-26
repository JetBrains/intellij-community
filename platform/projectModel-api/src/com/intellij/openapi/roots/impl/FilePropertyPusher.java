// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FilePropertyKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/**
 * Represents a non-linear operation which is executed before indexing process
 * is started {@link PushedFilePropertiesUpdater#pushAllPropertiesNow()}.
 * <br />
 * During this process any pusher is allowed to set some properties to any of files being indexed.
 * <br />
 * Most frequently property represents some kind of "language level"
 * which is in most cases required to determine the algorithm of stub and other indexes building.
 * <br />
 * After property was pushed it can be retrieved any time using {@link FilePropertyPusher#getFilePropertyKey()}.
 * <br/>
 * The computation of the value (simplified) for a given {@link VirtualFile} works as follows:
 * <ul>
 * <li> the {@link #getImmediateValue(Project, VirtualFile)} is executed,
 *      a non-null result is returned</li>
 * <li> the {@link #getImmediateValue(Module)} is executed for the module of a file,
 *      a non-null result is retuned</li>
 * <li> iterate all parent file that is in content root of the file
 *      and try to return first non-null
 *      result of the {@link #getImmediateValue(Project, VirtualFile)} call
 * </li>
 * <li> try calling the {@link #getImmediateValue(Project, VirtualFile)} with {@code null} as {@link VirtualFile}
 *      and return a non-null value</li>
 * <li> fallback to {@link #getDefaultValue()} and return it</li>
 * </ul>
 * Once the value is computed, it calls the {@link #persistAttribute(Project, VirtualFile, Object)}
 * to test if the value is changed, if so, the implementation is responsible to notify back
 * <br/>
 * This API is intended to be used to prepare state for indexing, please do not use it for other purposes
 * <br/><br/>
 * <b>Note:</b> Don't use plugin-specific classes as pushed properties, consider using <code>String</code> instead.
 * Otherwise, the plugin becomes not dynamic because its classes will be hard-referenced as <code>VirtualFile</code>'s user data.
 * For example, instead of pushing <code>SomeLanguageDialect</code> instances, push <code>someLanguageDialect.getName()</code> and
 * restore <code>SomeLanguageDialect</code> by name where needed.
 */
public interface FilePropertyPusher<T> {
  ExtensionPointName<FilePropertyPusher<?>> EP_NAME = ExtensionPointName.create("com.intellij.filePropertyPusher");

  default void initExtra(@NotNull Project project) {
  }

  default void afterRootsChanged(@NotNull Project project) {}

  /**
   * After property was pushed it can be retrieved any time using {@code getFileDataKey()}
   * from {@link VirtualFile#getUserData(Key)}.
   *
   * @deprecated use {@link #getFilePropertyKey()} instead
   */
  @Deprecated(forRemoval = true)
  default @NotNull Key<T> getFileDataKey() {
    // Existing plugins always override this method, so default body is never executed in the existing plugins.
    // Default implementation of `getFileDataKey` is only invoked from default implementation of `getFilePropertyKey`.
    // If new plugin observe this exception, this means that it did not override getFilePropertyKey nor getFileDataKey.
    // (or invoked getFileDataKey explicitly, either way this should be found at development phase, not in runtime in the fields)
    throw new IllegalStateException("Please override getFilePropertyKey().");
  }

  /**
   * After property was pushed it can be retrieved at any time using {@link FilePropertyKey#getPersistentValue(VirtualFile)}
   */
  default @NotNull FilePropertyKey<T> getFilePropertyKey() {
    return new InMemoryFilePropertyKeyImpl<>(getFileDataKey());
  }

  boolean pushDirectoriesOnly();

  @NotNull
  T getDefaultValue();

  @Nullable
  T getImmediateValue(@NotNull Module module);

  @Nullable
  T getImmediateValue(@NotNull Project project, @Nullable VirtualFile file);

  default boolean acceptsFile(@NotNull VirtualFile file, @NotNull Project project) {
    return acceptsFile(file);
  }

  boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project);

  /**
   * This method is called to persist the computed Pusher value (of type T).
   * The implementation is supposed to call {@link PushedFilePropertiesUpdater#filePropertiesChanged}
   * if a change is detected to issue the {@code fileOrDir} re-index
   */
  void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T value) throws IOException;

  //<editor-fold desc="Deprecated APIs" defaultState="collapsed">

  /**
   * @deprecated Please override {@link FilePropertyPusher#acceptsFile(VirtualFile, Project)}
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  default boolean acceptsFile(@NotNull VirtualFile file) {
    return false;
  }
  //</editor-fold>
}


/**
 * Don't use outside {@link FilePropertyPusher#getFilePropertyKey()}.
 * Should be deleted together with the {@link FilePropertyPusher#getFileDataKey()}
 * ({@link FilePropertyPusher#getFilePropertyKey()} default body should also be deleted together with the {@link FilePropertyPusher#getFileDataKey()})
 */
@Deprecated
@ApiStatus.Internal
class InMemoryFilePropertyKeyImpl<T> implements FilePropertyKey<T> {
  private final Key<T> key;

  InMemoryFilePropertyKeyImpl(Key<T> key) { this.key = key; }

  @Override
  public T getPersistentValue(@Nullable VirtualFile virtualFile) {
    return key.get(virtualFile);
  }

  @Override
  public boolean setPersistentValue(@Nullable VirtualFile virtualFile, T value) {
    T oldValue = key.get(virtualFile);
    key.set(virtualFile, value);
    return !Objects.equals(oldValue, value);
  }
}