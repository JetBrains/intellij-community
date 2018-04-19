/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;

import java.util.Collection;

/**
 * plugin.dtd:reference interface.
 */
public interface Reference extends DomElement {

  @NotNull
  @Convert(ActionOrGroupResolveConverter.class)
  GenericAttributeValue<ActionOrGroup> getRef();

  @NotNull
  @Convert(ActionOrGroupResolveConverter.class)
  GenericAttributeValue<ActionOrGroup> getId();

  @NotNull
  Collection<AddToGroup> getAddToGroups();

}
