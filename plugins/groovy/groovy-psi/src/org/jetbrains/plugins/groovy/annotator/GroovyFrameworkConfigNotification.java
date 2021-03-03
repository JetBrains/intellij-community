// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author sergey.evdokimov
 */
public abstract class GroovyFrameworkConfigNotification {
  public static final ExtensionPointName<GroovyFrameworkConfigNotification> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.groovyFrameworkConfigNotification");

  public abstract boolean hasFrameworkStructure(@NotNull Module module);

  public abstract boolean hasFrameworkLibrary(@NotNull Module module);

  @Nullable
  public abstract JPanel createConfigureNotificationPanel(@NotNull Module module, @NotNull FileEditor fileEditor);
}
