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

/*
 * @author max
 */
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @deprecated Unused, can be replaced with AtomicInteger. If you write your own {@link com.intellij.openapi.vfs.VirtualFileWithId},
 * ensure you comply with the contract from its documentation.
 */
@Deprecated
public class DummyFileIdGenerator {
  private static final Logger LOG = Logger.getInstance(DummyFileIdGenerator.class);
  private static final AtomicInteger ourId = new AtomicInteger(Integer.MAX_VALUE / 2);

  private DummyFileIdGenerator() {
    LOG.warn("com.intellij.openapi.vfs.ex.dummy.DummyFileIdGenerator should not be used");
  }

  public static int next() {
    return ourId.getAndIncrement();
  }
}