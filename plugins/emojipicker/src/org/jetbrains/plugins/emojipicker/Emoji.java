// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker;

import java.io.Serializable;

public class Emoji implements Serializable {

  private final int myId;
  private final String myValue;
  private final boolean myToned;

  public Emoji(int id, String value, boolean toned) {
    myId = id;
    myValue = value;
    myToned = toned;
  }

  public int getId() {
    return myId;
  }

  public String getTonedValue(EmojiSkinTone tone) {
    return myToned ? myValue + tone.getStringValue() : myValue;
  }

}
