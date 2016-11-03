/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.Nullable;

public class GenericDetailsLoader<Id, Data> implements Details<Id, Data> {
  private final Consumer<Id> myLoader;
  private final ValueConsumer<Id, Data> myValueConsumer;
  private Id myCurrentlySelected;

  /**
   * @param loader - is called in AWT. Should call {@link #take} with data when ready. Also in AWT
   * @param valueConsumer - is called in AWT. passive, just benefits from details loading
   */
  public GenericDetailsLoader(Consumer<Id> loader, PairConsumer<Id, Data> valueConsumer) {
    myLoader = loader;
    myValueConsumer = new ValueConsumer<>(valueConsumer);
  }

  @CalledInAwt
  public void updateSelection(@Nullable Id id, boolean force) {
    myValueConsumer.setId(id);

    Id previousId = myCurrentlySelected;
    myCurrentlySelected = id;
    if (force || !Comparing.equal(id, previousId)) {
      myLoader.consume(id);
    }
  }

  @CalledInAwt
  @Override
  public void take(Id id, Data data) {
    myValueConsumer.consume(id, data);
  }

  @CalledInAwt
  @Override
  public Id getCurrentlySelected() {
    return myCurrentlySelected;
  }
}
