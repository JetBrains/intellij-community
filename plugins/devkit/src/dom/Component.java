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

// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

/**
 * plugin.dtd:component interface.
 */
public interface Component extends DomElement {

  @NotNull
  @Required
  @Convert(PluginPsiClassConverter.class)
  GenericDomValue<PsiClass> getImplementationClass();


  @NotNull
  @ExtendClass(instantiatable = false)
  @Convert(PluginPsiClassConverter.class)
  GenericDomValue<PsiClass> getInterfaceClass();

  @NotNull
  @Convert(PluginPsiClassConverter.class)
  GenericDomValue<PsiClass> getHeadlessImplementationClass();

  @NotNull
  List<Option> getOptions();

  Option addOption();

  interface Application extends Component {
  }

  interface Module extends Component {
  }

  interface Project extends Component {

    @NotNull
    @SubTag(value = "skipForDummyProject", indicator = true)
    GenericDomValue<Boolean> getSkipForDummyProject();

    @NotNull
    @SubTag(value = "skipForDefaultProject", indicator = true)
    GenericDomValue<Boolean> getSkipForDefaultProject();
  }
}
