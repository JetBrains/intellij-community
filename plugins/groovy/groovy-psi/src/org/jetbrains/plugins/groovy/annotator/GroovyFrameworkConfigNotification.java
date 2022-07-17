// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author sergey.evdokimov
 */
public abstract class GroovyFrameworkConfigNotification {

  public static final ExtensionPointName<GroovyFrameworkConfigNotification> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.groovyFrameworkConfigNotification");

  public abstract boolean hasFrameworkStructure(@NotNull Module module);

  public abstract boolean hasFrameworkLibrary(@NotNull Module module);
}
