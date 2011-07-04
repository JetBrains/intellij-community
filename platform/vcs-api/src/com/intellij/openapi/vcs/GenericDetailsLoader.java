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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author irengrig
 *         Date: 6/29/11
 *         Time: 11:38 PM
 */
public class GenericDetailsLoader<Id, Data> implements PairConsumer<Id, Data> {
  private final Consumer<Id> myLoader;
  private final ValueConsumer<Id, Data> myValueConsumer;
  private final AtomicReference<Id> myCurrentlySelected;

  public GenericDetailsLoader(final Consumer<Id> loader, final PairConsumer<Id, Data> valueConsumer) {
    myLoader = loader;
    myValueConsumer = new ValueConsumer<Id, Data>(valueConsumer);
    myCurrentlySelected = new AtomicReference<Id>(null);
  }

  public void updateSelection(@Nullable final Id id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myValueConsumer.setId(id);

    if (! Comparing.equal(id, myCurrentlySelected.getAndSet(id))) {
      myLoader.consume(id);
    }
  }

  @Override
  public void consume(Id id, Data data) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myValueConsumer.consume(id, data);
  }

  public Id getCurrentlySelected() {
    return myCurrentlySelected.get();
  }
}
