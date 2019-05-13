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
package com.intellij.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

/**
 *
 * @version 1.0
 */
public class WeakPropertyChangeAdapter
  implements PropertyChangeListener
{
  private final WeakReference myRef;

  public WeakPropertyChangeAdapter(PropertyChangeListener l) {
    myRef = new WeakReference(l);
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    PropertyChangeListener l = (PropertyChangeListener)myRef.get();
    if (l != null) {
      l.propertyChange(e);
    }
  }
}
