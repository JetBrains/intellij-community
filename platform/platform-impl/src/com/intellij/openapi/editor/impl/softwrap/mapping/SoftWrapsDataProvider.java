/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import org.jetbrains.annotations.NotNull;

/**
 * {@link DataProvider} implementation that exposes information about {@link SoftWrap soft wraps}.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 12:18:49 PM
 */
public class SoftWrapsDataProvider extends AbstractListBasedDataProvider<SoftWrapDataProviderKeys, SoftWrapImpl> {

  @SuppressWarnings({"unchecked"})
  public SoftWrapsDataProvider(@NotNull SoftWrapsStorage storage) {
    super(SoftWrapDataProviderKeys.SOFT_WRAP, storage.getSoftWraps());
  }

  @Override
  protected int getSortingKey(@NotNull SoftWrapImpl data) {
    return data.getStart();
  }
}
