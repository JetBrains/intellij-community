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
package com.intellij.util;

import com.intellij.openapi.util.Condition;

/**
 * @author max
 */
public class FilteringProcessor<T> implements Processor<T> {
  private final Condition<T> myFilter;
  private final Processor<T> myProcessor;

  public FilteringProcessor(final Condition<T> filter, Processor<T> processor) {
    myFilter = filter;
    myProcessor = processor;
  }

  public boolean process(final T t) {
    if (!myFilter.value(t)) return true;
    return myProcessor.process(t);
  }
}
