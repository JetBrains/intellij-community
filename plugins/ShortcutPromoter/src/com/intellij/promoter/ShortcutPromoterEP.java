package com.intellij.promoter;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class ShortcutPromoterEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<ShortcutPromoterEP> EP_NAME = new ExtensionPointName<>("com.intellij.shortcutPromoter");

  @Attribute("actionId")
  public String actionId;

  @Attribute("skip")
  public int skip;

  @Attribute("repeat")
  public int repeat;

  @Nullable
  public static ShortcutPromoterEP find(@Nullable String actionId) {
    for (ShortcutPromoterEP ep : EP_NAME.getExtensions()) {
      if (ep.actionId.equals(actionId)) {
        return ep;
      }
    }
    return null;
  }
}
