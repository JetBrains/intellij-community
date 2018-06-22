/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

/**
 * @since 15
 */
public interface ActionOrGroup extends DomElement {

  @NotNull
  @NameValue
  @Stubbed
  @Required(value = false)
  GenericAttributeValue<String> getId();

  @NotNull
  @Stubbed
  @Required(value = false)
  GenericAttributeValue<String> getIcon();

  @NotNull
  @Stubbed
  GenericAttributeValue<Boolean> getPopup();

  @NotNull
  @Stubbed
  @Required(value = false)
  GenericAttributeValue<String> getText();

  @NotNull
  @Stubbed
  @Required(value = false)
  GenericAttributeValue<String> getDescription();
}
