// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * States that every rename processor of a language has a {@link HeadlessRenamePsiElementProcessor}
 * registration, so the headless rename covers it.
 * <p>
 * <b>This is temporary.</b> It exists while the conversion runs language by language. Without the
 * statement a rename of an element of an unconverted language runs the processors that are registered,
 * misses the ones that are not, and reports success. Remove this extension point when every language
 * is converted. Then an empty processor list is the honest answer on its own.
 */
@ApiStatus.Internal
public final class HeadlessRenameSupportedLanguage {
  public static final ExtensionPointName<HeadlessRenameSupportedLanguage> EP_NAME =
    ExtensionPointName.create("com.intellij.headlessRenameSupportedLanguage");

  /** The id of the language, as {@link com.intellij.lang.Language#getID} reports it. */
  @Attribute("language")
  @RequiredElement
  public String language;
}
