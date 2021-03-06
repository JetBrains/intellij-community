// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;

import java.io.Serializable;

public class Emoji implements Serializable {
  private final int myId;
  @NonNls private final String myValue;
  private final boolean myToned;

  public Emoji(int id, @NonNls String value, boolean toned) {
    myId = id;
    myValue = value;
    myToned = toned;
  }

  public int getId() {
    return myId;
  }

  @NlsSafe
  public String getTonedValue(EmojiSkinTone tone) {
    return myToned ? myValue + tone.getStringValue() : myValue;
  }
}
