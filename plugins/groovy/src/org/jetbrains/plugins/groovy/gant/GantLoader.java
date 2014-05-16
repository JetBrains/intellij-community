/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;

/**
 * @author ilyas
 */
public class GantLoader implements ApplicationComponent {

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "Gant loader";
  }

  @Override
  public void initComponent() {
    GroovyTemplatesFactory.getInstance().registerCustromTemplates(GroovyTemplates.GANT_SCRIPT);
  }

  @Override
  public void disposeComponent() {
  }
}
