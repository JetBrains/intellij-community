/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.autoimport;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/18/13 8:29 PM
 */
public abstract class AbstractGradleUserProjectChange<T extends AbstractGradleUserProjectChange> implements GradleUserProjectChange<T> {

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public T getState() {
    return (T)this;
  }

  @Override
  public void loadState(T state) {
    XmlSerializerUtil.copyBean(state, this); 
  }
}
