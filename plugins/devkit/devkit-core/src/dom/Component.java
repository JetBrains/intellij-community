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

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

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
  @ExtendClass(allowEmpty = true)
  GenericDomValue<PsiClass> getHeadlessImplementationClass();

  @NotNull
  List<Option> getOptions();

  Option addOption();

  interface Application extends Component {
  }

  interface Module extends Component {
  }

  interface Project extends Component {
    /**
     * @deprecated project components aren't loaded in the default project by default so there is not need to use this tag;
     * add 'loadForDefaultProject' if your really need to have your component in the default project.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @NotNull
    @SubTag(value = "skipForDefaultProject", indicator = true)
    @Deprecated
    GenericDomValue<Boolean> getSkipForDefaultProject();

    @NotNull
    @SubTag(value = "loadForDefaultProject", indicator = true)
    GenericDomValue<Boolean> getLoadForDefaultProject();
  }
}
