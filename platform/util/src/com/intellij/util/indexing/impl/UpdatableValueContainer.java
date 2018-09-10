/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.DataExternalizer;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public abstract class UpdatableValueContainer<T> extends ValueContainer<T> {

  public abstract void addValue(int inputId, T value);

  public abstract void removeAssociatedValue(int inputId);

  private volatile boolean myNeedsCompacting;

  boolean needsCompacting() {
    return myNeedsCompacting;
  }

  void setNeedsCompacting(boolean value) {
    myNeedsCompacting = value;
  }

  public abstract void saveTo(DataOutput out, DataExternalizer<? super T> externalizer) throws IOException;
}
