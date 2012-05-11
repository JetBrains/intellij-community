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

import com.intellij.util.PairConsumer;

/**
 * @author irengrig
 *         Date: 6/29/11
 *         Time: 11:51 PM
 */
public class ValueConsumer<Id, Data> {
  private Id myId;
  private Id mySetId;
  private final PairConsumer<Id, Data> myConsumer;
  private PairConsumer<Id, Data> myCacheConsumer;

  protected ValueConsumer(PairConsumer<Id, Data> consumer) {
    myConsumer = consumer;
  }

  public void setCacheConsumer(PairConsumer<Id, Data> cacheConsumer) {
    myCacheConsumer = cacheConsumer;
  }

  public void consume(final Id id, final Data data) {
    if (id.equals(mySetId) || ! id.equals(myId)) {
      if (myCacheConsumer != null) {
        myCacheConsumer.consume(id, data);
      }
      return;
    }
    mySetId = id;
    myConsumer.consume(id, data);
  }

  public void setId(Id id) {
    myId = id;
    mySetId = null;
  }

  public void reset() {
    mySetId = null;
  }
}
