// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.Serializable;
import java.util.List;

public final class EmojiCategory implements Serializable {
  @NonNls private final String myId;
  private final List<Emoji> myEmoji;
  private transient Icon myIcon;

  public EmojiCategory(String id, List<Emoji> emojiList) {
    myId = id;
    myEmoji = emojiList;
  }

  @NonNls
  public String getId() {
    return myId;
  }

  public List<Emoji> getEmoji() {
    return myEmoji;
  }

  public Icon getIcon() {
    Icon icon = myIcon;
    if (icon == null) {
      myIcon = icon = IconLoader.getIcon("/icons/categories/" + myId + ".svg", EmojiCategory.class.getClassLoader());
    }
    return icon;
  }
}
