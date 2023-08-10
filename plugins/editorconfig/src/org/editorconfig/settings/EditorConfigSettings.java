// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.settings;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.util.messages.Topic;

public final class EditorConfigSettings extends CustomCodeStyleSettings {
  public static final Topic<EditorConfigListener> EDITOR_CONFIG_ENABLED_TOPIC = new Topic<>(EditorConfigListener.class);
  public boolean ENABLED = true;

  EditorConfigSettings(CodeStyleSettings container) {
    super("editorconfig", container);
  }
}
