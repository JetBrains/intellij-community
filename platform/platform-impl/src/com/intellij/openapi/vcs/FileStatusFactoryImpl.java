// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Supplier;

public final class FileStatusFactoryImpl extends FileStatusFactory {
  private final MultiMap<@Nullable PluginId, FileStatus> myStatuses = new MultiMap<>();

  @Override
  public synchronized FileStatus createFileStatus(@NonNls @NotNull String id,
                                                  @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String> description,
                                                  @Nullable Color color,
                                                  @Nullable PluginId pluginId) {
    FileStatusImpl result = new FileStatusImpl(id, ColorKey.createColorKey(FILESTATUS_COLOR_KEY_PREFIX + id, color), description);
    myStatuses.putValue(pluginId, result);
    return result;
  }

  @Override
  public synchronized FileStatus[] getAllFileStatuses() {
    return myStatuses.values().toArray(new FileStatus[0]);
  }

  private synchronized void onPluginUnload(@NotNull PluginId pluginId) {
    myStatuses.remove(pluginId);
  }

  /**
   * author: lesya
   */
  private static final class FileStatusImpl implements FileStatus {
    private final String myStatus;
    private final ColorKey myColorKey;
    private final Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> myTextSupplier;

    FileStatusImpl(@NotNull String status,
                   @NotNull ColorKey key,
                   @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> textSupplier) {
      myStatus = status;
      myColorKey = key;
      myTextSupplier = textSupplier;
    }

    @NonNls
    public String toString() {
      return myStatus;
    }

    @Override
    public String getText() {
      return myTextSupplier.get();
    }

    @Override
    public Color getColor() {
      return EditorColorsManager.getInstance().getSchemeForCurrentUITheme().getColor(getColorKey());
    }

    @NotNull
    @Override
    public ColorKey getColorKey() {
      return myColorKey;
    }

    @NotNull
    @Override
    public String getId() {
      return myStatus;
    }
  }

  private static final class PluginListener implements DynamicPluginListener {
    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
      PluginId pluginId = pluginDescriptor.getPluginId();
      FileStatusFactory factory = getInstance();
      if (pluginId != null && factory instanceof FileStatusFactoryImpl) {
        ((FileStatusFactoryImpl)factory).onPluginUnload(pluginId);
      }
    }
  }
}
