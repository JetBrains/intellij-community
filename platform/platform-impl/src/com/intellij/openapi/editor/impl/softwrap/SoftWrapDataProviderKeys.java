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
package com.intellij.openapi.editor.impl.softwrap;

/**
 * {@link CompositeDataProvider} defines a generic infrastructure for combining multiple {@link DataProvider}s to the
 * single compound entity. But it should be possible to distinguish the source of
 * {@link CompositeDataProvider#getData() every data} returned from it.
 * <p/>
 * Current enum defines types of sources which data is used during soft wraps calculation.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 12:23:04 PM
 */
public enum SoftWrapDataProviderKeys {

  // Please note that declaration order pays significant role here. It defines processing order, i.e. soft wraps are processed before,
  // say, collapsed fold regions located at the same offset.
  SOFT_WRAP,
  COLLAPSED_FOLDING,
  TABULATION
}
