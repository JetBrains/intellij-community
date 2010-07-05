/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

public interface VcsShowConfirmationOption {
  enum Value {
    SHOW_CONFIRMATION(0),
    DO_NOTHING_SILENTLY(1),
    DO_ACTION_SILENTLY(2);

    private final int myId;

    Value(final int id) {
      myId = id;
    }

    public int getId() {
      return myId;
    }
    
    public String toString() {
      return String.valueOf(myId);
    }

    public static Value fromString(String s){
      if (s == null) return SHOW_CONFIRMATION;
      if (s.equals("1")) return DO_NOTHING_SILENTLY;
      if (s.equals("2")) return DO_ACTION_SILENTLY;
      return SHOW_CONFIRMATION;
    }
  }

  public static final VcsShowConfirmationOption STATIC_SHOW_CONFIRMATION = new VcsShowConfirmationOption() {
    @Override
    public Value getValue() {
      return Value.SHOW_CONFIRMATION;
    }
    @Override
    public void setValue(Value value) {
    }
    @Override
    public boolean isPersistent() {
      return false;
    }
  };

  Value getValue();
  void setValue(Value value);
  boolean isPersistent();
}
