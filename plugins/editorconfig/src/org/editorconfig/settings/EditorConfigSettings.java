package org.editorconfig.settings;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.util.messages.Topic;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigSettings extends CustomCodeStyleSettings {
  public static final Topic<EditorConfigListener> EDITOR_CONFIG_ENABLED_TOPIC = Topic.create("editor config changed", EditorConfigListener.class, Topic.BroadcastDirection.TO_CHILDREN);
  public boolean ENABLED = true;

  protected EditorConfigSettings(CodeStyleSettings container) {
    super("editorconfig", container);
  }
}
